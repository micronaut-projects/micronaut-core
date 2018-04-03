package example.security

import example.security.security.LdapAuthenticationFailed
import example.security.security.AuthenticatorService
import io.micronaut.security.UserDetails
import io.micronaut.security.UsernamePassword
import spock.lang.See
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class AuthenticatorServiceSpec extends Specification {

    @Subject
    @Shared
    AuthenticatorService service = new AuthenticatorService()

    @See("https://www.forumsys.com/tutorials/integration-how-to/ldap/online-ldap-test-server/")
    def "authenticate against Online LDAP Test Server"() {
        given:
        service.baseDn = 'cn=read-only-admin,dc=example,dc=com'
        service.ldapServer = 'ldap.forumsys.com'
        service.ldapPort = 389

        expect:
        service.authenticate(new UsernamePassword('newton', 'password')) instanceof UserDetails

        and:
        service.authenticate(new UsernamePassword('newton', 'foo')) instanceof LdapAuthenticationFailed
    }

    @Unroll
    def "#username has #roleName"(String username, String roleName) {
        expect:
        service.rolesByUsername(username) == [roleName]

        where:
        username | roleName
        'newton' | 'ROLE_GROOVY'
        'euler'  | 'ROLE_GRAILS'
    }

}
