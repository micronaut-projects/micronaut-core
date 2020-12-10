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
package io.micronaut.annotation.processing.visitor

import io.micronaut.inject.ast.AbstractClassElementSpec
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.PrimitiveElement
import spock.lang.Issue

import javax.lang.model.element.TypeElement

@Issue("https://github.com/micronaut-projects/micronaut-core/issues/4424")
class JavaClassElementSpec extends AbstractClassElementSpec {
    @Override
    protected List<ClassElement> getClassElements() {
        return [new JavaClassElement(Mock(TypeElement), null, null, [:]),
                new JavaEnumElement(Mock(TypeElement), null, null)]
    }

    @Deprecated
    def "test (deprecated) JavaVoidElement array conversions"() {
        given:
        final jve = new JavaVoidElement()

        expect:
        !jve.isArray()

        when:
        final arrayType = jve.toArray()

        then:
        arrayType.isArray()

        and:
        arrayType.class === PrimitiveElement.class

        when:
        jve.fromArray()

        then:
        thrown IllegalStateException
    }
}
