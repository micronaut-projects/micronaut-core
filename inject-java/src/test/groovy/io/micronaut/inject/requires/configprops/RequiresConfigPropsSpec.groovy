package io.micronaut.inject.requires.configprops

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContext

class RequiresConfigPropsSpec extends AbstractTypeElementSpec {

    void "test requires configuration property presence"() {
        given:
        ApplicationContext context = ApplicationContext
                .builder('outer.outer-property': 'anyValue')
                .build();

        context.start()

        expect:
        context.containsBean(A.class)

        cleanup:
        context.close()
    }

    void "test configuration property absence"() {
        given:
        ApplicationContext context = ApplicationContext
                .builder()
                .build();

        context.start()

        expect:
        !context.containsBean(A.class)

        cleanup:
        context.close()
    }

    void "test requires inner configuration property presence"() {
        given:
        ApplicationContext context = ApplicationContext
                .builder('outer.inner.inner-property': 'anyValue')
                .build()

        context.start()

        expect:
        context.containsBean(B.class)

        cleanup:
        context.close()
    }

    void "test requires inherited configuration property presence"() {
        given:
        ApplicationContext context = ApplicationContext
                .builder('outer.inherited.inherited-property': 'anyValue')
                .build()

        context.start()

        expect:
        context.containsBean(C.class)

        cleanup:
        context.close()
    }

    void "test requires configuration property value"() {
        given:
        ApplicationContext context = ApplicationContext
                .builder('outer.outer-property': 'true')
                .build();

        context.start()

        expect:
        context.containsBean(D.class)

        cleanup:
        context.close()
    }

    void "test requires configuration not equals value"() {
        given:
        ApplicationContext context = ApplicationContext
                .builder('outer.outer-property': 'disabled')
                .build();

        context.start()

        expect:
        context.containsBean(E.class)

        cleanup:
        context.close()
    }

    void "test requires different types properties"() {
        given:
        ApplicationContext context = ApplicationContext
                .builder(
                        'type.int-property': '1',
                        'type.bool-property': 'true',
                        'type.string-property': 'test'
                )
                .build();

        context.start()

        expect:
        context.containsBean(F.class)

        cleanup:
        context.close()
    }

    void "test not configuration properties class"() {
        given:
        ApplicationContext context = ApplicationContext
                .builder()
                .build();

        context.start()

        expect:
        !context.containsBean(G.class)

        cleanup:
        context.close()
    }
}
