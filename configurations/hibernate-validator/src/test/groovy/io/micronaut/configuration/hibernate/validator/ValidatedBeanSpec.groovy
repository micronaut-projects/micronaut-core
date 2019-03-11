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
package io.micronaut.configuration.hibernate.validator

import io.micronaut.context.ApplicationContext
import io.micronaut.context.DefaultApplicationContext
import io.micronaut.context.annotation.Value
import io.micronaut.context.exceptions.BeanInstantiationException
import org.hibernate.validator.constraints.URL
import spock.lang.Specification

import javax.inject.Singleton

/**
 * Created by graemerocher on 15/06/2017.
 */
class ValidatedBeanSpec extends Specification {

    void "test validated bean invalid bean"() {
        given:
        System.setProperty("a.url", "test")
        System.setProperty("a.number", "10")
        System.setProperty("a.nobean", "abc")
        ApplicationContext applicationContext = new DefaultApplicationContext("test ")
                                                            .start()

        when:
        A a = applicationContext.getBean(A)

        then:
        def e = thrown(BeanInstantiationException)
        e.message.normalize() == '''\
Error instantiating bean of type  [io.micronaut.configuration.hibernate.validator.ValidatedBeanSpec$A]

Message: Validation failed for bean definition [io.micronaut.configuration.hibernate.validator.ValidatedBeanSpec$A]
List of constraint violations:[
\turl - must be a valid URL
]
'''
    }

    void "test validated bean invalid bean custom validator"() {

        given:
        System.setProperty("a.url", "http://www.google.com")
        System.setProperty("a.number", "3")
        System.setProperty("a.nobean", "abc")
        ApplicationContext applicationContext = new DefaultApplicationContext("test ")
                .start()

        when:
        A a = applicationContext.getBean(A)

        then:
        def e = thrown(BeanInstantiationException)
        e.message.normalize() == '''\
Error instantiating bean of type  [io.micronaut.configuration.hibernate.validator.ValidatedBeanSpec$A]

Message: Validation failed for bean definition [io.micronaut.configuration.hibernate.validator.ValidatedBeanSpec$A]
List of constraint violations:[
\tnumber - Must be a big number
]
'''
    }

    void "test validated bean invalid bean custom validator that isnt a bean"() {

        given:
        System.setProperty("a.url", "http://www.google.com")
        System.setProperty("a.number", "10")
        System.setProperty("a.nobean", "")
        ApplicationContext applicationContext = new DefaultApplicationContext("test ")
                .start()

        when:
        A a = applicationContext.getBean(A)

        then:
        def e = thrown(BeanInstantiationException)
        e.message.normalize() == '''\
Error instantiating bean of type  [io.micronaut.configuration.hibernate.validator.ValidatedBeanSpec$A]

Message: Validation failed for bean definition [io.micronaut.configuration.hibernate.validator.ValidatedBeanSpec$A]
List of constraint violations:[
\tnoBean - The class isn't a bean
]
'''
    }

    @Singleton
    static class A {
        @URL
        @Value('${a.url}')
        String url

        @BigNumber
        @Value('${a.number}')
        Integer number

        @NoBean
        @Value('${a.nobean}')
        String noBean
    }

}
