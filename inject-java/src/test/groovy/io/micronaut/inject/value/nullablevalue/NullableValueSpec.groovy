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
package io.micronaut.inject.value.nullablevalue

import io.micronaut.context.ApplicationContext
import spock.lang.Specification

class NullableValueSpec extends Specification {

    void "test value with nullable"() {
        given:
        ApplicationContext context = ApplicationContext.run(
                [
                        "spec.name": getClass().simpleName,
                        "exists.x":"fromConfig"
                ], "test"
        )

        when:
        A a = context.getBean(A)

        then:
        a.nullField == null
        a.nonNullField == "fromConfig"
        a.nullConstructorArg == null
        a.nonNullConstructorArg == "fromConfig"
        a.nullMethodArg == null
        a.nonNullMethodArg == "fromConfig"
    }
}
