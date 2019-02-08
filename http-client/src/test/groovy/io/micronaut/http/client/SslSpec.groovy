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
package io.micronaut.http.client

import io.micronaut.context.ApplicationContext
import spock.lang.Ignore
import spock.lang.Specification

class SslSpec extends Specification {

    @Ignore // service down at the moment
    void "test that clients work with self signed certificates"() {
        given:
        ApplicationContext ctx = ApplicationContext.run()
        HttpClient client = ctx.createBean(HttpClient, new URL("https://httpbin.org"))

        expect:
        client.toBlocking().retrieve('/get')

        cleanup:
        ctx.close()
        client.close()
    }
}
