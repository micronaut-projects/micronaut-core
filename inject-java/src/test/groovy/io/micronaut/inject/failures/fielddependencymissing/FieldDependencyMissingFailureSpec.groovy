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
package io.micronaut.inject.failures.fielddependencymissing

import io.micronaut.context.BeanContext
import io.micronaut.context.DefaultBeanContext
import io.micronaut.context.exceptions.DependencyInjectionException
import spock.lang.Specification

class FieldDependencyMissingFailureSpec extends Specification {


    void "test injection via setter with interface"() {
        given:
        BeanContext context = new DefaultBeanContext()
        context.start()

        when:"A bean is obtained that has a setter with @Inject"
        B b =  context.getBean(B)

        then:"The implementation is injected"
        def e = thrown(DependencyInjectionException)
        e.message.normalize() == '''\
Failed to inject value for field [a] of class: io.micronaut.inject.failures.fielddependencymissing.B

Path Taken: B.a'''

        cleanup:
        context.close()
    }
}

