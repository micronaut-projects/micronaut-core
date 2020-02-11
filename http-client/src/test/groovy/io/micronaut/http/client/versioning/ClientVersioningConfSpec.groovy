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
package io.micronaut.http.client.versioning

import io.micronaut.context.ApplicationContext
import io.micronaut.http.client.interceptor.configuration.ClientVersioningConfiguration
import io.micronaut.http.client.interceptor.configuration.DefaultClientVersioningConfiguration
import io.micronaut.inject.qualifiers.Qualifiers
import spock.lang.Specification

class ClientVersioningConfSpec extends Specification {

    def "should not contain any versioning configurations in context"() {
        when:
        def context = ApplicationContext.run("test")
        then:
        context.getBeansOfType(ClientVersioningConfiguration, Qualifiers.byName("simple")).isEmpty()
        context.getBean(ClientVersioningConfiguration).headers == [DefaultClientVersioningConfiguration.DEFAULT_HEADER_NAME]
        context.getBean(ClientVersioningConfiguration).parameters == []

        cleanup:
        context.close()
    }

    def "should contain versioning configuration in context"() {
        when:
        def context = ApplicationContext.run(["micronaut.http.client.versioning.simple.headers": ["X-API"]],
                "test")
        then:
        context.getBean(ClientVersioningConfiguration, Qualifiers.byName("simple")) != null

        cleanup:
        context.close()
    }

    def "should contain headers and parameters for configuration"() {
        when:
        def context = ApplicationContext.run(["micronaut.http.client.versioning.simple.headers"   : ["X-API"],
                                              "micronaut.http.client.versioning.simple.parameters": ["api-version"],
                                              "micronaut.http.client.versioning.default.parameters": ["version"]],
                "test")
        def simpleConfig = context.getBean(ClientVersioningConfiguration, Qualifiers.byName("simple"))
        def defaultConfig = context.getBean(ClientVersioningConfiguration)
        then:
        simpleConfig.getHeaders() == ["X-API"]
        simpleConfig.getParameters() == ["api-version"]
        defaultConfig.getParameters() == ["version"]

        cleanup:
        context.close()
    }


}