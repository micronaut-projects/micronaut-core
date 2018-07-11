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
package io.micronaut.configuration.hystrix

import io.reactivex.Flowable
import io.micronaut.context.ApplicationContext
import io.micronaut.core.io.buffer.ByteBuffer
import io.micronaut.http.HttpRequest
import io.micronaut.http.client.RxStreamingHttpClient
import io.micronaut.runtime.server.EmbeddedServer
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import java.nio.charset.StandardCharsets

/**
 * @author graemerocher
 * @since 1.0
 */
class HystrixStreamControllerSpec extends Specification {

    @Shared @AutoCleanup EmbeddedServer server = ApplicationContext.run(
            EmbeddedServer,
            ['hystrix.stream.enabled':true]
    )

    void setup() {
        server.getApplicationContext().getBean(HystrixCommandSpec.MyHook).reset()
    }


    void "test hystrix event stream"() {
        given:
        RxStreamingHttpClient client = server.applicationContext.createBean(RxStreamingHttpClient, server.getURL())

        when:
        Flowable flowable = client.dataStream(HttpRequest.GET('/hystrix.stream'))
        List<String> data = new ArrayList<>()
        flowable.subscribe(new Subscriber<ByteBuffer<?>>() {
            @Override
            void onSubscribe(Subscription s) {
                s.request(1)
            }

            @Override
            void onNext(ByteBuffer<?> byteBuffer) {
                data.add byteBuffer.toString(StandardCharsets.UTF_8)
            }

            @Override
            void onError(Throwable t) {
                t.printStackTrace()
            }

            @Override
            void onComplete() {

            }
        })


        HystrixCommandSpec.TestService testService = server.applicationContext.getBean(HystrixCommandSpec.TestService)

        String value = testService.hello("Fred")
        PollingConditions conditions = new PollingConditions(timeout: 3)

        then:
        value == "Hello Fred"
        conditions.eventually {
            data.size() == 1
            data.first().startsWith("data:")
            data.first().contains("\"type\":\"HystrixCommand\",\"name\":\"hello\"")
        }

    }
}
