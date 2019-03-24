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
package io.micronaut

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
abstract class AbstractMicronautSpec extends Specification {

    static final SPEC_NAME_PROPERTY = 'spec.name'

    @Shared File uploadDir = File.createTempDir()
    @Shared @AutoCleanup ApplicationContext context = ApplicationContext.run(
            getConfiguration() << [(SPEC_NAME_PROPERTY):getClass().simpleName]
    )
    @Shared EmbeddedServer embeddedServer = context.getBean(EmbeddedServer).start()
    @Shared @AutoCleanup HttpClient client = context.createBean(HttpClient, embeddedServer.getURL())


    Map<String, Object> getConfiguration() {
        ['micronaut.server.multipart.location':uploadDir.absolutePath]
    }


    def cleanupSpec()  {
        uploadDir.delete()
    }
}