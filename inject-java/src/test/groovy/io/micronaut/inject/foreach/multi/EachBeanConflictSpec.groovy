package io.micronaut.inject.foreach.multi

import io.micronaut.context.ApplicationContext
import io.micronaut.context.exceptions.NonUniqueBeanException
import io.micronaut.inject.qualifiers.Qualifiers
import spock.lang.Specification

class EachBeanConflictSpec extends Specification {

    void "test multiple each beans"() {
        given:
            ApplicationContext context = ApplicationContext.run([
                    "my.multi.beans.first.name" : "first",
                    "my.multi.beans.second.name": "second"
            ])

        when:
            context.getBean(MyService)
        then:
            thrown(NonUniqueBeanException)
        and:
            context.getBeansOfType(MyService, Qualifiers.byName("first")).size() == 2
            context.getBeansOfType(MyService, Qualifiers.byName("second")).size() == 2

        when:
            context.getBean(MyService, Qualifiers.byName("first"))
        then:
            thrown(NonUniqueBeanException)

        when:
            context.getBean(MyService, Qualifiers.byName("second"))
        then:
            thrown(NonUniqueBeanException)

        when:
            context.getBean(Foo, Qualifiers.byName("first"))
            context.getBean(Foo, Qualifiers.byName("second"))
            context.getBean(Bar, Qualifiers.byName("first"))
            context.getBean(Bar, Qualifiers.byName("second"))
        then:
            noExceptionThrown()

        cleanup:
            context.close()
    }
}
