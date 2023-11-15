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
package io.micronaut.json.convert

import io.micronaut.context.ApplicationContext
import io.micronaut.core.annotation.Introspected
import io.micronaut.core.convert.exceptions.ConversionErrorException
import spock.lang.Specification

class JsonConverterRegistrarSpec extends Specification {

    void "throw ConversionErrorException when there is problem with serialisation"() {

        given:
        ApplicationContext ctx = ApplicationContext.run([
                'micronaut.codec.json.additional-types': ['text/javascript', 'text/json']
        ])

        when:
        JsonConverterRegistrar registrar = ctx.getBean(JsonConverterRegistrar)
        def converter = registrar.mapToObjectConverter()
        Map<String, Object> jsonMap = Map.of("firstName", "test", "constraint", Map.of("type", "age", "value", "18"))
        converter.convert(jsonMap, OuterClass.class)

        then:
        def conversionError = thrown(ConversionErrorException.class)
        conversionError.getMessage().contains("Ensure the class is annotated with io.micronaut.core.annotation.Introspected")
    }

    @Introspected
    static class OuterClass {
        String firstName
        Constraint constraint
    }

    static record Constraint(Type type, String value) {
        enum Type {
            AGE,
            SEX
        }
    }

}
