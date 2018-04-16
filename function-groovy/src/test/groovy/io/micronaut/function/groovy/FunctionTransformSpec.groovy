/*
 * Copyright 2017-2018 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.function.groovy

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.server.EmbeddedServer
import io.reactivex.Flowable
import org.codehaus.groovy.control.CompilerConfiguration
import spock.lang.Ignore
import spock.lang.Shared
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class FunctionTransformSpec extends Specification{

    @Shared File uploadDir = File.createTempDir()

    def cleanup() {
        TestFunctionExitHandler.lastError = null
        uploadDir.delete()
    }

    void 'test parse function'() {
        given:
        CompilerConfiguration configuration = new CompilerConfiguration()
        configuration.optimizationOptions['micronaut.function.compile'] = true
        GroovyClassLoader gcl = new GroovyClassLoader(FunctionTransformSpec.classLoader, configuration)

        Class functionClass = gcl.parseClass('''
int round(float value) {
    Math.round(value) 
}
''')

        expect:
        functionClass.main(['-d','1.6f'] as String[])
        TestFunctionExitHandler.lastError == null
    }

    //TODO: Fix me and remove @Ignore
    @Ignore
    void 'test parse JSON marshalling function'() {
        given:
        CompilerConfiguration configuration = new CompilerConfiguration()
        configuration.optimizationOptions['micronaut.function.compile'] = true
        GroovyClassLoader gcl = new GroovyClassLoader(FunctionTransformSpec.classLoader, configuration)
        gcl.parseClass('''
package test

class Test { String name }
''')
        Class functionClass = gcl.parseClass('''
import test.*

Test test(Test test) {
    test 
}
''')

        expect:
        functionClass.main(['-d','{"name":"Fred"}'] as String[])
        TestFunctionExitHandler.lastError == null
    }

    void "run function main method"() {

        expect:
        RoundFunction.main(['-d','1.6f'] as String[])
        TestFunctionExitHandler.lastError == null

    }
    void "run function"() {
        expect:
        new RoundFunction().round(1.6f) == 4
        new SumFunction().sum(new Sum(a: 10,b: 20)) == 30
        new MaxFunction().max() == Integer.MAX_VALUE.toLong()
    }

    void "run consumer"() {
        given:
        NotifyFunction function = new NotifyFunction()

        def message = new Message(title: "Hello", body: "World")
        when:
        function.send(message)

        then:
        function.messageService.messages.contains(message)
    }

    void "run bi-consumer"() {
        given:
        NotifyWithArgsFunction function = new NotifyWithArgsFunction()

        def message = new Message(title: "Hello", body: "World")
        when:
        function.send("Hello", "World")

        then:
        function.messageService.messages.contains(message)
    }

    void "test run JSON bi-consumer as REST service"() {

        given:
        Map configuration = ['micronaut.server.multipart.location':uploadDir.absolutePath]
        ApplicationContext context = ApplicationContext.run(
                configuration << ['spec.name': getClass().simpleName,
                                  'math.multiplier': '2'
                ]
        )
        EmbeddedServer server = context.getBean(EmbeddedServer).start()
        HttpClient client = context.createBean(HttpClient, server.getURL())
        def message = new Message(title: "Hello", body: "World")
        def data = '{"title":"Hello", "body":"World"}'

        when:
        Flowable<HttpResponse> flowable = Flowable.fromPublisher(
                client.exchange(
                        HttpRequest.POST("/notify-with-args", data)
                                .contentType(MediaType.APPLICATION_JSON_TYPE)
                )
        )

        HttpResponse response = flowable.blockingFirst()


        then:
        response.code() == HttpStatus.OK.code
        context.getBean(MessageService).messages.contains(message)

        cleanup:
        if(server != null)
            server.stop()
    }

    void "test run JSON function as REST service"() {
        given:
        ApplicationContext context = ApplicationContext.run(['math.multiplier':'2'], 'test')
        EmbeddedServer server = context.getBean(EmbeddedServer).start()
        def data = '{"a":10, "b":5}'
        
        when:
        def conditions = new PollingConditions()

        then:
        conditions.eventually {
            server.isRunning()
        }

        when:
        HttpClient client = context.createBean(HttpClient, server.getURL())
        Flowable<HttpResponse<String>> flowable = Flowable.fromPublisher(
                client.exchange(
                        HttpRequest.POST("/sum", data)
                                .contentType(io.micronaut.http.MediaType.APPLICATION_JSON_TYPE),
                        String
                )
        )
        HttpResponse<String> response = flowable.blockingFirst()

        then:
        response.code() == HttpStatus.OK.code
        response.getBody().get() == '15'

        cleanup:
        server?.stop()
    }

    void "test run function as REST service"() {
        given:
        ApplicationContext context = ApplicationContext.run(['math.multiplier':'2'], 'test')
        EmbeddedServer server = context.getBean(EmbeddedServer).start()
        HttpClient client = context.createBean(HttpClient, server.getURL())

        def data = '1.6'

        Flowable<HttpResponse<String>> flowable = Flowable.fromPublisher(
                client.exchange(
                        HttpRequest.POST("/round", data)
                                .contentType(io.micronaut.http.MediaType.TEXT_PLAIN_TYPE),
                        String
                )
        )

        when:
        def conditions = new PollingConditions()

        then:
        conditions.eventually {
            server.isRunning()
        }

        when:
        HttpResponse<String> response = flowable.blockingFirst()

        then:
        response.code() == HttpStatus.OK.code
        response.getBody().get() == '4'

        cleanup:
        server?.stop()
    }

    void "test run supplier as REST service"() {
        given:
        ApplicationContext context = ApplicationContext.run(['math.multiplier':'2'], 'test')
        EmbeddedServer server = context.getBean(EmbeddedServer).start()
        HttpClient client = context.createBean(HttpClient, server.getURL())

        Flowable<HttpResponse<String>> flowable = Flowable.fromPublisher(
                client.exchange(
                        HttpRequest.GET("/max"),
                        String
                )
        )

        when:
        def conditions = new PollingConditions()

        then:
        conditions.eventually {
            server.isRunning()
        }

        when:
        HttpResponse<String> response = flowable.blockingFirst()

        then:
        response.code() == HttpStatus.OK.code
        response.getBody().get() == String.valueOf(Integer.MAX_VALUE)

        cleanup:
        server?.stop()
    }
}
