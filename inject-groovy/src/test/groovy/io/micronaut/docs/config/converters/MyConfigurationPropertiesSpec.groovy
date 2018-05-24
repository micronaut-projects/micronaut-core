/*
 * Copyright 2017-2018 original authors
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
package io.micronaut.docs.config.converters

import io.micronaut.context.ApplicationContext
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import java.time.LocalDate

/**
 * @author Sergio del Amo
 * @since 1.0
 */
//tag::bookSpec[]
class MyConfigurationPropertiesSpec extends Specification {

    @AutoCleanup
    @Shared
    //tag::runContext[]
    ApplicationContext ctx = ApplicationContext.run(
            "myapp.updatedAt": [day: 28, month: 10, year: 1982]  // <1>
    )
    //end::runContext[]


    void "test convert date from map"() {
        when:
        MyConfigurationProperties props = ctx.getBean(MyConfigurationProperties)

        then:
        props.updatedAt == LocalDate.of(1982, 10, 28)
    }
}
//end::bookSpec[]
