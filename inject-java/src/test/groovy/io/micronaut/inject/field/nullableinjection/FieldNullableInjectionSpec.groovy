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
package io.micronaut.inject.field.nullableinjection

import io.micronaut.context.ApplicationContext
import io.micronaut.context.exceptions.DependencyInjectionException
import spock.lang.Specification

class FieldNullableInjectionSpec extends Specification {

    void "test nullable injection with field"() {
        given:
        ApplicationContext context = ApplicationContext.run()

        when:"A bean is obtained that has a constructor with @Inject"
        B b =  context.getBean(B)

        then:"The implementation is not injected, but null is"
        b.a == null

        cleanup:
        context.close()
    }

    void "test normal injection still fails"() {
        given:
        ApplicationContext context = ApplicationContext.run()

        when:"A bean is obtained that has a constructor with @Inject"
        context.getBean(C)

        then:"The bean is not found"
        thrown(DependencyInjectionException)

        cleanup:
        context.close()
    }

    void "test injecting a nullable provider"() {
        given:
        ApplicationContext context = ApplicationContext.run()

        when:"A bean is obtained that has a constructor with @Inject"
        D d = context.getBean(D)

        then:"The bean is not found"
        noExceptionThrown()
        d.a == null

        cleanup:
        context.close()
    }
}