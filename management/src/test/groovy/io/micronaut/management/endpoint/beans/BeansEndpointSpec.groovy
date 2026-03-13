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
import io.micronaut.context.RuntimeBeanDefinition
import io.micronaut.context.env.Environment
import io.micronaut.http.HttpResponse
import io.micronaut.core.annotation.AnnotationUtil
import io.micronaut.inject.qualifiers.Qualifiers
import io.micronaut.http.HttpStatus
import io.micronaut.inject.writer.BeanDefinitionWriter
import io.micronaut.http.client.HttpClient
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
        HttpClient rxClient = embeddedServer.applicationContext.createBean(HttpClient, embeddedServer.getURL())

        when:
        HttpResponse<Map> response = rxClient.toBlocking().exchange("/beans", Map)
        Map result = response.body()
        Map<String, Map<String, Object>> beans = result.beans

        then:
        response.code() == HttpStatus.OK.code
        beans["io.micronaut.management.endpoint.beans.\$BeansEndpoint" + BeanDefinitionWriter.CLASS_SUFFIX].dependencies.contains("io.micronaut.management.endpoint.beans.BeanDefinitionDataCollector")
        beans["io.micronaut.management.endpoint.beans.\$BeansEndpoint" + BeanDefinitionWriter.CLASS_SUFFIX].scope == AnnotationUtil.SINGLETON
        beans["io.micronaut.management.endpoint.beans.\$BeansEndpoint" + BeanDefinitionWriter.CLASS_SUFFIX].type == "io.micronaut.management.endpoint.beans.BeansEndpoint"
        result.disabled.find {
            it.type == 'io.micronaut.logging.PropertiesLoggingLevelsConfigurer'
        }.reasons == ["Required property [logger] not present"]

        cleanup:
        rxClient.close()
        embeddedServer.close()
    }

    void "test beans endpoint handles runtime bean definitions with duplicate implementation class"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.builder()
            .properties(['endpoints.beans.sensitive': false])
            .beanDefinitions(
                RuntimeBeanDefinition.builder(String, () -> "one").singleton(true).named("one").build(),
                RuntimeBeanDefinition.builder(String, () -> "two").singleton(true).named("two").build()
            )
            .environments(Environment.TEST)
            .run(EmbeddedServer)
        HttpClient rxClient = embeddedServer.applicationContext.createBean(HttpClient, embeddedServer.getURL())

        when:
        HttpResponse<Map> response = rxClient.toBlocking().exchange("/beans", Map)
        Map result = response.body()
        Map<String, Map<String, Object>> beans = result.beans
        List<Map<String, Object>> runtimeBeans = beans.findAll { key, value ->
            key.startsWith('io.micronaut.context.RuntimeBeanDefinition#') && value.type == 'java.lang.String'
        }.values().toList()

        then:
        response.code() == HttpStatus.OK.code
        runtimeBeans*.qualifier.toSet() == [Qualifiers.byName('one').toString(), Qualifiers.byName('two').toString()] as Set

        cleanup:
        rxClient.close()
        embeddedServer.close()
    }

}
