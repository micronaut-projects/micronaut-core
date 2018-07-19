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
package io.micronaut.docs.server.sse

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.client.sse.RxSseClient
import io.micronaut.http.sse.Event
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class HeadlineControllerSpec extends Specification {

    @Shared @AutoCleanup EmbeddedServer embeddedServer =
            ApplicationContext.run(EmbeddedServer)

    void "test consume event stream object"() {
        given:
        RxSseClient client = embeddedServer.applicationContext.createBean(RxSseClient, embeddedServer.getURL())

        List<Event<Headline>> events = []

        client.eventStream(HttpRequest.GET("/headlines"), Headline).subscribe { event ->
            events.add(event)
        }

        PollingConditions conditions = new PollingConditions(timeout: 3)
        expect:
        conditions.eventually {
            events.size() == 2
            events[0].data.title == "Micronaut 1.0 Released"
            events[0].data.description == "Come and get it"
        }
    }

}