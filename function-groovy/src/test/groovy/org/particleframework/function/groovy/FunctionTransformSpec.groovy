/*
 * Copyright 2017 original authors
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
package org.particleframework.function.groovy

import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.customizers.ASTTransformationCustomizer
import org.particleframework.context.ApplicationContext
import org.particleframework.context.env.MapPropertySource
import org.particleframework.http.HttpStatus
import org.particleframework.runtime.server.EmbeddedServer
import spock.lang.Specification

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class FunctionTransformSpec extends Specification{
    def cleanup() {
        TestFunctionExitHandler.lastError = null
    }

    void 'test parse function'() {
        given:
        CompilerConfiguration configuration = new CompilerConfiguration()
        configuration.optimizationOptions['particle.function.compile'] = true
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
        def applicationContext = ApplicationContext.build()
                .environment({ env ->
            env.addPropertySource(MapPropertySource.of(
                    'test',
                    ['math.multiplier': '2']
            ))

        })
        EmbeddedServer server = applicationContext.start().getBean(EmbeddedServer).start()
        def message = new Message(title: "Hello", body: "World")
        String url = "http://localhost:$server.port"
        OkHttpClient client = new OkHttpClient()
        def data = '{"title":"Hello", "body":"World"}'

        def request = new Request.Builder()
                .url("$url/notify-with-args")
                .post(RequestBody.create( MediaType.parse(org.particleframework.http.MediaType.APPLICATION_JSON), data))

        when:
        def response = client.newCall(request.build()).execute()

        then:
        response.code() == HttpStatus.OK.code
        applicationContext.getBean(MessageService).messages.contains(message)

        cleanup:
        if(server != null)
            server.stop()
    }

    void "test run JSON function as REST service"() {
        given:
        EmbeddedServer server = ApplicationContext.build()
                .environment({ env ->
            env.addPropertySource(MapPropertySource.of(
                    'test',
                    ['math.multiplier':'2']
            ))

        }).start().getBean(EmbeddedServer).start()

        String url = "http://localhost:$server.port"
        OkHttpClient client = new OkHttpClient()
        def data = '{"a":10, "b":5}'
        def request = new Request.Builder()
                .url("$url/sum")
                .post(RequestBody.create( MediaType.parse(org.particleframework.http.MediaType.APPLICATION_JSON), data))

        when:
        def response = client.newCall(request.build()).execute()

        then:
        response.code() == HttpStatus.OK.code
        response.body().string() == '15'

        cleanup:
        if(server != null)
            server.stop()
    }

    void "test run function as REST service"() {
        given:
        EmbeddedServer server = ApplicationContext.build()
                                                  .environment({ env ->
            env.addPropertySource(MapPropertySource.of(
                    'test',
                    ['math.multiplier':'2']
            ))

        }).start().getBean(EmbeddedServer).start()

        String url = "http://localhost:$server.port"
        OkHttpClient client = new OkHttpClient()
        def data = '1.6'
        def request = new Request.Builder()
                .url("$url/round")
                .post(RequestBody.create( MediaType.parse("text/plain"), data))

        when:
        def response = client.newCall(request.build()).execute()

        then:
        response.code() == HttpStatus.OK.code
        response.body().string() == '4'

        cleanup:
        if(server != null)
            server.stop()
    }

    void "test run supplier as REST service"() {
        given:
        EmbeddedServer server = ApplicationContext.build()
                .environment({ env ->
            env.addPropertySource(MapPropertySource.of(
                    'test',
                    ['math.multiplier':'2']
            ))

        }).start().getBean(EmbeddedServer).start()

        String url = "http://localhost:$server.port"
        OkHttpClient client = new OkHttpClient()
        def request = new Request.Builder()
                .url("$url/max")


        when:
        def response = client.newCall(request.build()).execute()

        then:
        response.code() == HttpStatus.OK.code
        response.body().string() == String.valueOf(Integer.MAX_VALUE)

        cleanup:
        if(server != null)
            server.stop()
    }
}
