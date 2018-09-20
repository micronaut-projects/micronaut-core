package io.micronaut.multitenancy.tenantresolver

import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.Environment
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class PrincipalTenantResolverEnabledSpec extends Specification {
    static final SPEC_NAME_PROPERTY = 'spec.name'

    @Shared
    @AutoCleanup ApplicationContext context = ApplicationContext.run([
            'micronaut.multitenancy.tenantresolver.principal.enabled': true,
            (SPEC_NAME_PROPERTY):getClass().simpleName
    ], Environment.TEST)

    void "TenantResolver is enabled if micronaut.multitenancy.tenantresolver.principal.enabled = true"() {
        when:
        context.getBean(TenantResolver)

        then:
        noExceptionThrown()
    }
}
