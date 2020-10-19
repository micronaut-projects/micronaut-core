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
package io.micronaut.multitenancy.tenantresolver

import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.Environment
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class SessionTenantResolverEnabledSpec extends Specification {
    static final SPEC_NAME_PROPERTY = 'spec.name'

    @Shared
    @AutoCleanup ApplicationContext context = ApplicationContext.run([
            'micronaut.multitenancy.tenantresolver.session.enabled': true,
            (SPEC_NAME_PROPERTY):getClass().simpleName
    ], Environment.TEST)

    void "TenantResolver is enabled if micronaut.multitenancy.tenantresolver.session.enabled = true"() {
        when:
        context.getBean(TenantResolver)

        then:
        noExceptionThrown()

        and:
        context.containsBean(HttpRequestTenantResolver)
        context.getBean(HttpRequestTenantResolver) instanceof SessionTenantResolver
    }
}
