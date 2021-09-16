package io.micronaut.inject.requires.configproperties

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContext

class RequiresConfigPropertiesSpec extends AbstractTypeElementSpec {

    void "test requires configuration properties method returning true"() {
        given:
        ApplicationContext context = ApplicationContext
                .builder('outer.enabled': 'true')
                .build();

        context.start()

        expect:
        context.containsBean(A.class)

        cleanup:
        context.close()
    }

    void "test requires configuration properties method returning false"() {
        given:
        ApplicationContext context = ApplicationContext
                .builder('outer.enabled': 'false')
                .build();

        context.start()

        expect:
        !context.containsBean(A.class)

        cleanup:
        context.close()
    }

    void "test requires inner configuration properties method returning true"() {
        given:
        ApplicationContext context = ApplicationContext
                .builder('outer.inner.enabled': 'true')
                .build();

        context.start()

        expect:
        context.containsBean(B.class)

        cleanup:
        context.close()
    }


}
