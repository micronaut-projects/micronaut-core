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
package io.micronaut.inject.method

import io.micronaut.context.ApplicationContext
import io.micronaut.inject.qualifiers.One
import jakarta.inject.Inject
import jakarta.inject.Named
import jakarta.inject.Singleton
import spock.lang.Specification
/**
 * Created by graemerocher on 15/05/2017.
 */
class SetterWithQualifierSpec extends Specification {

    void "test that a property with a qualifier is injected correctly"() {
        given:
        ApplicationContext context = ApplicationContext.run()

        when:
        B b = context.getBean(B)

        then:
        b.a instanceof OneA
        b.a2 instanceof TwoA

        cleanup:
        context.close()
    }

    static class B {
        private A a
        private A a2
        @Inject setA(@One A a) {
            this.a = a
        }
        @Inject setAnother(@Named('twoA') A a2) {
            this.a2 = a2
        }

        A getA() {
            return a
        }

        A getA2() {
            return a2
        }
    }

    static  interface A {

    }

}


@Singleton
class OneA implements SetterWithQualifierSpec.A {

}
@Singleton
class TwoA implements SetterWithQualifierSpec.A {

}




