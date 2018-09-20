package io.micronaut.multitenancy.propagation

import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.Environment
import io.micronaut.multitenancy.tenantresolver.TenantResolver
import io.micronaut.multitenancy.propagation.TenantPropagationHttpClientFilter
import io.micronaut.multitenancy.writer.TenantWriter
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class TenantPropagationHttpClientFilterEnabledSpec extends Specification {
    static final SPEC_NAME_PROPERTY = 'spec.name'

    @Shared
    @AutoCleanup ApplicationContext context = ApplicationContext.run([
            'micronaut.multitenancy.tenantresolver.httpheader.enabled': true,
            'micronaut.multitenancy.propagation.enabled': true,
            'micronaut.multitenancy.tenantwriter.httpheader.enabled': true,
            (SPEC_NAME_PROPERTY):getClass().simpleName
    ], Environment.TEST)

    void "TenantPropagationHttpClientFilter is enabled if propagation is enabled and there is a tenant resolver and a tenant writer"() {
        when:
        context.getBean(TenantWriter)

        then:
        noExceptionThrown()

        when:
        context.getBean(TenantResolver)

        then:
        noExceptionThrown()

        when:
        context.getBean(TenantPropagationHttpClientFilter)

        then:
        noExceptionThrown()
    }
}
