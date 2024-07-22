package io.micronaut.inject.foreach.generic

import io.micronaut.context.ApplicationContext
import spock.lang.Specification

class EachBeanGenericSpec extends Specification {

    void "test each bean also delegates generics 1"() {
        given:
            ApplicationContext context = ApplicationContext.run([
                    'spec': 'EachBeanGenericSpec'
            ])
        when:
            def registrations = context.getBeanRegistrations(CoreReader1)
        then:
            registrations.size() == 1
            registrations[0].definition().getTypeArguments(CoreReader1).size() == 1
            registrations[0].definition().getTypeArguments(CoreReader1)[0].type == String
        cleanup:
            context.close()
    }

    void "test each bean also delegates generics 2"() {
        given:
            ApplicationContext context = ApplicationContext.run([
                    'spec': 'EachBeanGenericSpec'
            ])
        when:
            def registrations = context.getBeanRegistrations(CoreReader2)
        then:
            registrations.size() == 1
            registrations[0].definition().getTypeArguments(CoreReader2).size() == 1
            registrations[0].definition().getTypeArguments(CoreReader2)[0].type == String
        cleanup:
            context.close()
    }
}
