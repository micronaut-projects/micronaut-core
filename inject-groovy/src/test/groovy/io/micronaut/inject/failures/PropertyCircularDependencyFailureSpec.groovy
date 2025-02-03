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
import jakarta.inject.Inject
import jakarta.inject.Singleton
import spock.lang.Specification
/**
 * Created by graemerocher on 16/05/2017.
 */
class PropertyCircularDependencyFailureSpec extends Specification {

    void "test simple property circular dependency failure"() {
        given:
        ApplicationContext context = ApplicationContext.run()

        when:"A bean is obtained that has a setter with @Inject"
        context.getBean(MyClassB)

        then:"The implementation is injected"
        CircularDependencyException e = thrown()
        e.message == '''\
Failed to inject value for parameter [propA] of method [setPropA] of class: io.micronaut.inject.failures.PropertyCircularDependencyFailureSpec$MyClassB

Message: Circular dependency detected
Path Taken:
new @j.i.Singleton i.m.i.f.P$MyClassB()
      \\---> @j.i.Singleton i.m.i.f.P$MyClassB#setPropA([MyClassA propA])
            ^  \\---> @j.i.Singleton i.m.i.f.P$MyClassA#setPropB([MyClassB propB])
            |        |
            +--------+'''

        cleanup:
        context.close()
    }

    @Singleton
    static class MyClassA {
        @Inject MyClassB propB
    }

    @Singleton
    static class MyClassB {
        @Inject MyClassA propA
    }
}
