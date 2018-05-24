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
package io.micronaut.configuration.hibernate.validator

import org.hibernate.validator.constraints.URL
import io.micronaut.context.ApplicationContext
import io.micronaut.context.DefaultApplicationContext
import io.micronaut.context.annotation.Value
import io.micronaut.context.exceptions.BeanInstantiationException
import spock.lang.Specification

import javax.inject.Singleton

/**
 * Created by graemerocher on 15/06/2017.
 */
class ValidatedBeanSpec extends Specification {

    void "test validated bean invalid bean"() {

        given:
        System.setProperty("a.name", "test")
        ApplicationContext applicationContext = new DefaultApplicationContext("test ")
                                                            .start()

        when:
        A a = applicationContext.getBean(A)

        then:
        def e = thrown(BeanInstantiationException)
        e.message == '''\
Error instantiating bean of type  [io.micronaut.configuration.hibernate.validator.ValidatedBeanSpec$A]

Message: Validation failed for bean definition [io.micronaut.configuration.hibernate.validator.ValidatedBeanSpec$A]
List of constraint violations:[
\turl - must be a valid URL
]
'''

    }

    @Singleton
    static class A {
        @URL
        @Value('a.name')
        String url
    }
}
