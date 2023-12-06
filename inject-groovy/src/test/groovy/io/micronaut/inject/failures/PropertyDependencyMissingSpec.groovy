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
package io.micronaut.inject.failures

import io.micronaut.context.ApplicationContext
import io.micronaut.context.exceptions.DependencyInjectionException
import jakarta.inject.Inject
import spock.lang.Specification

/**
 * Created by graemerocher on 12/05/2017.
 */
class PropertyDependencyMissingSpec  extends Specification {

    void "test a useful exception is thrown when a dependency injection failure occurs"() {
        given:
        ApplicationContext context = ApplicationContext.run()

        when:"A bean is obtained that has a setter with @Inject"
        context.getBean(B)

        then:"The correct error is thrown"
        DependencyInjectionException e = thrown()
        def lines = e.message.lines().toList()
        lines[0] == 'Failed to inject value for parameter [a] of method [setA] of class: io.micronaut.inject.failures.PropertyDependencyMissingSpec$B'
        lines[1] == 'Message: No bean of type [io.micronaut.inject.failures.PropertyDependencyMissingSpec$A] exists. '
        lines[2] == 'Path Taken: new B() --> B.setA([A a])'

        cleanup:
        context.close()
    }

    static interface A {

    }

    static class B {
        @Inject
        A a
    }

}
