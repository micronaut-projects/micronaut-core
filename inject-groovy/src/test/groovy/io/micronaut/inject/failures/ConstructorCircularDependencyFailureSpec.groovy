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
class ConstructorCircularDependencyFailureSpec extends Specification {

    void "test simple constructor circular dependency failure"() {
        given:
        ApplicationContext context = ApplicationContext.run()

        when:"A bean is obtained that has a setter with @Inject"
        context.getBean(MyClassB)

        then:"The implementation is injected"
        def e = thrown(CircularDependencyException)
        e.message.normalize() == '''\
Failed to inject value for field [propA] of class: io.micronaut.inject.failures.ConstructorCircularDependencyFailureSpec$MyClassB

Message: Circular dependency detected
Path Taken:
new @j.i.Singleton i.m.i.f.C$MyClassB()
      \\---> @j.i.Singleton i.m.i.f.C$MyClassB#propA
            ^  \\---> new @j.i.Singleton i.m.i.f.C$MyClassA([MyClassC propC])
            |        \\---> new i.m.i.f.C$MyClassC([MyClassB propB])
            |              |
            +--------------+'''

        cleanup:
        context.close()
    }

    void "test another constructor circular dependency failure"() {
        given:
        ApplicationContext context = ApplicationContext.run(["spec.name": getClass().simpleName])

        when:"A bean is obtained that has a setter with @Inject"
        context.getBean(MyClassD)

        then:"The implementation is injected"
        def e = thrown(CircularDependencyException)
        e.message.normalize() == '''\
Failed to inject value for field [propA] of class: io.micronaut.inject.failures.ConstructorCircularDependencyFailureSpec$MyClassB

Message: Circular dependency detected
Path Taken:
new @j.i.Singleton i.m.i.f.C$MyClassD(MyClassB propB)
      \\---> new @j.i.Singleton i.m.i.f.C$MyClassD([MyClassB propB])
            \\---> @j.i.Singleton i.m.i.f.C$MyClassB#propA
                  ^  \\---> new @j.i.Singleton i.m.i.f.C$MyClassA([MyClassC propC])
                  |        \\---> new i.m.i.f.C$MyClassC([MyClassB propB])
                  |              |
                  +--------------+'''
    }

    static class MyClassC {
        @Inject
        MyClassC(MyClassB propB) {}
    }
    @Singleton
    static class MyClassA {
        MyClassA(MyClassC propC) {}
    }

    @Singleton
    static class MyClassB {
        @Inject protected MyClassA propA
    }

    @Singleton
    static class MyClassD {
        MyClassD(MyClassB propB) {}
    }
}

