/*
 * Copyright 2017-2021 original authors
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
package io.micronaut.http.server.netty.jackson

import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.MapPropertySource
import io.micronaut.context.env.PropertySource
import spock.lang.Specification
import tools.jackson.core.json.JsonFactory
import tools.jackson.databind.ObjectMapper

/**
 * @author Vladislav Chernogorov
 * @since 1.0
 */
class JsonFactorySetupSpec extends Specification {

    void "verify default jackson setup with JsonFactory bean"() {
        given:
        ApplicationContext applicationContext = ApplicationContext.builder("test").start()

        expect:
        applicationContext.containsBean(JsonFactory)
        applicationContext.containsBean(ObjectMapper)

        cleanup:
        applicationContext?.close()
    }

    void "verify JsonFactory properties are injected into the bean"() {
        given:
        ApplicationContext applicationContext = ApplicationContext.builder("test").build()
        applicationContext.environment.addPropertySource((MapPropertySource) PropertySource.of(
                'jackson.json-factory-features.fail-on-symbol-hash-overflow': false
        ))
        applicationContext.start()

        expect:
        applicationContext.containsBean(ObjectMapper)

        when:
        ObjectMapper objectMapper = applicationContext.getBean(ObjectMapper)

        then:
        !objectMapper.tokenStreamFactory().isEnabled(JsonFactory.Feature.FAIL_ON_SYMBOL_HASH_OVERFLOW)

    }
}
