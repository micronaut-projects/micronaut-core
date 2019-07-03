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
package io.micronaut.inject.qualifiers.primary

import io.micronaut.context.BeanContext
import io.micronaut.context.exceptions.NonUniqueBeanException
import spock.lang.Specification
/**
 * Created by graemerocher on 26/05/2017.
 */
class PrimarySpec extends Specification {

    void "test the @Primary annotation influences bean selection"() {

        given:
        BeanContext context = BeanContext.run()

        when:"A bean has a dependency on an interface with multiple impls"
        B b = context.getBean(B)

        then:"The impl marked with @Primary is selected"
        b.all.size() == 2
        b.all.any() { it instanceof A1 }
        b.all.any() { it instanceof A2 }
        b.a instanceof A2
    }

    void "test duplicate @Primary bean throws exception"() {
        given:
        BeanContext context = BeanContext.run()

        when:
        C c = context.getBean(C)

        then:
        thrown(NonUniqueBeanException)
    }
}
