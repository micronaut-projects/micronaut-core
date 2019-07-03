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
import io.micronaut.context.exceptions.NoSuchBeanException
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class MultipleTenantResolversDisabledSpec extends Specification {
    static final SPEC_NAME_PROPERTY = 'spec.name'

    @Shared
    @AutoCleanup
    ApplicationContext context = ApplicationContext.run(
        [
            'micronaut.multitenancy.tenantresolver.cookie.enabled'        : false,
            'micronaut.multitenancy.tenantresolver.fixed.enabled'         : false,
            'micronaut.multitenancy.tenantresolver.httpheader.enabled'    : false,
            'micronaut.multitenancy.tenantresolver.principal.enabled'     : false,
            'micronaut.multitenancy.tenantresolver.session.enabled'       : false,
            'micronaut.multitenancy.tenantresolver.subdomain.enabled'     : false,
            'micronaut.multitenancy.tenantresolver.systemproperty.enabled': false,
            (SPEC_NAME_PROPERTY)                                          : getClass().simpleName
        ], Environment.TEST)

    void 'TenantResolvers should not be enabled if they are disabled'() {
        when: 'trying to get a tenantResolver bean'
        context.getBean(TenantResolver)

        then: 'no bean is retrieved because all of them are disabled'
        thrown(NoSuchBeanException)
    }
}
