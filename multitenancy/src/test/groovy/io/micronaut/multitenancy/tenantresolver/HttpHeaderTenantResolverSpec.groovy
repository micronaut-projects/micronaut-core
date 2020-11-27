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

import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpRequest
import io.micronaut.multitenancy.exceptions.TenantNotFoundException
import spock.lang.Specification

class HttpHeaderTenantResolverSpec extends Specification {

    void "Test not tenant id found"() {
        setup:
        def request = Stub(HttpRequest) {
            getHeaders() >> new MockHttpHeaders([:])
        }

        when:
        new HttpHeaderTenantResolver(null).resolveTenantIdentifierAtRequest(request)

        then:
        def e = thrown(TenantNotFoundException)
        e.message == "Tenant could not be resolved. Header ${HttpHeaderTenantResolverConfiguration.DEFAULT_HEADER_NAME} value is null"
    }

    void "Test HttpHeader value is the tenant id when a request is present"() {

        setup:
        def httpHeaders = Stub(HttpHeaders) {
            get(HttpHeaderTenantResolverConfiguration.DEFAULT_HEADER_NAME) >> "foo"
        }
        def request = Stub(HttpRequest) {
            getHeaders() >> httpHeaders
        }

        when:
        def tenantId = new HttpHeaderTenantResolver(new HttpHeaderTenantResolverConfigurationProperties()).resolveTenantIdentifierAtRequest(request)

        then:
        tenantId == "foo"
    }

}
