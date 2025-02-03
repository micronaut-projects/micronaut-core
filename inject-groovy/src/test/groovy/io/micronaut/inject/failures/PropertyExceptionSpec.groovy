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
import io.micronaut.context.exceptions.BeanInstantiationException
import jakarta.inject.Inject
import jakarta.inject.Singleton
import spock.lang.Specification
/**
 * Created by graemerocher on 17/05/2017.
 */
class PropertyExceptionSpec extends Specification {


    void "test error message when exception occurs setting a property"() {
        given:
        ApplicationContext context = ApplicationContext.run()

        when:"A bean is obtained that has a setter with @Inject"
        context.getBean(MyClassB)

        then:"The implementation is injected"
        BeanInstantiationException e = thrown()
        e.cause.message == 'bad'
        e.message.normalize() == '''\
Error instantiating bean of type  [io.micronaut.inject.failures.PropertyExceptionSpec$MyClassB]

Message: bad
Path Taken:
new i.m.i.f.P$MyClassB()
\\---> i.m.i.f.P$MyClassB#propA'''

        cleanup:
        context.close()
    }

    @Singleton
    static class MyClassC {
    }
    @Singleton
    static class MyClassA {
        @Inject
        void setC(MyClassC propC) {
            throw new RuntimeException("bad")
        }
    }

    static class MyClassB {
        @Inject
        private MyClassA propA

        MyClassA getPropA() {
            return this.propA
        }
    }

}
