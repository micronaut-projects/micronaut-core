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
package io.micronaut.jackson.databind.convert

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.databind.node.NullNode
import io.micronaut.context.ApplicationContext
import io.micronaut.core.convert.ArgumentConversionContext
import io.micronaut.core.convert.ConversionService
import io.micronaut.core.type.Argument
import io.micronaut.json.tree.JsonNode
import spock.lang.Specification

class JsonNodeToObjectConverterSpec extends Specification {

    void "test the converter handles NullNode correctly"() {
        given:
        def ctx = ApplicationContext.run()
        def converter = ctx.getBean(ConversionService)

        when:
        Optional optional = converter.convert(NullNode.instance, Pojo.class)

        then:
        noExceptionThrown()
        !optional.isPresent()

        cleanup:
        ctx.close()
    }

    class Pojo {

    }

    void "deserialize optional arguments properly"() {
        given:
        def ctx = ApplicationContext.run()
        def converter = ctx.getBean(ConversionService)

        when:
        Optional<Optional<WrappedValue>> optional = converter.convert(JsonNode.createStringNode("foo"), Optional.class, new ArgumentConversionContext() {
            @Override
            Argument getArgument() {
                return Argument.of(Optional.class, WrappedValue.class)
            }
        })

        then:
        noExceptionThrown()
        optional.isPresent()
        optional.get().isPresent()
        optional.get().get().value == 'foo'

        cleanup:
        ctx.close()
    }

    static class WrappedValue {
        String value

        @JsonCreator
        WrappedValue(String value) {
            this.value = value
        }
    }
}
