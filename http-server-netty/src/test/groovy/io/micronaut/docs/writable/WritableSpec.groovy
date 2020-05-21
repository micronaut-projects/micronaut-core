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
package io.micronaut.docs.writable

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.client.RxHttpClient
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class WritableSpec extends Specification {

    @Shared @AutoCleanup EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer)
    @Shared @AutoCleanup RxHttpClient client = embeddedServer.getApplicationContext().createBean(RxHttpClient, embeddedServer.getURL())


    void "test render template"() {
        expect:
        HttpResponse<String> resp = client.toBlocking().exchange('/template/welcome', String)
        resp.body() == 'Dear Fred Flintstone. Nice to meet you.'
        resp.contentType.get() == MediaType.TEXT_PLAIN_TYPE
    }

    void "test the correct headers are applied"() {
        when:
        HttpResponse response = client.toBlocking().exchange('/template/welcome', String)

        then:
        response.getHeaders().contains("Date")
        response.getHeaders().contains("Content-Length")
    }

}
