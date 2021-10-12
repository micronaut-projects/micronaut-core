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
package io.micronaut.docs.server.request

import io.micronaut.context.ApplicationContext
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class MessageControllerSpec extends Specification{

    @Shared @AutoCleanup EmbeddedServer embeddedServer =
            ApplicationContext.run(EmbeddedServer) // <1>

    @Shared @AutoCleanup HttpClient client = HttpClient.create(embeddedServer.URL)

    void "test message response"() {
        expect:
        client.toBlocking().retrieve("/request/hello?name=John") == "Hello John!!"
        client.toBlocking().retrieve("/request/hello-static?name=John") == "Hello John!!"
        client.toBlocking().retrieve("/request/hello-reactor?name=John") == "Hello John!!"
    }

}
