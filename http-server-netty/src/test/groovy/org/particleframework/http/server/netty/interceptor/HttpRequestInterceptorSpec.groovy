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

import okhttp3.Request
import org.particleframework.http.HttpStatus
import org.particleframework.http.server.netty.AbstractParticleSpec
import spock.lang.Ignore

/**
 * @author Graeme Rocher
 * @since 1.0
 */
@Ignore // disable for now until interceptor code is rewritten
class HttpRequestInterceptorSpec extends AbstractParticleSpec {

    void "test interceptor execution and order - write replacement"() {
        when:
        def request = new Request.Builder()
                .url("$server/secure")
                .get()

        def response = client.newCall(
                request.build()
        ).execute()

        then:
        response.code() == HttpStatus.FORBIDDEN.code
    }

    void "test interceptor execution and order - proceed"() {
        when:
        def request = new Request.Builder()
                .url("$server/secure?username=fred")
                .get()

        def response = client.newCall(
                request.build()
        ).execute()

        then:
        response.code() == HttpStatus.OK.code
        response.body().string() == "Authenticated: fred"
    }
}
