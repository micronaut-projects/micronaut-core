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
package org.particleframework.docs.server.intro

// tag::imports[]
import org.particleframework.context.ApplicationContext
import org.particleframework.http.HttpRequest
import org.particleframework.http.client.HttpClient
import org.particleframework.runtime.server.EmbeddedServer
import spock.lang.*
// end::imports[]

/**
 * @author Graeme Rocher
 * @since 1.0
 */
// tag::class[]
class HelloControllerSpec extends Specification {

    @Shared @AutoCleanup EmbeddedServer embeddedServer =
            ApplicationContext.run(EmbeddedServer) // <1>

    @Shared @AutoCleanup HttpClient client = HttpClient.create(embeddedServer.URL) // <2>

    void "test hello world response"() {
        expect:
        client.toBlocking() // <3>
              .retrieve(HttpRequest.GET('/hello')) == "Hello World" // <4>
    }
}
// end::class[]
