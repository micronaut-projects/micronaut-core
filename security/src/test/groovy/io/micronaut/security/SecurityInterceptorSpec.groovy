package io.micronaut.security

import spock.lang.Specification
import spock.lang.Unroll

class SecurityInterceptorSpec extends Specification {

    @Unroll("#description")
    def "tests proceedResult method"(List<String> expectedRoles,
                       List<String> grantedRoles,
                       boolean authenticated,
                       SecuredInterceptor.ProceedResult expected,
                       String description) {

        expect:
        expected == SecuredInterceptor.proceedResult(expectedRoles as String[],
                new SecuredInterceptor.AuthenticationChecker() { @Override  boolean isAuthenticated() { authenticated }},
                new SecuredInterceptor.RoleChecker() { @Override boolean hasRole(String role) { grantedRoles.contains(role) }})


        where:
        expectedRoles                         | grantedRoles                 | authenticated | expected
        null                                  | []                           | false         | SecuredInterceptor.ProceedResult.UNAUTHORIZED
        ["isAuthenticated()"]                 | []                           | true          | SecuredInterceptor.ProceedResult.PROCEED
        ["isAuthenticated()"]                 | []                           | false         | SecuredInterceptor.ProceedResult.UNAUTHORIZED
        ["isAnonymous()"]                     | []                           | true          | SecuredInterceptor.ProceedResult.PROCEED
        ["isAnonymous()"]                     | []                           | false         | SecuredInterceptor.ProceedResult.PROCEED
        ["ROLE_ADMIN"]                        | ['ROLE_ADMIN', 'ROLE_USER']  | true          | SecuredInterceptor.ProceedResult.PROCEED
        ["ROLE_MANAGER"]                      | ['ROLE_ADMIN', 'ROLE_USER']  | true          | SecuredInterceptor.ProceedResult.FORBIDDEN
        ["isAuthenticated()", "ROLE_MANAGER"] | ['ROLE_ADMIN', 'ROLE_USER']  | true          | SecuredInterceptor.ProceedResult.PROCEED
        ["ROLE_ADMIN", "ROLE_USER"]           | ['ROLE_USER']                | true          | SecuredInterceptor.ProceedResult.PROCEED

        description = "For ${expectedRoles?.join(', ') ?: 'no values in annotation'} if ${authenticated ? 'authenticated' : 'not logged in'} with roles ${grantedRoles.join(', ')} expected: ${expected}"
    }
}