package io.micronaut.multitenancy.tenantresolver

import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpRequest
import io.micronaut.multitenancy.exceptions.TenantNotFoundException
import spock.lang.Specification

class HttpHeaderTenantResolverSpec extends Specification {

    void "Test not tenant id found"() {
        setup:
        def request = Mock(HttpRequest)

        when:
        new HttpHeaderTenantResolver(null).resolveTenantIdentifierAtRequest(request)

        then:
        def e = thrown(TenantNotFoundException)
        e.message == "Tenant could not be resolved from HTTP Header: ${HttpHeaderTenantResolverConfiguration.DEFAULT_HEADER_NAME}"
        1 * request.getHeaders()
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
