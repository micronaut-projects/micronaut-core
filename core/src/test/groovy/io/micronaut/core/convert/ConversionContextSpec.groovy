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
package io.micronaut.core.convert

import io.micronaut.core.type.DefaultArgument
import spock.lang.Specification
import spock.lang.Unroll

import static org.codehaus.groovy.runtime.DefaultGroovyMethods.iterator

class ConversionContextSpec extends Specification {

    @Unroll
    void "test conversion context with argument propagates error context"() {
        given:
        def conversionError = new ConversionError() {
            @Override Exception getCause() {
                return null
            }
        }

        ConversionContext conversionContext = new ConversionContext() {
            Object value
            Exception exception

            @Override void reject(Exception exception) {
                reject(null, exception)
            }

            @Override void reject(Object value, Exception exception) {
                this.value = value
                this.exception = exception
            }

            @Override Iterator<ConversionError> iterator() {
                return iterator(conversionError)
            }

            @Override Optional<ConversionError> getLastError() {
                return Optional.of(conversionError)
            }
        }


        def value = 1L
        def exception = new Exception()
        DefaultArgument<Object> argument = new DefaultArgument<>(Object.class, "name", conversionContext.getAnnotationMetadata())

        when:
        def conversionContextWithArgument1 = conversionContext.with(argument)
        def conversionContextWithArgument2 = conversionContext.with(argument)

        then:
        conversionContext != conversionContextWithArgument1
        conversionContext != conversionContextWithArgument2

        conversionContextWithArgument1.iterator().next() == conversionError
        conversionContextWithArgument1.getLastError().get() == conversionError

        conversionContextWithArgument2.iterator().next() == conversionError
        conversionContextWithArgument2.getLastError().get() == conversionError


        when:
        conversionContextWithArgument1.reject(exception)

        then:
        conversionContext.exception == exception
        conversionContext.value == null


        when:
        conversionContextWithArgument2.reject(value, exception)

        then:
        conversionContext.exception == exception
        conversionContext.value == value
    }
}
