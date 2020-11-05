/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.inject.ast

import spock.lang.Specification

abstract class AbstractClassElementSpec extends Specification {
    protected abstract List<ClassElement> getClassElements()

    def "test #ce.class.name array conversions"() {
        expect:
        !ce.isArray()

        when:
        final arrayType = ce.toArray()

        then:
        arrayType.isArray()

        and:
        arrayType.class === ce.class

        when:
        final elementType = arrayType.fromArray()

        then:
        !elementType.isArray()

        and:
        elementType.class === ce.class

        when:
        elementType.fromArray()

        then:
        thrown IllegalStateException

        where:
        ce << getClassElements()
    }
}
