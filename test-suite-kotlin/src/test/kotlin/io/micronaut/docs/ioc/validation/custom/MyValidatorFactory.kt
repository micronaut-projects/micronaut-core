/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.docs.ioc.validation.custom

// tag::imports[]
import io.micronaut.context.annotation.Factory
import io.micronaut.validation.validator.constraints.ConstraintValidator
import javax.inject.Singleton
// end::imports[]

// tag::class[]
@Factory
class MyValidatorFactory {

    @Singleton
    fun durationPatternValidator() : ConstraintValidator<DurationPattern, CharSequence> {
        return ConstraintValidator { value, annotation, context ->
            value == null || value.toString().matches("^PT?[\\d]+[SMHD]{1}$".toRegex())
        }
    }
}
// end::class[]
