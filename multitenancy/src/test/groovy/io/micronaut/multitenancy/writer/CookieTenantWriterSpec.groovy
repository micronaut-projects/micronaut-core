package io.micronaut.multitenancy.writer

import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.Environment
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class CookieTenantWriterSpec extends Specification {
    static final String SPEC_NAME_PROPERTY = 'spec.name'

    @Shared
    @AutoCleanup
    ApplicationContext context = ApplicationContext.run([
            'micronaut.multitenancy.tenantwriter.cookie.enabled': true,
            (SPEC_NAME_PROPERTY) : getClass().simpleName
    ], Environment.TEST)

    void "TenantResolver is enabled if micronaut.multitenancy.tenantwriter.cookie.enabled = true"() {
        when:
        context.getBean(TenantWriter)

        then:
        noExceptionThrown()
    }
}
