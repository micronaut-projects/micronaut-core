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
package io.micronaut.inject.field

import io.micronaut.context.BeanContext
import io.micronaut.context.DefaultBeanContext
import io.micronaut.inject.qualifiers.One
import spock.lang.Specification

import jakarta.inject.Inject
import jakarta.inject.Named
import jakarta.inject.Singleton

/**
 * Created by graemerocher on 15/05/2017.
 */
class FieldProtectedWithQualifierSpec extends Specification {

    void "test that a field with a qualifier is injected correctly"() {
        given:
        BeanContext context = new DefaultBeanContext()
        context.start()

        when:
        B b = context.getBean(B)

        then:
        b.a instanceof OneA
        b.a2 instanceof TwoA
    }

    static class B {
        @Inject @One protected A a
        @Inject @Named('twoA') protected A a2
    }

    static  interface A {

    }

}


@Singleton
class OneA implements FieldProtectedWithQualifierSpec.A {

}
@Singleton
class TwoA implements FieldProtectedWithQualifierSpec.A {

}



