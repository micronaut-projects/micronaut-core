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
package io.micronaut.inject.failures.postconstruct

import io.micronaut.context.ApplicationContext
import io.micronaut.context.exceptions.BeanInstantiationException
import spock.lang.Specification

class PostConstructExceptionSpec extends Specification {

    void "test error message when a bean has an error in the post construct method"() {
        given:
        ApplicationContext context = ApplicationContext.run(["spec.name": getClass().simpleName])

        when:"A bean is obtained that has a setter with @Inject"
        context.getBean(MyClassB)

        then:"The implementation is injected"
        def e = thrown(BeanInstantiationException)
        e.message.normalize() == '''\
Error instantiating bean of type  [io.micronaut.inject.failures.postconstruct.MyClassB]

Message: bad
Path Taken:
new @j.i.Singleton i.m.i.f.p.MyClassB()'''

        cleanup:
        context.close()
    }
}
