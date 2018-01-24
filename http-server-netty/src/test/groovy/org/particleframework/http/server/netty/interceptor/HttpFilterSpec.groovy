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
package org.particleframework.http.server.netty.interceptor

import org.particleframework.http.HttpResponse
import org.particleframework.http.HttpStatus
import org.particleframework.http.client.exceptions.HttpClientResponseException
import org.particleframework.http.server.netty.AbstractParticleSpec

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class HttpFilterSpec extends AbstractParticleSpec {

    void "test interceptor execution and order - write replacement"() {
        when:
        rxClient.retrieve("/secure").blockingFirst()

        then:
        def e = thrown(HttpClientResponseException)
        e.status == HttpStatus.FORBIDDEN
    }

    void "test interceptor execution and order - proceed"() {
        when:
        HttpResponse<String> response = rxClient.exchange("/secure?username=fred", String).blockingFirst()

        then:
        response.status == HttpStatus.OK
        response.headers.get("X-Test") == "Foo Test"
        response.body.isPresent()
        response.body.get() == "Authenticated: fred"
    }
}
