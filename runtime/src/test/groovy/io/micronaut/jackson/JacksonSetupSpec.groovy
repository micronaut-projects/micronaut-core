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

        applicationContext.containsBean(JacksonConfiguration)
        applicationContext.getBean(ObjectMapper.class).valueToTree([foo: 'bar']).get('foo').textValue() == 'bar'
        !applicationContext.getBean(JacksonConfiguration).propertyNamingStrategy

        cleanup:
        applicationContext?.close()
    }

    void "verify json object mapper is primary"() {
        given:
        ApplicationContext applicationContext = new DefaultApplicationContext("test").start()
        ObjectMapper objectMapper = applicationContext.getBean(ObjectMapper.class)

        expect:
        objectMapper.readTree('{"foo" : "bar"}').get('foo').textValue() == 'bar'

        cleanup:
        applicationContext?.close()
    }

    void "verify custom jackson setup"() {

        given:
        ApplicationContext applicationContext = new DefaultApplicationContext("test")
        applicationContext.environment.addPropertySource(MapPropertySource.of(
                'jackson.dateFormat': 'yyMMdd',
                'jackson.serialization.indentOutput': true,
                'jackson.json-view.enabled': true
        ))
        applicationContext.start()

        expect:
        applicationContext.containsBean(ObjectMapper.class)
        applicationContext.getBean(ObjectMapper.class).valueToTree([foo: 'bar']).get('foo').textValue() == 'bar'

        applicationContext.containsBean(JacksonConfiguration)
        applicationContext.getBean(JacksonConfiguration).dateFormat == 'yyMMdd'
        applicationContext.getBean(JacksonConfiguration).serializationSettings.get(SerializationFeature.INDENT_OUTPUT)

        cleanup:
        applicationContext?.close()
    }

    void "verify that the defaultTyping configuration option is correctly converted and set on the object mapper"() {
        given:
        ApplicationContext applicationContext = new DefaultApplicationContext("test")
        applicationContext.environment.addPropertySource(MapPropertySource.of(
                'jackson.dateFormat': 'yyMMdd',
                'jackson.defaultTyping': 'NON_FINAL'
        ))
        applicationContext.start()

        expect:
        applicationContext.containsBean(ObjectMapper.class)
        ((ObjectMapper.DefaultTypeResolverBuilder) applicationContext.getBean(ObjectMapper.class).deserializationConfig.getDefaultTyper(null))._appliesFor == ObjectMapper.DefaultTyping.NON_FINAL

        applicationContext.containsBean(JacksonConfiguration)
        applicationContext.getBean(JacksonConfiguration).defaultTyping == ObjectMapper.DefaultTyping.NON_FINAL

        cleanup:
        applicationContext?.close()
    }

    @Unroll
    void 'Configuring #configuredJackonPropertyNamingStrategy sets PropertyNamingStrategy on the Context ObjectMapper.'() {
        when:
        ApplicationContext applicationContext = ApplicationContext.run(MapPropertySource.of(
                'jackson.property-naming-strategy': configuredJackonPropertyNamingStrategy.toString()
        ))

        then:
        applicationContext.containsBean(JacksonConfiguration)
        applicationContext.getBean(JacksonConfiguration).propertyNamingStrategy == expectedPropertyNamingStrategy

        applicationContext.containsBean(ObjectMapper.class)
        applicationContext.getBean(ObjectMapper.class).getPropertyNamingStrategy() == expectedPropertyNamingStrategy

        cleanup:
        applicationContext?.close()

        where:
        configuredJackonPropertyNamingStrategy | expectedPropertyNamingStrategy
        'SNAKE_CASE'                           | PropertyNamingStrategy.SNAKE_CASE
        'UPPER_CAMEL_CASE'                     | PropertyNamingStrategy.UPPER_CAMEL_CASE
        'LOWER_CAMEL_CASE'                     | PropertyNamingStrategy.LOWER_CAMEL_CASE
        'LOWER_CASE'                           | PropertyNamingStrategy.LOWER_CASE
        'KEBAB_CASE'                           | PropertyNamingStrategy.KEBAB_CASE
    }

    void "test property naming strategy from yml"() {
        ApplicationContext applicationContext = ApplicationContext.run("jackson")

        expect:
        applicationContext.getBean(JacksonConfiguration).propertyNamingStrategy == PropertyNamingStrategy.SNAKE_CASE
    }

    void "verify trim strings with custom property enabled"() {
        given:
        ApplicationContext applicationContext = new DefaultApplicationContext("test")
        applicationContext.environment.addPropertySource(MapPropertySource.of(
                'jackson.trim-strings': true
        ))
        applicationContext.start()

        expect:
        applicationContext.getBean(ObjectMapper.class).readValue('{"foo": "  bar  "}', Map.class).get("foo") == "bar"

        cleanup:
        applicationContext?.close()
    }

    void "verify strings are not trimmed by default"() {
        given:
        ApplicationContext applicationContext = new DefaultApplicationContext("test")
        applicationContext.start()

        expect:
        applicationContext.getBean(ObjectMapper.class).readValue('{"foo": "  bar  "}', Map.class).get("foo") == "  bar  "


        cleanup:
        applicationContext?.close()
    }
}
