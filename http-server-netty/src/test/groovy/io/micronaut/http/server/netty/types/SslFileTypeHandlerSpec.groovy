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
package io.micronaut.http.server.netty.types

import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpStatus
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.server.netty.AbstractMicronautSpec

class SslFileTypeHandlerSpec extends AbstractMicronautSpec {

    private static File tempFile

    static {
        tempFile = File.createTempFile("sslFileTypeHandlerSpec", ".html")
        tempFile.write("<html><head></head><body>HTML Page</body></html>")
        tempFile
    }

    void "test returning a file from a controller"() {
        when:
        def response = rxClient.exchange('/test/html', String).blockingFirst()

        then:
        response.code() == HttpStatus.OK.code
        response.body() == "<html><head></head><body>HTML Page</body></html>"
    }

    @Override
    Map<String, Object> getConfiguration() {
        super.getConfiguration() << ['micronaut.ssl.enabled': true, 'micronaut.ssl.buildSelfSigned': true]
    }

    @Controller('/test')
    @Requires(property = 'spec.name', value = 'SslFileTypeHandlerSpec')
    static class TestController {

        @Get('/html')
        File html() {
            tempFile
        }
    }
}
