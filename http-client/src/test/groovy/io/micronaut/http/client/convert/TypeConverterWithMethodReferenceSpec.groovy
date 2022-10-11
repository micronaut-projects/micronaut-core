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
package io.micronaut.http.client.convert

import io.micronaut.context.ApplicationContext
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class TypeConverterWithMethodReferenceSpec extends Specification {

    @Shared @AutoCleanup EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer)
    @Shared HttpClient client = embeddedServer.applicationContext.createBean(HttpClient, embeddedServer.getURL())

    void "test type converters can be used with method references"() {
        when:
        def foo = client.toBlocking().retrieve('/convert/foo/val', Foo)
        def bar = client.toBlocking().retrieve('/convert/bar/val', Bar)

        then:
        foo.value == 'val'
        bar.value == 'val'
    }
}
