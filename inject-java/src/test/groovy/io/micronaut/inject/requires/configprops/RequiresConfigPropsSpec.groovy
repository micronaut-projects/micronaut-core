package io.micronaut.inject.requires.configprops

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.context.exceptions.NoSuchBeanException

class RequiresConfigPropsSpec extends AbstractTypeElementSpec {

    void "test requires configuration property presence"() {
        when:
        ApplicationContext context = ApplicationContext
                .builder('outer.outer-property': 'anyValue')
                .build();

        context.start()
        context.getBean(A.class)

        then:
        noExceptionThrown();

        cleanup:
        context.close()
    }

    void "test configuration property absence"() {
        when:
        ApplicationContext context = ApplicationContext
                .builder()
                .build();

        context.start()
        context.getBean(A.class)

        then:
        thrown(NoSuchBeanException.class)

        cleanup:
        context.close()
    }

    void "test requires inner configuration property presence"() {
        when:
        ApplicationContext context = ApplicationContext
                .builder('outer.inner.inner-property': 'anyValue')
                .build()

        context.start()
        context.getBean(B.class)

        then:
        noExceptionThrown();

        cleanup:
        context.close()
    }

    void "test requires inherited configuration property presence"() {
        when:
        ApplicationContext context = ApplicationContext
                .builder('outer.inherited.inherited-property': 'anyValue')
                .build()

        context.start()
        context.getBean(C.class)

        then:
        noExceptionThrown();

        cleanup:
        context.close()
    }

    void "test requires configuration property value"() {
        when:
        ApplicationContext context = ApplicationContext
                .builder('outer.outer-property': 'true')
                .build();

        context.start()
        context.getBean(D.class)

        then:
        noExceptionThrown()

        cleanup:
        context.close()
    }

    void "test requires different types properties"() {
        when:
        ApplicationContext context = ApplicationContext
                .builder(
                        'type.int-property': '1',
                        'type.bool-property': 'true',
                        'type.string-property': 'test'
                )
                .build();

        context.start()
        context.getBean(E.class)

        then:
        noExceptionThrown()

        cleanup:
        context.close()
    }
}
