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
package io.micronaut.upload.browser

import geb.spock.GebSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.Environment
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Requires
import spock.lang.Shared

class UploadBrowserSpec extends GebSpec {
    @Shared
    @AutoCleanup
    ApplicationContext context = ApplicationContext.run(
            [
                    'spec.name': UploadBrowserSpec.simpleName,
            ], Environment.TEST)

    @Shared
    EmbeddedServer embeddedServer = context.getBean(EmbeddedServer).start()

    def "submitting a form without a file triggers an error"() {
        given:
        browser.baseUrl = "http://localhost:${embeddedServer.port}"

        when:
        go '/image/create'

        then:
        at CreatePage

        when:
        CreatePage page = browser.page CreatePage
        page.upload()

        then:
        at FileEmptyPage
    }
}
