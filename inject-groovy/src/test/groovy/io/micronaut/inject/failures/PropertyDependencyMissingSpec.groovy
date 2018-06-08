/*
 * Copyright 2017-2018 original authors
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

import io.micronaut.context.BeanContext
import io.micronaut.context.DefaultBeanContext
import io.micronaut.context.exceptions.DependencyInjectionException
import spock.lang.Specification

import javax.inject.Inject

/**
 * Created by graemerocher on 12/05/2017.
 */
class PropertyDependencyMissingSpec  extends Specification {


    void "test a useful exception is thrown when a dependency injection failure occurs"() {
        given:
        BeanContext context = new DefaultBeanContext()
        context.start()

        when:"A bean is obtained that has a setter with @Inject"
        B b =  context.getBean(B)

        then:"The correct error is thrown"
        def e = thrown(DependencyInjectionException)
        e.message == '''\
Failed to inject value for parameter [a] of method [setA] of class: io.micronaut.inject.failures.PropertyDependencyMissingSpec$B

Path Taken: B.setA([A a])'''
    }

    static interface A {

    }

    static class B {
        @Inject
        A a
    }

}