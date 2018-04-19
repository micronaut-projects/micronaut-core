package io.micronaut.security.filters

import io.micronaut.http.HttpMethod
import io.micronaut.security.config.InterceptUrlMapPattern
import io.micronaut.security.endpoints.SecurityEndpointsConfiguration
import spock.lang.Specification

class SecurityControllersEndpointAccessProviderSpec extends Specification {

    def "if both login and refresh endpoints are enabled, two whitelisted InterceptUrlMapPattern are returned"() {
        given:
        SecurityEndpointsConfiguration securityEndpointsConfiguration = Stub(SecurityEndpointsConfiguration) {
            isLogin() >> true
            isRefresh() >> true
        }

        when:
        SecurityControllersEndpointAccessProvider provider = new SecurityControllersEndpointAccessProvider(securityEndpointsConfiguration)
        List<InterceptUrlMapPattern> results = provider.findEndpointAccessRestrictions().get()

        then:
        results
        results.size() == 2
        results*.pattern.contains("/login".toString())
        results.find { it.pattern == "/login".toString() }.httpMethod == HttpMethod.POST
        results.find { it.pattern == "/login".toString() }.access == [InterceptUrlMapPattern.TOKEN_IS_AUTHENTICATED_ANONYMOUSLY]

        results*.pattern.contains("/oauth/access_token".toString())
        results.find { it.pattern == "/oauth/access_token".toString() }.httpMethod == HttpMethod.POST
        results.find { it.pattern == "/oauth/access_token".toString() }.access == [InterceptUrlMapPattern.TOKEN_IS_AUTHENTICATED_ANONYMOUSLY]
    }

    def "if only login is enabled, one whitelisted InterceptUrlMapPattern are returned"() {
        given:
        SecurityEndpointsConfiguration securityEndpointsConfiguration = Stub(SecurityEndpointsConfiguration) {
            isLogin() >> true
            isRefresh() >> false
        }

        when:
        SecurityControllersEndpointAccessProvider provider = new SecurityControllersEndpointAccessProvider(securityEndpointsConfiguration)
        List<InterceptUrlMapPattern> results = provider.findEndpointAccessRestrictions().get()

        then:
        results
        results.size() == 1

        results*.pattern.contains("/login".toString())
        results.find { it.pattern == "/login".toString() }.httpMethod == HttpMethod.POST
        results.find { it.pattern == "/login".toString() }.access == [InterceptUrlMapPattern.TOKEN_IS_AUTHENTICATED_ANONYMOUSLY]
    }


    def "if only refresh is enabled, one whitelisted InterceptUrlMapPattern are returned"() {
        given:
        SecurityEndpointsConfiguration securityEndpointsConfiguration = Stub(SecurityEndpointsConfiguration) {
            isLogin() >> false
            isRefresh() >> true
        }

        when:
        SecurityControllersEndpointAccessProvider provider = new SecurityControllersEndpointAccessProvider(securityEndpointsConfiguration)
        List<InterceptUrlMapPattern> results = provider.findEndpointAccessRestrictions().get()

        then:
        results
        results.size() == 1

        results*.pattern.contains("/oauth/access_token".toString())
        results.find { it.pattern == "/oauth/access_token".toString() }.httpMethod == HttpMethod.POST
        results.find { it.pattern == "/oauth/access_token".toString() }.access == [InterceptUrlMapPattern.TOKEN_IS_AUTHENTICATED_ANONYMOUSLY]
    }
}
