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
class ConstructorDependencyFailureSpec extends Specification {


    void "test a useful exception is thrown when a dependency injection failure occurs"() {
        given:
        ApplicationContext context = ApplicationContext.run()
        var space = " "

        when:"A bean that defines a constructor dependency on a missing bean"
        context.getBean(MyClassB)

        then:"The correct error is thrown"
        def e = thrown(DependencyInjectionException)
        e.message.normalize() == """\
Failed to inject value for parameter [propA] of class: io.micronaut.inject.failures.ConstructorDependencyFailureSpec\$MyClassB

Message: No bean of type [io.micronaut.inject.failures.ConstructorDependencyFailureSpec\$MyClassA] exists.$space
Path Taken:$space
new i.m.i.f.C\$MyClassB(MyClassA propA)
\\---> new i.m.i.f.C\$MyClassB([MyClassA propA])"""

        cleanup:
        context.close()
    }

    static interface MyClassA {

    }

    static class MyClassB {
        private final MyClassA propA

        @Inject
        MyClassB(MyClassA propA) {
            this.propA = propA
        }
    }

}
