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
import io.micronaut.security.token.config.TokenConfiguration
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
}
