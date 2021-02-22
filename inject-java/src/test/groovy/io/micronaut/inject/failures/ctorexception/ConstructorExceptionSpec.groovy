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
package io.micronaut.inject.failures.ctorexception

import io.micronaut.context.ApplicationContext
import io.micronaut.context.exceptions.BeanInstantiationException
import spock.lang.Specification

class ConstructorExceptionSpec extends Specification {

    void "test error message when exception occurs in constructor"() {
        given:
        ApplicationContext context = ApplicationContext.run(["spec.name": getClass().simpleName])

        when:"A bean is obtained that has a setter with @Inject"
        B b =  context.getBean(B)

        then:"The implementation is injected"
        def e = thrown(BeanInstantiationException)
        //e.cause.message == 'bad'
        e.message.normalize() == '''\
Error instantiating bean of type  [io.micronaut.inject.failures.ctorexception.A]

Message: bad
Path Taken: B.a --> new A([C c])'''

        cleanup:
        context.close()
    }

}
