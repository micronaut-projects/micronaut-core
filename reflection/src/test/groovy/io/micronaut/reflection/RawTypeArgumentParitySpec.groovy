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
package io.micronaut.reflection

import io.micronaut.core.beans.BeanIntrospection
import io.micronaut.core.type.Argument
import io.micronaut.core.type.GenericPlaceholder
import spock.lang.Specification
import spock.lang.Unroll

/**
 * A class read reflectively must not disagree with the same class compiled about which of its types were
 * written raw: the compiled argument keeps the type arguments the declaring type declares, and only
 * {@link Argument#isRawType()} tells a raw usage apart from one written with those variables.
 */
class RawTypeArgumentParitySpec extends Specification {

    private BeanIntrospection<RawParityBean> generated = BeanIntrospection.getIntrospection(RawParityBean)
    private BeanIntrospection<RawParityBean> reflective = ReflectionBeanIntrospection.of(RawParityBean)

    private Argument<?> generated(String name) {
        generated.getRequiredProperty(name, Object).asArgument()
    }

    private Argument<?> reflective(String name) {
        reflective.getRequiredProperty(name, Object).asArgument()
    }

    @Unroll
    void "both descriptions agree that #name is raw: #raw"() {
        expect:
        generated(name).isRawType() == raw
        reflective(name).isRawType() == raw

        and: "and on the type arguments it keeps"
        generated(name).typeParameters*.type == parameters
        reflective(name).typeParameters*.type == parameters

        where:
        name        | raw   | parameters
        'raw'       | true  | [Object]
        'variable'  | false | [Number]
        'concrete'  | false | [String]
        'object'    | false | [Object]
        'box'       | true  | [Number]
        'nested'    | false | [String, List]
    }

    void "a raw usage is told apart from one written with a type variable, which the placeholder alone cannot"() {
        expect: "each keeps a placeholder of the variable the declaring type declares"
        [generated('raw'), reflective('raw'), generated('variable'), reflective('variable')].every {
            it.typeParameters[0] instanceof GenericPlaceholder && it.typeParameters[0].isTypeVariable()
        }

        and: "and only the raw one says so"
        generated('raw').isRawType()
        reflective('raw').isRawType()
        !generated('variable').isRawType()
        !reflective('variable').isRawType()
    }

    void "a raw type argument of a parameterized type is raw in both descriptions"() {
        expect:
        !generated('nested').isRawType()
        !reflective('nested').isRawType()
        generated('nested').typeParameters[1].isRawType()
        reflective('nested').typeParameters[1].isRawType()
        generated('nested').typeParameters[1].typeParameters*.type == [Object]
        reflective('nested').typeParameters[1].typeParameters*.type == [Object]
    }
}
