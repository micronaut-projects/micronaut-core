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
package io.micronaut.inject.field.simpleinjection

import io.micronaut.context.BeanContext
import io.micronaut.context.DefaultBeanContext
import io.micronaut.inject.AbstractTypeElementSpec
import io.micronaut.inject.qualifiers.Qualifiers
import spock.lang.Specification

class FieldInjectionSpec extends AbstractTypeElementSpec {

    void 'test field injection generics'() {
        given:
        def definition = buildBeanDefinition('fieldinjection.Test', '''
package fieldinjection;

import javax.inject.*;

@Singleton
class Test {
    @Inject
    java.util.List<Bar> bars;   
}

class Bar {

}
''')
        expect:
        definition.injectedFields.first().asArgument().firstTypeVariable.get().type.name == 'fieldinjection.Bar'
    }

    void "test injection via field with interface"() {
        given:
        BeanContext context = new DefaultBeanContext()
        context.start()

        when:"Alpha bean is obtained that has a field with @Inject"
        B b =  context.getBean(B)

        then:"The implementation is injected"
        b.a != null
    }
}

