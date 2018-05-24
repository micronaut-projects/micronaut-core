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
import io.micronaut.context.DefaultApplicationContext
import io.micronaut.context.env.DefaultEnvironment
import io.micronaut.context.env.Environment
import spock.lang.Specification

import javax.inject.Inject

/**
 * Created by graemerocher on 12/06/2017.
 */
class EnvironmentInjectSpec extends Specification {

    void "test inject the environment object"() {
        given:
        ApplicationContext applicationContext = new DefaultApplicationContext("test")
                                                        .start()

        when:
        A a = applicationContext.getBean(A)

        then:
        a.environment != null
        a.defaultEnvironment != null
    }

    static class A {
        @Inject Environment environment

        @Inject DefaultEnvironment defaultEnvironment
    }
}
