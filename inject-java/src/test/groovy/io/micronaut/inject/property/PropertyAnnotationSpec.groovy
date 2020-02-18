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
package io.micronaut.inject.property

import io.micronaut.context.ApplicationContext
import spock.lang.Specification

class PropertyAnnotationSpec extends Specification {

    void "test inject properties"() {
        given:
        ApplicationContext ctx = ApplicationContext.run(
                'my.string':'foo',
                'my.int':10,
                'my.map.one':'one',
                'my.map.one.two':'two'
        )

        ConstructorPropertyInject constructorInjectedBean = ctx.getBean(ConstructorPropertyInject)
        MethodPropertyInject methodInjectedBean = ctx.getBean(MethodPropertyInject)
        FieldPropertyInject fieldInjectedBean = ctx.getBean(FieldPropertyInject)

        expect:
        constructorInjectedBean.nullable == null
        constructorInjectedBean.integer == 10
        constructorInjectedBean.str == 'foo'
        constructorInjectedBean.values == ['one':'one', 'one.two':'two']
        methodInjectedBean.nullable == null
        methodInjectedBean.integer == 10
        methodInjectedBean.str == 'foo'
        methodInjectedBean.values == ['one':'one', 'one.two':'two']
        fieldInjectedBean.nullable == null
        fieldInjectedBean.integer == 10
        fieldInjectedBean.str == 'foo'
        fieldInjectedBean.values == ['one':'one', 'one.two':'two']
        fieldInjectedBean.defaultInject == ['one':'one']
    }

    void "test a class with only a property annotation is a bean and injected"() {
        given:
        ApplicationContext ctx = ApplicationContext.run(
                'my.int':10,
        )

        expect:
        ctx.getBean(PropertyOnlyInject).integer == 10
    }
}
