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
package io.micronaut.inject.qualifiers

import io.micronaut.context.BeanContext
import io.micronaut.context.DefaultBeanContext
import io.micronaut.context.annotation.Type
import spock.lang.Specification

import jakarta.inject.Singleton

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class ByTypeSpec extends Specification {

    void "test by type qualifier injection"() {
        given:
        BeanContext context = new DefaultBeanContext().start()

        when:
        Bean b = context.getBean(Bean)

        then:
        b.foos.find { it instanceof One}
        b.foos.find { it instanceof Two}
        !b.foos.find { it instanceof Three}
    }

    static interface Foo {}

    @Singleton
    static class One implements Foo {

    }
    @Singleton
    static class Two implements Foo {

    }
    @Singleton
    static class Three implements Foo {

    }

    @Singleton
    static class Bean {
        List<Foo> foos = []

        Bean(@Type([One,Two]) Foo[] foos) {
            this.foos = Arrays.asList(foos)
        }
    }
}
