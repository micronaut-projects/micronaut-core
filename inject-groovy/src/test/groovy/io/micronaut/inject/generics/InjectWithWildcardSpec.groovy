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
package io.micronaut.inject.generics

import io.micronaut.context.ApplicationContext
import io.micronaut.context.DefaultApplicationContext
import io.micronaut.core.convert.ConversionService
import spock.lang.Specification

import javax.inject.Inject

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class InjectWithWildcardSpec extends Specification {


    void "test that wild card injection doesn't produce a ClassNotFoundException"() {
        given:
        ApplicationContext context = new DefaultApplicationContext("test").start()

        expect:
        context.getBean(WildCardInject) instanceof WildCardInject

        cleanup:
        context.close()
    }

    static class WildCardInject {
        // tests injecting field
        @Inject
        protected ConversionService<?> conversionService

        // tests injecting constructor
        WildCardInject(ConversionService<?> conversionService) {
        }

        // tests injection method
        @Inject
        void setConversionService(ConversionService<?> conversionService) {
            this.conversionService = conversionService
        }
    }
}
