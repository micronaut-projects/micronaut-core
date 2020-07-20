/*
 * Copyright 2017-2019 original authors
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
package io.micronaut.tracing.brave

import io.micronaut.context.ApplicationContext
import io.micronaut.core.io.socket.SocketUtils
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.tracing.brave.sender.HttpClientSender
import io.reactivex.Flowable
import io.reactivex.Single
import spock.lang.Specification
import spock.util.concurrent.PollingConditions
import zipkin2.Span

/**
 * @author graemerocher
 * @since 1.0
 */
class HttpClientSenderSpec extends Specification {

    void "test http client sender receives spans"() {
        given:
        ApplicationContext context = ApplicationContext.run(
                'tracing.zipkin.enabled':true,
                'tracing.zipkin.sampler.probability':1,
                'tracing.zipkin.http.url':HttpClientSender.Builder.DEFAULT_SERVER_URL
        )
        EmbeddedServer embeddedServer = context.getBean(EmbeddedServer).start()
        HttpClient client = context.createBean(HttpClient, embeddedServer.getURL())

        when:
        PollingConditions conditions = new PollingConditions(timeout: 10)
        // mock Zipkin server
        EmbeddedServer zipkinServer = ApplicationContext.run(
                EmbeddedServer,
                ['micronaut.server.port':9411]
        )
        SpanController spanController = zipkinServer.applicationContext.getBean(SpanController)

        then:
        conditions.eventually {
            !SocketUtils.isTcpPortAvailable(9411)
        }

        when:"Requests are executed"
        HttpResponse<String> response = client.toBlocking().exchange('/traced/nested/John', String)


        then:"spans are received"
        conditions.eventually {
            response.status() == HttpStatus.OK
            spanController.receivedSpans.size() == 4
            spanController.receivedSpans[0].tags.get("foo") == 'bar'
            spanController.receivedSpans[0].tags.get('http.path') == '/traced/hello/John'
            spanController.receivedSpans[0].name == 'get /traced/hello/{name}'
            spanController.receivedSpans[0].kind == Span.Kind.SERVER.name()
            spanController.receivedSpans[1].tags.get('http.path') == '/traced/hello/John'
            spanController.receivedSpans[1].name == 'get /traced/hello/{name}'
            spanController.receivedSpans[1].kind == Span.Kind.CLIENT.name()
            spanController.receivedSpans[2].name == 'get /traced/nested/{name}'
            spanController.receivedSpans[2].kind == Span.Kind.SERVER.name()
            spanController.receivedSpans[2].tags.get('http.method') == 'GET'
            spanController.receivedSpans[2].tags.get('http.path') == '/traced/nested/John'
            spanController.receivedSpans[3].tags.get("foo") == null
            spanController.receivedSpans[3].tags.get('http.path') == '/traced/nested/John'
            spanController.receivedSpans[3].name == 'get'
            spanController.receivedSpans[3].kind == Span.Kind.CLIENT.name()

        }

        cleanup:
        client.close()
        context.close()
        zipkinServer.close()

    }

    @Controller('/api/v2')
    static class SpanController {
        List<Map> receivedSpans = []
        @Post('/spans')
        Single<HttpResponse> spans(@Body Flowable<Map> spans) {
            spans.toList().map({ List list ->
                println "SPANS $list"
                receivedSpans.addAll(list)
                HttpResponse.ok()
            })
        }

    }
}
