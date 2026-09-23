/*
 * Copyright 2017-2026 original authors
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

import io.micronaut.core.annotation.AnnotationMetadata
import spock.lang.Specification

class AllTypeArgumentsSpec extends Specification {

    interface Holder<T> {
    }

    private static final ClassElement VARIABLE = ClassElement.of(Holder.typeParameters[0])

    void "a type argument of a super type two levels up is bound through the level below"() {
        given: "Leaf -> Middle<T=String> -> Ancestor<A=List<T>>"
        def leaf = leafOf(ClassElement.of('java.util.List', true, null, [E: VARIABLE]))

        expect:
        leaf.getTypeArguments('test.Middle')['T'].name == String.name
        leaf.getTypeArguments('test.Ancestor')['A'].name == 'java.util.List'
        leaf.getTypeArguments('test.Ancestor')['A'].typeArguments['E'].name == String.name
    }

    void "a variable of a super type two levels up is bound without copying the type that holds it"() {
        given: "Leaf -> Middle<T=String> -> Ancestor<A=T>"
        def leaf = leafOf(VARIABLE)

        expect:
        leaf.getTypeArguments('test.Ancestor')['A'].name == String.name
    }

    void "a type that does not support being copied is reported unbound rather than failing"() {
        given: "the type holding the variable is a type reference, which does not support copying"
        def uncopyable = ClassElement.of(List, AnnotationMetadata.EMPTY_METADATA, [E: VARIABLE])
        def leaf = leafOf(uncopyable)

        when:
        def arguments = leaf.getTypeArguments('test.Ancestor')

        then: "the hierarchy is still reported, with the variable left as it was"
        noExceptionThrown()
        arguments['A'].typeArguments['E'].is(VARIABLE)

        and: "the level below is bound as usual"
        leaf.getTypeArguments('test.Middle')['T'].name == String.name
    }

    private static ClassElement leafOf(ClassElement ancestorArgument) {
        def ancestor = ClassElement.of('test.Ancestor', true, null, [A: ancestorArgument], null, [])
        def middle = ClassElement.of('test.Middle', true, null, [T: ClassElement.of(String)], null, [ancestor])
        return ClassElement.of('test.Leaf', false, null, [:], null, [middle])
    }
}
