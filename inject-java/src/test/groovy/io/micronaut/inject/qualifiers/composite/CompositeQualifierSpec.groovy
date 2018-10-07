package io.micronaut.inject.qualifiers.composite

import io.micronaut.context.DefaultBeanContext
import io.micronaut.context.Qualifier
import io.micronaut.inject.qualifiers.Qualifiers
import spock.lang.Specification

class CompositeQualifierSpec extends Specification {

    void 'test using a composite qualifier'() {
        given:
        DefaultBeanContext context = new DefaultBeanContext()
        context.start()

        when:
        Qualifier qualifier = Qualifiers.byQualifiers(Qualifiers.byType(Runnable), Qualifiers.byName('thread'))

        then:
        context.getBeanDefinitions(qualifier).size() == 1

        cleanup:
        context.close()
    }

}

