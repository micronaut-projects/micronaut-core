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
package io.micronaut.jackson.bind

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.PropertyNamingStrategy
import io.micronaut.context.ApplicationContext
import io.micronaut.core.convert.ArgumentConversionContext
import io.micronaut.core.convert.ConversionContext
import io.micronaut.core.convert.ConversionError
import io.micronaut.core.convert.ConversionService
import spock.lang.Specification
import spock.lang.Unroll

class CharSequencePropertyNamingStrategyConverterSpec extends Specification {

    @Unroll
    void 'test configuring #propertyNaminStrategyString converts to correct PropertyNamingStrategy'() {
        given:
        def ctx = ApplicationContext.run()
        def converter = ctx.getBean(ConversionService)

        when:
        Optional<PropertyNamingStrategy> actualPropertyNamingStrategy = converter.convert(
                propertyNaminStrategyString, PropertyNamingStrategy)

        then:
        actualPropertyNamingStrategy.isPresent()
        actualPropertyNamingStrategy.get() == expectedPropertyNamingStrategy

        cleanup:
        ctx.close()

        where:
        propertyNaminStrategyString | expectedPropertyNamingStrategy
        'SNAKE_CASE'                | PropertyNamingStrategies.SNAKE_CASE
        'UPPER_CAMEL_CASE'          | PropertyNamingStrategies.UPPER_CAMEL_CASE
        'LOWER_CAMEL_CASE'          | PropertyNamingStrategies.LOWER_CAMEL_CASE
        'LOWER_DOT_CASE'            | PropertyNamingStrategies.LOWER_DOT_CASE
        'LOWER_CASE'                | PropertyNamingStrategies.LOWER_CASE
        'KEBAB_CASE'                | PropertyNamingStrategies.KEBAB_CASE
    }

    @Unroll
    void 'test invalid String #invalidString throws IllegalArgumentException'() {
        given:
        def ctx = ApplicationContext.run()
        def converter = ctx.getBean(ConversionService)

        when:
        ConversionContext conversionContext = ArgumentConversionContext.of(CharSequence)
        converter.convert(invalidString, PropertyNamingStrategy, conversionContext)
        ConversionError conversionError = conversionContext.last()

        then:
        conversionError.cause instanceof IllegalArgumentException
        conversionError.cause.message == "Unable to convert '$invalidString' to a com.fasterxml.jackson.databind.PropertyNamingStrategy"

        cleanup:
        ctx.close()

        where:
        invalidString | _
        ''            | _
        'CASE'        | _
    }
}
