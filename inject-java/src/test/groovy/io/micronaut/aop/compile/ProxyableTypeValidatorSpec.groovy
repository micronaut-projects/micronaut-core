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
package io.micronaut.aop.compile

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.processing.ProxyableTypeValidator
import spock.lang.Unroll

/**
 * The reason a type cannot be proxied is answered without a failure being raised.
 */
class ProxyableTypeValidatorSpec extends AbstractTypeElementSpec {

    @Unroll
    void "#description: #reason"() {
        expect:
        buildClassElement(source) { ClassElement element ->
            assert element.name == 'test.MyBean'
            ProxyableTypeValidator.whyUnproxyable(element).orElse(null)
        } == reason

        where:
        description           | source                                                                                      | reason
        'a class'             | 'package test; class MyBean {}'                                                             | null
        'an interface'        | 'package test; interface MyBean {}'                                                         | null
        'a non-sealed class'  | 'package test; non-sealed class MyBean extends Base {} sealed class Base permits MyBean {}' | null
        'a final class'       | 'package test; final class MyBean {}'                                                       | 'Cannot apply AOP advice to final class. Class must be made non-final to support proxying: test.MyBean'
        'a record'            | 'package test; record MyBean(String name) {}'                                               | 'Cannot apply AOP advice to final class. Class must be made non-final to support proxying: test.MyBean'
        'a sealed class'      | 'package test; sealed class MyBean permits MyBean.Only { static final class Only extends MyBean {} }' | 'Cannot apply AOP advice to sealed type. Type must be made non-sealed to support proxying: test.MyBean'
        'a sealed interface'  | 'package test; sealed interface MyBean permits MyBean.Only { final class Only implements MyBean {} }' | 'Cannot apply AOP advice to sealed type. Type must be made non-sealed to support proxying: test.MyBean'
    }
}
