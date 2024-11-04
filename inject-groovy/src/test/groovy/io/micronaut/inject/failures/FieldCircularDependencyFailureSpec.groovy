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
import io.micronaut.context.exceptions.CircularDependencyException
import spock.lang.Specification

import javax.inject.Inject
import javax.inject.Singleton
/**
 * Created by graemerocher on 16/05/2017.
 */
class FieldCircularDependencyFailureSpec extends Specification {

    void "test simple field circular dependency failure"() {
        given:
        ApplicationContext context = ApplicationContext.run()

        when:"A bean is obtained that has a setter with @Inject"
        B b =  context.getBean(B)

        then:"The implementation is injected"
        def e = thrown(CircularDependencyException)
        e.message.normalize() == '''\
Failed to inject value for field [a] of class: io.micronaut.inject.failures.FieldCircularDependencyFailureSpec$B

Message: Circular dependency detected
Path Taken:
new B()
      \\---> B.a
            ^  \\---> new A([C c])
            |        \\---> C.b
            |              |
            +--------------+'''
        cleanup:
        context.close()
    }

    static class C {
        @Inject protected B b
    }
    @Singleton
    static class A {
        A(C c) {}
    }

    @Singleton
    static class B {
        @Inject protected A a
    }
}
