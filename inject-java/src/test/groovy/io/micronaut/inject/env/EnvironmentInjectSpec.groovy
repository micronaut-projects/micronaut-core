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
package io.micronaut.inject.env

import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.Environment
import spock.lang.Specification

class EnvironmentInjectSpec extends Specification {

    void "test inject the environment object"() {
        given:
        ApplicationContext applicationContext = ApplicationContext.run("foo")

        when:
        A a = applicationContext.getBean(A)

        then:
        a.environment != null
        a.environment.activeNames.contains("foo")
        a.environment.activeNames.contains(Environment.TEST)
        a.defaultEnvironment != null
        a.defaultEnvironment.activeNames.contains("foo")
    }
}