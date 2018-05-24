/*
 * Copyright 2017-2018 original authors
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
package io.micronaut.security.rules

import io.micronaut.http.HttpMethod
import io.micronaut.http.HttpRequest
import io.micronaut.security.config.InterceptUrlMapPattern
import io.micronaut.security.config.SecurityConfigurationProperties
import io.micronaut.security.token.config.TokenConfiguration
import spock.lang.Specification
import spock.lang.Unroll

class ConfigurationInterceptUrlMapRuleSpec extends Specification {

    @Unroll('#description')
    def "verify behaviour different intercept url map configurations"(SecurityRuleResult securityRuleResult, List<InterceptUrlMapPattern> interceptUrlMap, String description) {
        given:
        def securityConfiguration = Stub(SecurityConfigurationProperties) {
            getInterceptUrlMap() >> interceptUrlMap
        }
        def request = Stub(HttpRequest) {
            getUri() >> new URI('/books')
            getMethod() >> HttpMethod.GET
        }
        ConfigurationInterceptUrlMapRule provider = new ConfigurationInterceptUrlMapRule(Mock(TokenConfiguration), securityConfiguration)

        expect:
        provider.check(request, null, null) == securityRuleResult

        where:
        securityRuleResult          | interceptUrlMap                                                                               | description
        SecurityRuleResult.ALLOWED  | [new InterceptUrlMapPattern('/books',[SecurityRule.IS_ANONYMOUS], HttpMethod.GET)]     | 'if interceptUrlMap defines anonymous and GET method, result is ALLOWED'
        SecurityRuleResult.ALLOWED  | [new InterceptUrlMapPattern('/books',[SecurityRule.IS_ANONYMOUS], null)]    | 'if interceptUrlMap defines anonymous and no HTTP method, result is ALLOWED'
        SecurityRuleResult.REJECTED | [new InterceptUrlMapPattern('/books',['ROLE_ADMIN'], HttpMethod.GET)]                  | 'if interceptUrlMap defines a neccessary role and GET method, result is REJECTED'
    }

    @Unroll("comparing required: #requiredRoles and granted should return #description")
    def 'verify compare role behaviour'(List<String> requiredRoles, List<String> grantedRoles, SecurityRuleResult expected, String description) {
        given:
        ConfigurationInterceptUrlMapRule provider = new ConfigurationInterceptUrlMapRule(Mock(TokenConfiguration), Mock(SecurityConfigurationProperties))

        expect:
        expected == provider.compareRoles(requiredRoles, grantedRoles)

        where:
        requiredRoles                | grantedRoles                                     | expected
        ['ROLE_ADMIN', 'ROLE_USER']  | ['ROLE_ADMIN', 'ROLE_USER']                      | SecurityRuleResult.ALLOWED
        ['isAuthenticated()']        | ['ROLE_ADMIN', 'ROLE_USER', 'isAuthenticated()'] | SecurityRuleResult.ALLOWED
        ['ROLE_ADMIN', 'ROLE_USER']  | ['ROLE_USER']                                    | SecurityRuleResult.ALLOWED
        ['ROLE_ADMIN']               | ['ROLE_USER']                                    | SecurityRuleResult.REJECTED
        ['isAnonymous()']            | [SecurityRule.IS_ANONYMOUS]                      | SecurityRuleResult.ALLOWED
        ['isAuthenticated()']        | [SecurityRule.IS_AUTHENTICATED]                  | SecurityRuleResult.ALLOWED
        description = expected == SecurityRuleResult.ALLOWED ? 'Allowed' : 'Rejected'
    }
}
