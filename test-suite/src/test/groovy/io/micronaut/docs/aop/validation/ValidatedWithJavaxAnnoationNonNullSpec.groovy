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
package io.micronaut.docs.aop.validation

import edu.umd.cs.findbugs.annotations.NonNull
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.context.env.Environment
import io.micronaut.validation.Validated
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import javax.inject.Singleton
import javax.validation.ConstraintViolationException
import javax.validation.constraints.NotNull

class ValidatedWithJavaxAnnoationNonNullSpec extends Specification {

    @Shared
    @AutoCleanup
    ApplicationContext applicationContext = ApplicationContext.run([
            'spec.name': 'nonnull'
    ], Environment.TEST)

    void "javax.annotation.NonNull does not fails validation"() {
        given:
        NonNullService nonNullService = applicationContext.getBean(NonNullService)

        when:"An invalid title is passed"
        String salutation = nonNullService.sayHello(null)

        then:
        noExceptionThrown()
        salutation == 'Hello'

        when:
        NonNullAndNotNullService nonNullNotNullService = applicationContext.getBean(NonNullAndNotNullService)
        nonNullNotNullService.sayHello(null)

        then:"A constraint violation occurred"
        def e = thrown(ConstraintViolationException)
        e.message == 'sayHello.name: must not be null'
    }

    @Requires(property = 'spec.name', value = 'nonnull')
    @Singleton
    @Validated
    static class NonNullService {

        String sayHello(@NonNull String name) {
            name ? "Hello $name" : "Hello"
        }
    }

    @Requires(property = 'spec.name', value = 'nonnull')
    @Singleton
    @Validated
    static class NonNullAndNotNullService {

        String sayHello(@NotNull @NonNull String name) {
            name ? "Hello $name" : "Hello"
        }
    }
}

