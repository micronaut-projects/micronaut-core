/*
 * Copyright 2017-2018 original authors
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
package io.micronaut.jackson

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategy
import com.fasterxml.jackson.databind.SerializationFeature
import io.micronaut.context.ApplicationContext
import io.micronaut.context.DefaultApplicationContext
import io.micronaut.context.env.MapPropertySource
import spock.lang.Specification
import spock.lang.Unroll

/**
 * Created by graemerocher on 31/08/2017.
 */
class JacksonSetupSpec extends Specification {

    void "verify default jackson setup"() {

        given:
        ApplicationContext applicationContext = new DefaultApplicationContext("test").start()

        expect:
        applicationContext.containsBean(ObjectMapper.class)
        applicationContext.getBean(ObjectMapper.class).getPropertyNamingStrategy() == PropertyNamingStrategy.LOWER_CAMEL_CASE

        applicationContext.containsBean(JacksonConfiguration)
        applicationContext.getBean(ObjectMapper.class).valueToTree([foo:'bar']).get('foo').textValue() == 'bar'
        applicationContext.getBean(JacksonConfiguration).propertyNamingStrategy == JacksonPropertyNamingStrategy.LOWER_CAMEL_CASE

        cleanup:
        applicationContext?.close()
    }


    void "verify custom jackson setup"() {

        given:
        ApplicationContext applicationContext = new DefaultApplicationContext("test")
        applicationContext.environment.addPropertySource(MapPropertySource.of(
                'jackson.dateFormat':'yyMMdd',
                'jackson.serialization.indentOutput':true
        ))
        applicationContext.start()

        expect:
        applicationContext.containsBean(ObjectMapper.class)
        applicationContext.getBean(ObjectMapper.class).valueToTree([foo:'bar']).get('foo').textValue() == 'bar'

        applicationContext.containsBean(JacksonConfiguration)
        applicationContext.getBean(JacksonConfiguration).dateFormat == 'yyMMdd'
        applicationContext.getBean(JacksonConfiguration).serializationSettings.get(SerializationFeature.INDENT_OUTPUT)

        cleanup:
        applicationContext?.close()
    }

    @Unroll
    void 'Configuring JacksonPropertyNamingStrategy.#configuredJackonPropertyNamingStrategy sets PropertyNamingStrategy on the Context ObjectMapper.'() {
        when:
        ApplicationContext applicationContext = ApplicationContext.run(MapPropertySource.of(
                'jackson.property-naming-strategy': configuredJackonPropertyNamingStrategy.toString()
        ))

        then:
        applicationContext.containsBean(JacksonConfiguration)
        applicationContext.getBean(JacksonConfiguration).propertyNamingStrategy == configuredJackonPropertyNamingStrategy

        applicationContext.containsBean(ObjectMapper.class)
        applicationContext.getBean(ObjectMapper.class).getPropertyNamingStrategy() == expectedPropertyNamingStrategy

        cleanup:
        applicationContext?.close()

        where:
        configuredJackonPropertyNamingStrategy         | expectedPropertyNamingStrategy
        JacksonPropertyNamingStrategy.SNAKE_CASE       | PropertyNamingStrategy.SNAKE_CASE
        JacksonPropertyNamingStrategy.UPPER_CAMEL_CASE | PropertyNamingStrategy.UPPER_CAMEL_CASE
        JacksonPropertyNamingStrategy.LOWER_CAMEL_CASE | PropertyNamingStrategy.LOWER_CAMEL_CASE
        JacksonPropertyNamingStrategy.LOWER_CASE       | PropertyNamingStrategy.LOWER_CASE
        JacksonPropertyNamingStrategy.KEBAB_CASE       | PropertyNamingStrategy.KEBAB_CASE
    }
}
