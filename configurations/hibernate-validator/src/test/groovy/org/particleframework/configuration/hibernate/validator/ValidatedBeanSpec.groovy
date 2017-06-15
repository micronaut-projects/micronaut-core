package org.particleframework.configuration.hibernate.validator

import org.hibernate.validator.constraints.NotBlank
import org.hibernate.validator.constraints.URL
import org.particleframework.context.ApplicationContext
import org.particleframework.context.DefaultApplicationContext
import org.particleframework.context.annotation.Value
import org.particleframework.context.exceptions.BeanInstantiationException
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
Error instantiating bean of type  [org.particleframework.configuration.hibernate.validator.ValidatedBeanSpec$A]

Message: Validation failed for bean definition [org.particleframework.configuration.hibernate.validator.ValidatedBeanSpec$A]
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
