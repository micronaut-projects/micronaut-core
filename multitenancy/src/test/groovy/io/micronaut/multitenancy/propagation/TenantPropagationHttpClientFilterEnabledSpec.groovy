/*
 * Copyright 2017-2019 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
