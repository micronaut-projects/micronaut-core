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

class InterceptUrlMapRuleSpec extends Specification {

    void "test query arguments are ignored by matching logic"() {
        given:
        def rule = new InterceptUrlMapRule(new TokenConfiguration() {
            @Override
            String getRolesName() {
                "roles"
            }
        }) {
            @Override
            protected List<InterceptUrlMapPattern> getPatternList() {
                [new InterceptUrlMapPattern("/foo", ["ROLE_ADMIN"], HttpMethod.GET)]
            }
        }

        when:
        SecurityRuleResult result = rule.check(HttpRequest.GET("/foo"), null, [roles: ["ROLE_ADMIN"]])

        then:
        result == SecurityRuleResult.ALLOWED

        when:
        result = rule.check(HttpRequest.GET("/foo?bar=true"), null, [roles: ["ROLE_ADMIN"]])

        then:
        result == SecurityRuleResult.ALLOWED

        when:
        result = rule.check(HttpRequest.GET("/foo/bar"), null, [roles: ["ROLE_ADMIN"]])

        then:
        result == SecurityRuleResult.UNKNOWN
    }
}
