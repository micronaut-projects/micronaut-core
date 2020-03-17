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
package io.micronaut.inject.failures.ctorcirculardependency

import io.micronaut.context.ApplicationContext
import io.micronaut.context.BeanContext
import io.micronaut.context.DefaultBeanContext
import io.micronaut.context.annotation.Property
import io.micronaut.context.exceptions.CircularDependencyException
import spock.lang.Specification

import javax.inject.Singleton

class ConstructorCircularDependencyFailureSpec extends Specification {

    void "test simple constructor circular dependency failure"() {
        given:
        BeanContext context = new DefaultBeanContext()
        context.start()

        when:"A bean is obtained that has a setter with @Inject"
        B b =  context.getBean(B)

        then:"The implementation is injected"
        def e = thrown(CircularDependencyException)
        e.message.normalize() == '''\
Failed to inject value for field [a] of class: io.micronaut.inject.failures.ctorcirculardependency.B

Message: Circular dependency detected
Path Taken: 
B.a --> new A([C c]) --> new C([B b])
^                                  |
|                                  |
|                                  |
+----------------------------------+'''
    }

    void "test multiple optionals do not cause a circular dependency exception"() {
        ApplicationContext ctx = ApplicationContext.run()

        when:
        ctx.createBean(ParameterizedBean, "foo")

        then:
        noExceptionThrown()
    }

}

