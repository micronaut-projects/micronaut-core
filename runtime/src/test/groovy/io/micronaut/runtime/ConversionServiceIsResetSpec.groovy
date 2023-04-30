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
package io.micronaut.runtime

import io.micronaut.context.ApplicationContext
import io.micronaut.core.convert.ConversionContext
import io.micronaut.core.convert.ConversionService
import io.micronaut.core.convert.MutableConversionService
import io.micronaut.core.convert.TypeConverter
import spock.lang.Specification

class ConversionServiceIsResetSpec extends Specification {

    void "test ConversionService is reset on stop/start"() {
        given:
            ApplicationContext ctx = ApplicationContext.run()
            TypeConverter<A, B> typeConverter = new TypeConverter<A, B>() {
                @Override
                Optional<B> convert(A object, Class<B> targetType, ConversionContext context) {
                    return Optional.of(new B())
                }
            }
            ctx.getBean(MutableConversionService).addConverter(A, B, typeConverter)

        when:
            def result = ctx.getBean(ConversionService).convert(new A(), B)

        then:
            result.isPresent()

        when:
            ctx.stop()
            ctx.start()
        then:
            !ctx.getBean(ConversionService).convert(new A(), B).isPresent()
        and:
            try (ApplicationContext ctx2 = ApplicationContext.run()) {
                assert !ctx2.getBean(ConversionService).convert(new A(), B).isPresent()
            }
        cleanup:
            ctx.close()
    }

}

class A {

}

class B {

}
