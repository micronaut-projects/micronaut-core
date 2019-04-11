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
package io.micronaut.security.rules

import io.micronaut.http.HttpMethod
import io.micronaut.http.HttpRequest
import io.micronaut.security.config.InterceptUrlMapPattern
import io.micronaut.security.token.DefaultRolesFinder
import io.micronaut.security.token.config.TokenConfiguration
import spock.lang.Issue
import spock.lang.Specification
import spock.lang.Unroll

class InterceptUrlMapRuleSpec extends Specification {

    @Unroll
    void "test query arguments are ignored by matching logic"() {

        given: 'a token configuration'
        TokenConfiguration configuration = Mock()

        and: 'the expected mock behaviour'
        (0..1) * configuration.rolesName >> "roles"
        0 * _

        and:
        SecurityRule rule = new InterceptUrlMapRule(configuration) {
            @Override
            protected List<InterceptUrlMapPattern> getPatternList() {
                [new InterceptUrlMapPattern("/foo", ["ROLE_ADMIN"], HttpMethod.GET)]
            }
        }

        expect:
        rule.check(HttpRequest.GET(uri), null, [roles: ["ROLE_ADMIN"]]) == expectedResult

        where:
        uri             || expectedResult
        '/foo'          || SecurityRuleResult.ALLOWED
        '/foo?bar=true' || SecurityRuleResult.ALLOWED
        '/foo/bar'      || SecurityRuleResult.UNKNOWN
    }

    @Issue("https://github.com/micronaut-projects/micronaut-core/issues/1511")
    @Unroll
    void "An http #request.method request to '#request.uri' should result in the security result '#expectedResult'"() {

        given: 'a token configuration'
        TokenConfiguration configuration = Mock()

        and: 'the expected mock behaviour'
        (0..1) * configuration.rolesName >> "roles"
        0 * _

        and: ''
        SecurityRule rule = new InterceptUrlMapRule(new DefaultRolesFinder(configuration)) {
            @Override
            protected List<InterceptUrlMapPattern> getPatternList() {
                [
                        new InterceptUrlMapPattern("/v1/sessions/**", ["isAuthenticated()"], null),
                        new InterceptUrlMapPattern("/v1/sessions/**", ["isAnonymous()"], HttpMethod.OPTIONS)
                ]
            }
        }

        expect:
        rule.check(request, null, null) == expectedResult

        where:
        request                                       || expectedResult
        HttpRequest.OPTIONS('/v1/sessions/123')       || SecurityRuleResult.ALLOWED
        HttpRequest.OPTIONS('/v1/sessions/')          || SecurityRuleResult.ALLOWED
        HttpRequest.GET('/v1/sessions/123')           || SecurityRuleResult.REJECTED
        HttpRequest.GET('/v1/sessions/')              || SecurityRuleResult.REJECTED
        HttpRequest.POST('/v1/sessions/123', 'body')  || SecurityRuleResult.REJECTED
        HttpRequest.POST('/v1/sessions/', 'body')     || SecurityRuleResult.REJECTED
        HttpRequest.PUT('/v1/sessions/123', 'body')   || SecurityRuleResult.REJECTED
        HttpRequest.PUT('/v1/sessions/', 'body')      || SecurityRuleResult.REJECTED
        HttpRequest.DELETE('/v1/sessions/123')        || SecurityRuleResult.REJECTED
        HttpRequest.DELETE('/v1/sessions/')           || SecurityRuleResult.REJECTED
        HttpRequest.HEAD('/v1/sessions/123')          || SecurityRuleResult.REJECTED
        HttpRequest.HEAD('/v1/sessions/')             || SecurityRuleResult.REJECTED
        HttpRequest.PATCH('/v1/sessions/123', 'body') || SecurityRuleResult.REJECTED
        HttpRequest.PATCH('/v1/sessions/', 'body')    || SecurityRuleResult.REJECTED
    }
}
