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

package io.micronaut.validation.validator.pojo

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requires
import io.micronaut.context.env.Environment
import io.micronaut.core.annotation.Introspected
import io.micronaut.validation.validator.Validator
import io.micronaut.validation.validator.constraints.ConstraintValidator
import jakarta.inject.Singleton
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class PojoValidatorSpec extends Specification {

    @Shared
    @AutoCleanup
    ApplicationContext applicationContext = ApplicationContext.run(
        ['spec.name': 'customValidatorPOJO'],
        Environment.TEST
    )

    @Shared
    Validator validator = applicationContext.getBean(Validator)

    void "test custom constraint validator on a Pojo"() {
        given:
        Search search = new Search()

        when:
        def constraintViolations = validator.validate(search)

        then:
        constraintViolations.size() == 1
        constraintViolations.first().message == "Both name and lastName can't be null"
    }
}

@Introspected
@NameAndLastNameValidator
class Search {
    String name
    String lastName
}

@Factory
@Requires(property = "spec.name", value = "customValidatorPOJO")
class NameAndLastNameValidatorFactory {

    @Singleton
    ConstraintValidator<NameAndLastNameValidator, Search> nameAndLastNameValidator() {
        return { value, annotationMetadata, context ->
            Objects.requireNonNull(annotationMetadata)
            Objects.requireNonNull(context)
            value != null && (value.getName() != null || value.getLastName() != null)
        }
    }
}

