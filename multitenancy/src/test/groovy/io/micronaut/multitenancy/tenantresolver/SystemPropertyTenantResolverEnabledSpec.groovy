package io.micronaut.multitenancy.tenantresolver

import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.Environment
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.util.environment.RestoreSystemProperties

class SystemPropertyTenantResolverEnabledSpec extends Specification {
    static final SPEC_NAME_PROPERTY = 'spec.name'

    @Shared
    @AutoCleanup ApplicationContext context = ApplicationContext.run([
            'micronaut.multitenancy.tenantresolver.systemproperty.enabled': true,
            (SPEC_NAME_PROPERTY):getClass().simpleName
    ], Environment.TEST)

    void "TenantResolver is enabled if micronaut.multitenancy.tenantresolver.systemproperty.enabled = true"() {
        when:
        context.getBean(TenantResolver)

        then:
        noExceptionThrown()
    }

    @RestoreSystemProperties
    void "SystemPropertyTenantResolver resolves a Java system property"() {
        given:
        System.properties['tenantId'] = 'green'

        expect:
        'green' == context.getBean(TenantResolver).resolveTenantIdentifier()
    }
}
