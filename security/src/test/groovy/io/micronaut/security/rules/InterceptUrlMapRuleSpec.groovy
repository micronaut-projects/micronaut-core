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
