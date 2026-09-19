/*
 * Copyright 2017-2025 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.python.annotation.processing.test.context

import io.micronaut.context.ApplicationContext
import io.micronaut.context.python.PythonContextRuntime
import io.micronaut.context.python.ValueCoercible
import io.micronaut.python.annotation.processing.test.AbstractPythonTypeElementSpec
import io.micronaut.python.compiler.InMemoryBeanDefinitionsProvider
import io.micronaut.runtime.server.EmbeddedServer
import org.graalvm.polyglot.Context

import java.util.function.Supplier

/**
 * A Python test started by {@code @MicronautTest} may run a second {@code ApplicationContext.run(...)}
 * (an {@code EmbeddedServer} it restarts, a context per configuration) and close it. Every application
 * owns its own GraalPy context: closing the nested one must neither close the enclosing one nor leave
 * generated code without a runtime.
 */
class NestedApplicationContextSpec extends AbstractPythonTypeElementSpec {

    private static final String PYTHON = '''
import java
from dataclasses import dataclass
from java.util.function import Supplier
from jakarta.inject import Singleton
from micronaut.context.annotation import Executable
from micronaut.core.annotation import Introspected
from micronaut.http.annotation import Controller, Get

HttpClient = java.type("io.micronaut.http.client.HttpClient")
GreeterType = java.type("python.Greeter")


@Introspected
@dataclass
class Greeting:
    message: str


@Singleton
class Greeter:
    @Executable
    def greet(self, name: str) -> Greeting:
        return Greeting("Hello " + name)


@Singleton
class LateGreeter:
    """Created by the enclosing application only after the nested one is gone."""

    @Executable
    def greet(self, name: str) -> Greeting:
        return Greeting("Late hello " + name)


@Controller("/nested")
class GreetingController:
    def __init__(self, greeter: Greeter):
        self.greeter = greeter

    @Get("/greet")
    def greet(self) -> str:
        return self.greeter.greet("http").message


@Singleton
class NestedRunner:
    def __init__(self, greeter: Greeter):
        self.greeter = greeter

    @Executable
    def run_nested(self, start: Supplier) -> str:
        nested = start.get()
        try:
            nested_greeter = nested.getBean(GreeterType)
            return nested_greeter.greet("nested").message + " / " + self.greeter.greet("outer").message
        finally:
            nested.close()

    @Executable
    def restart_servers(self, start: Supplier) -> list[str]:
        results = []
        for _ in range(2):
            server = start.get()
            try:
                client = server.getApplicationContext().createBean(HttpClient, server.getURL()).toBlocking()
                results.append(client.retrieve("/nested/greet"))
            finally:
                server.close()
        return results
'''

    void "a nested application context run and closed from Python leaves the enclosing application working"() {
        given:
        ApplicationContext context = buildContext(PYTHON, true)
        Context primaryContext = context.getBean(Context)
        def runner = getBean(context, 'python.NestedRunner')
        List<ApplicationContext> nestedContexts = []
        Supplier<ApplicationContext> start = { ->
            ApplicationContext nested = startNested(context)
            nestedContexts << nested
            nested
        }

        when:
        String result = runner.run_nested(start)

        then:
        result == 'Hello nested / Hello outer'
        nestedContexts.size() == 1
        !nestedContexts[0].running
        context.running

        and: "generated code resolves the enclosing application again"
        PythonContextRuntime.initialized
        PythonContextRuntime.context == primaryContext

        when: "a Python bean of the enclosing application is created after the nested one closed"
        def lateGreeter = getBean(context, 'python.LateGreeter')
        def greeting = lateGreeter.greet('outer')

        then:
        greeting.message == 'Late hello outer'
        ((ValueCoercible) greeting).asPolyglotValue().context == primaryContext

        when: "the nested run is repeated"
        result = runner.run_nested(start)

        then:
        result == 'Hello nested / Hello outer'
        nestedContexts.size() == 2
        !nestedContexts[1].running
        PythonContextRuntime.context == primaryContext

        cleanup:
        nestedContexts.each { it.close() }
        context?.close()
    }

    void "an embedded server restarted from Python keeps the enclosing application working"() {
        given:
        ApplicationContext context = buildContext(PYTHON, true)
        Context primaryContext = context.getBean(Context)
        def runner = getBean(context, 'python.NestedRunner')
        List<EmbeddedServer> servers = []
        Supplier<EmbeddedServer> start = { ->
            EmbeddedServer server = startNested(context).getBean(EmbeddedServer).start()
            servers << server
            server
        }

        when:
        List<String> responses = runner.restart_servers(start)

        then:
        responses == ['Hello http', 'Hello http']
        servers.size() == 2
        servers.every { !it.running && !it.applicationContext.running }
        PythonContextRuntime.context == primaryContext

        when:
        def greeting = getBean(context, 'python.LateGreeter').greet('server')

        then:
        greeting.message == 'Late hello server'
        ((ValueCoercible) greeting).asPolyglotValue().context == primaryContext

        cleanup:
        servers.each { it.applicationContext.close() }
        context?.close()
    }

    void "a nested application context run and closed from Java leaves the enclosing application working"() {
        given:
        ApplicationContext context = buildContext(PYTHON, true)
        Context primaryContext = context.getBean(Context)

        when:
        ApplicationContext nested = startNested(context)
        Context nestedPrimaryContext = nested.getBean(Context)
        def nestedGreeting = getBean(nested, 'python.Greeter').greet('nested')

        then:
        nestedPrimaryContext != primaryContext
        nestedGreeting.message == 'Hello nested'
        PythonContextRuntime.context == nestedPrimaryContext

        when:
        nested.close()
        def greeting = getBean(context, 'python.LateGreeter').greet('outer')

        then:
        PythonContextRuntime.context == primaryContext
        greeting.message == 'Late hello outer'
        ((ValueCoercible) greeting).asPolyglotValue().context == primaryContext

        when: "the enclosing application closes"
        context.close()

        then:
        !PythonContextRuntime.initialized

        cleanup:
        nested?.close()
        context?.close()
    }

    /**
     * A second application over the same compiled Python application, the way
     * {@code ApplicationContext.run(...)} inside a Python test starts one.
     */
    private static ApplicationContext startNested(ApplicationContext enclosing) {
        ApplicationContext.builder()
            .classLoader(enclosing.classLoader)
            .environments('test')
            .properties(['micronaut.server.port': -1])
            .beanDefinitionsProvider(new InMemoryBeanDefinitionsProvider(true))
            .build()
            .start()
    }
}
