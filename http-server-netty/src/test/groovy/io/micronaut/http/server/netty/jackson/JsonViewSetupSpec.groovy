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
package io.micronaut.http.server.netty.jackson


import io.micronaut.context.ApplicationContext
import io.micronaut.context.DefaultApplicationContext
import io.micronaut.context.env.MapPropertySource
import io.micronaut.jackson.JacksonConfiguration
import spock.lang.Specification

class JsonViewSetupSpec extends Specification {

    void "verify default jackson setup with @JsonView disabled"() {

        given:
        ApplicationContext applicationContext = new DefaultApplicationContext("test").start()

        expect:
        applicationContext.containsBean(JacksonConfiguration)
        !applicationContext.containsBean(JsonViewMediaTypeCodecFactory)
        !applicationContext.containsBean(JsonViewServerFilter)

        cleanup:
        applicationContext?.close()
    }


    void "verify jackson setup with @JsonView enabled"() {

        given:
        ApplicationContext applicationContext = new DefaultApplicationContext("test")
        applicationContext.environment.addPropertySource(MapPropertySource.of(
                'jackson.json-view.enabled': true
        ))
        applicationContext.start()

        expect:
        applicationContext.containsBean(JacksonConfiguration)
        applicationContext.containsBean(JsonViewMediaTypeCodecFactory)
        applicationContext.containsBean(JsonViewServerFilter)

        cleanup:
        applicationContext?.close()
    }
}
