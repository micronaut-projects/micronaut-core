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

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.util.BufferRecycler
import com.fasterxml.jackson.databind.ObjectMapper
import io.micronaut.context.ApplicationContext
import io.micronaut.context.DefaultApplicationContext
import io.micronaut.context.env.MapPropertySource
import io.micronaut.docs.context.annotation.primary.ColorPicker
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import spock.lang.Specification

/**
 * @author Vladislav Chernogorov
 * @since 1.0
 */
class JsonFactorySetupSpec extends Specification {

    void "verify default jackson setup with JsonFactory bean"() {

        given:
        ApplicationContext applicationContext = new DefaultApplicationContext("test").start()

        expect:
        applicationContext.containsBean(JsonFactory)
        applicationContext.containsBean(ObjectMapper)

        cleanup:
        applicationContext?.close()
    }

    void "verify JsonFactory properties are injected into the bean"() {
        given:
        ApplicationContext applicationContext = new DefaultApplicationContext("test")
        applicationContext.environment.addPropertySource(MapPropertySource.of(
                'jackson.factory.use-thread-local-for-buffer-recycling': false
        ))
        applicationContext.start()

        expect:
        applicationContext.containsBean(ObjectMapper)

        when:
        ObjectMapper objectMapper = applicationContext.getBean(ObjectMapper)

        then:
        !objectMapper.getFactory().isEnabled(JsonFactory.Feature.USE_THREAD_LOCAL_FOR_BUFFER_RECYCLING)

    }
}
