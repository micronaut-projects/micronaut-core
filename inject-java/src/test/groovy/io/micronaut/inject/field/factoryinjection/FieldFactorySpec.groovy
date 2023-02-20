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
package io.micronaut.inject.field.factoryinjection

import io.micronaut.context.BeanContext
import io.micronaut.context.DefaultBeanContext
import spock.lang.Specification
/**
 * Created by graemerocher on 12/05/2017.
 */
class FieldFactorySpec extends Specification {

    void "test injection with field supplied by a provider"() {
        given:
        BeanContext context = BeanContext.run()

        when:"A bean is obtained which has a field that depends on a bean provided by a provider"
        B b =  context.getBean(B)

        then:"The implementation is injected"
        b.a != null
        b.a instanceof AImpl
        b.a.c != null
        b.a.c2 != null
        b.a.is(context.getBean(A))

        cleanup:
        context.close()
    }




}

