/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.python.annotation.processing.test

import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * A pooled Python type may carry advice, and the advice runs in every context.
 *
 * Advice on a Python class arrives solely through a proxy of its Python object -- no
 * compile-time interceptor is generated for one, unlike for a Java class -- and a Python
 * proxy belongs to the context it was created in. A pooled type exists in every context, so
 * one proxy cannot stand for it. The proxy is therefore created per context, and these tests
 * pin that down by calling often enough that more than one context serves the bean: if the
 * proxy were bypassed in the contexts it was not created for, the advice would be skipped
 * with nothing said, which for {@code @Transactional} is a method running with no transaction.
 */
class PooledAdviceSpec extends AbstractPythonTypeElementSpec {

    private static final String INTERCEPTOR = '''
from micronaut.aop import InterceptorBean, MethodInvocationContext, Around
from micronaut.context.python.scope import ContextPooled
import java

MethodInterceptor = java.type("io.micronaut.aop.MethodInterceptor")

@Around
def Wrapped(func):
    return func

@InterceptorBean(Wrapped)
class WrappedInterceptor(MethodInterceptor):
    def intercept(self, context : MethodInvocationContext):
        return "advised:" + str(context.proceed())
'''

    void "advice on a pooled class with constructor arguments runs in every context"() {
        given:
        def python = INTERCEPTOR + '''
@ContextPooled
class Greeter:
    def greeting(self) -> str:
        return "hello"

@ContextPooled
class Greeting:
    def __init__(self, greeter: Greeter):
        self.greeter = greeter

    @Wrapped
    def greet(self, name: str) -> str:
        return self.greeter.greeting() + " " + name
'''
        def context = buildContext(python, true, ["micronaut.python.pool.size": 4])
        def bean = context.getBean(context.classLoader.loadClass("python.Greeting"))

        when: "called often enough that the pool rotates through its contexts"
        def results = (1..40).collect { bean.greet("world") }.toSet()

        then: "every call went through the proxy, whichever context served it"
        results == ["advised:hello world"] as Set

        cleanup:
        context?.close()
    }

    void "advice on a pooled class without constructor arguments runs in every context"() {
        given: "no arguments, so the bean is otherwise served from the pool's per-class cache"
        def python = INTERCEPTOR + '''
@ContextPooled
class Plain:
    @Wrapped
    def value(self) -> str:
        return "value"
'''
        def context = buildContext(python, true, ["micronaut.python.pool.size": 4])
        def bean = context.getBean(context.classLoader.loadClass("python.Plain"))

        when:
        def results = (1..40).collect { bean.value() }.toSet()

        then: "the holder of per-context proxies takes over from that cache"
        results == ["advised:value"] as Set

        cleanup:
        context?.close()
    }

    void "advice on a pooled class runs when contexts are used concurrently"() {
        given:
        def python = INTERCEPTOR + '''
@ContextPooled
class Slow:
    @Wrapped
    def value(self) -> str:
        import time
        time.sleep(0.05)
        return "value"
'''
        def context = buildContext(python, true, ["micronaut.python.pool.size": 4])
        def bean = context.getBean(context.classLoader.loadClass("python.Slow"))
        def executor = Executors.newFixedThreadPool(4)

        when: "four threads call at once, so four contexts build their own proxy at once"
        def results = (1..8).collect { executor.submit { bean.value() } }
            .collect { it.get(30, TimeUnit.SECONDS) }
            .toSet()

        then:
        results == ["advised:value"] as Set

        cleanup:
        executor?.shutdownNow()
        context?.close()
    }

    void "an advised pooled class is served by more than one context"() {
        given: "the advised method reports which context ran it"
        def python = INTERCEPTOR + '''
@ContextPooled
class CtxReader:
    @Wrapped
    def ctx_id(self) -> str:
        import time
        import builtins
        time.sleep(0.1)
        g = globals()
        return g.get("__MN_CTX_ID__") or builtins.__dict__.get("__MN_CTX_ID__") or "unknown"
'''
        def previous = System.getProperty("micronaut.python.context-id.enabled")
        System.setProperty("micronaut.python.context-id.enabled", "true")
        def context = buildContext(python, true, ["micronaut.python.pool.size": 4])
        def bean = context.getBean(context.classLoader.loadClass("python.CtxReader"))
        def executor = Executors.newFixedThreadPool(4)
        warmPool(context, executor, 4)

        when:
        def results = (1..4).collect { executor.submit { bean.ctx_id() } }
            .collect { it.get(30, TimeUnit.SECONDS) }

        then: "every call is advised, and they did not all run in the proxy's own context"
        results.every { it.startsWith("advised:") }
        results.toSet().size() >= 2

        cleanup:
        executor?.shutdownNow()
        context?.close()
        if (previous == null) {
            System.clearProperty("micronaut.python.context-id.enabled")
        } else {
            System.setProperty("micronaut.python.context-id.enabled", previous)
        }
    }
}
