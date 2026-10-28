/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.visitors

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.ElementModifier
import io.micronaut.inject.ast.ElementQuery

class NonSealedModifierSpec extends AbstractTypeElementSpec {

    void "test non-sealed modifier is mapped"() {
        expect:
        buildClassElement('''
package test;

non-sealed class Circle implements Shape {}

sealed interface Shape permits Circle, Square {}

final class Square implements Shape {}
''') { ClassElement circle ->
            assert circle.getModifiers() == [ElementModifier.NON_SEALED] as Set
            circle
        }
        ElementModifier.NON_SEALED.toString() == 'non-sealed'
    }

    void "test modifier query on a non-sealed class"() {
        expect:
        buildClassElement('''
package test;

class Holder {
    sealed interface Shape permits Circle {}

    public static non-sealed class Circle implements Shape {}
}
''') { ClassElement holder ->
            def circles = holder.getEnclosedElements(ElementQuery.of(ClassElement)
                    .modifiers(m -> m.contains(ElementModifier.NON_SEALED)))
            assert circles*.name == ['test.Holder$Circle']
            assert circles[0].getModifiers() == [ElementModifier.PUBLIC, ElementModifier.STATIC, ElementModifier.NON_SEALED] as Set
            holder
        }
    }
}
