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
package io.micronaut.management.endpoint.beans

import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.Environment
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.ReactorHttpClient
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.Specification

class BeansEndpointSpec extends Specification {

    /**
     * Known failure of the scope. Relies on changes to the annotation
     * metadata to return the correct result.
     */
    void "test beans endpoint"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, ['endpoints.beans.sensitive': false], Environment.TEST)
        ReactorHttpClient rxClient = embeddedServer.applicationContext.createBean(ReactorHttpClient, embeddedServer.getURL())

        when:
        def response = rxClient.exchange("/beans", Map).blockFirst()
        Map result = response.body()
        Map<String, Map<String, Object>> beans = result.beans

        then:
        response.code() == HttpStatus.OK.code
        beans["io.micronaut.management.endpoint.beans.\$BeansEndpointDefinition"].dependencies.contains("io.micronaut.context.BeanContext")
        beans["io.micronaut.management.endpoint.beans.\$BeansEndpointDefinition"].dependencies.contains("io.micronaut.management.endpoint.beans.BeanDefinitionDataCollector")
        beans["io.micronaut.management.endpoint.beans.\$BeansEndpointDefinition"].scope == "io.micronaut.management.endpoint.annotation.Endpoint"
        beans["io.micronaut.management.endpoint.beans.\$BeansEndpointDefinition"].type == "io.micronaut.management.endpoint.beans.BeansEndpoint"

        cleanup:
        rxClient.close()
        embeddedServer.close()
    }
}
