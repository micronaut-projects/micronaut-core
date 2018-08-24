package io.micronaut.security.ldap

import io.micronaut.context.ApplicationContext
import io.micronaut.security.authentication.AuthenticationFailed
import io.micronaut.security.authentication.AuthenticationResponse
import io.micronaut.security.authentication.UserDetails

class LdapAuthenticationSpec extends InMemoryLdapSpec {

    void "test authentication and role retrieval with uniquemember"() {
        given:
        def s = createServer("basic.ldif")
        s.startListening()
        def ctx = ApplicationContext.run([
                'micronaut.security.enabled': true,
                'micronaut.security.ldap.default.enabled': true,
                'micronaut.security.ldap.default.context.server': "ldap://localhost:${s.listenPort}",
                'micronaut.security.ldap.default.context.managerDn': "cn=admin,dc=example,dc=com",
                'micronaut.security.ldap.default.context.managerPassword': "password",
                'micronaut.security.ldap.default.search.base': "dc=example,dc=com",
                'micronaut.security.ldap.default.groups.enabled': true,
                'micronaut.security.ldap.default.groups.base': "dc=example,dc=com",
        ], "test")

        when:
        LdapAuthenticationProvider authenticationProvider = ctx.getBean(LdapAuthenticationProvider)
        AuthenticationResponse response = authenticate(authenticationProvider,"riemann")

        then:
        response.authenticated
        ((UserDetails) response).username == "riemann"
        ((UserDetails) response).roles.size() == 1
        ((UserDetails) response).roles.contains("Mathematicians")

        when:
        response = authenticate(authenticationProvider,"newton")

        then:
        response.authenticated
        ((UserDetails) response).username == "newton"
        ((UserDetails) response).roles.size() == 1
        ((UserDetails) response).roles.contains("Scientists")


        when:
        response = authenticate(authenticationProvider,"gauss")

        then:
        response.authenticated
        ((UserDetails) response).username == "gauss"
        ((UserDetails) response).roles.size() == 2
        ((UserDetails) response).roles.contains("Scientists")
        ((UserDetails) response).roles.contains("Mathematicians")

        cleanup:
        ctx.close()
        s.shutDown(true)
    }

    void "test authentication and role retrieval with member"() {
        given:
        def s = createServer("member.ldif")
        s.startListening()
        def ctx = ApplicationContext.run([
                'micronaut.security.enabled': true,
                'micronaut.security.ldap.default.enabled': true,
                'micronaut.security.ldap.default.context.server': "ldap://localhost:${s.listenPort}",
                'micronaut.security.ldap.default.context.managerDn': "cn=admin,dc=example,dc=com",
                'micronaut.security.ldap.default.context.managerPassword': "password",
                'micronaut.security.ldap.default.search.base': "dc=example,dc=com",
                'micronaut.security.ldap.default.groups.enabled': true,
                'micronaut.security.ldap.default.groups.base': "ou=groups,dc=example,dc=com",
                'micronaut.security.ldap.default.groups.filter': "member={0}",
        ], "test")

        when:
        LdapAuthenticationProvider authenticationProvider = ctx.getBean(LdapAuthenticationProvider)
        AuthenticationResponse response = authenticate(authenticationProvider,"euclid")

        then:
        response.authenticated
        ((UserDetails) response).username == "euclid"
        ((UserDetails) response).roles.size() == 1
        ((UserDetails) response).roles.contains("users")

        when:
        response = authenticate(authenticationProvider,"gauss")

        then:
        response.authenticated
        ((UserDetails) response).username == "gauss"
        ((UserDetails) response).roles.size() == 2
        ((UserDetails) response).roles.contains("users")
        ((UserDetails) response).roles.contains("admins")

        cleanup:
        ctx.close()
        s.shutDown(true)
    }

    void "test authenticating with a username that doesn't exist"() {
        given:
        def s = createServer("basic.ldif")
        s.startListening()
        def ctx = ApplicationContext.run([
                'micronaut.security.enabled': true,
                'micronaut.security.ldap.default.enabled': true,
                'micronaut.security.ldap.default.context.server': "ldap://localhost:${s.listenPort}",
                'micronaut.security.ldap.default.context.managerDn': "cn=admin,dc=example,dc=com",
                'micronaut.security.ldap.default.context.managerPassword': "password",
                'micronaut.security.ldap.default.search.base': "dc=example,dc=com",
                'micronaut.security.ldap.default.groups.enabled': true,
                'micronaut.security.ldap.default.groups.base': "dc=example,dc=com",
        ], "test")

        when:
        LdapAuthenticationProvider authenticationProvider = ctx.getBean(LdapAuthenticationProvider)
        AuthenticationResponse response = authenticate(authenticationProvider,"abc")

        then:
        response instanceof AuthenticationFailed
        response.message.get() == "User Not Found"
    }

    void "test authenticating with an invalid password"() {
        given:
        def s = createServer("basic.ldif")
        s.startListening()
        def ctx = ApplicationContext.run([
                'micronaut.security.enabled': true,
                'micronaut.security.ldap.default.enabled': true,
                'micronaut.security.ldap.default.context.server': "ldap://localhost:${s.listenPort}",
                'micronaut.security.ldap.default.context.managerDn': "cn=admin,dc=example,dc=com",
                'micronaut.security.ldap.default.context.managerPassword': "password",
                'micronaut.security.ldap.default.search.base': "dc=example,dc=com",
                'micronaut.security.ldap.default.groups.enabled': true,
                'micronaut.security.ldap.default.groups.base': "dc=example,dc=com",
        ], "test")

        when:
        LdapAuthenticationProvider authenticationProvider = ctx.getBean(LdapAuthenticationProvider)
        AuthenticationResponse response = authenticate(authenticationProvider,"euclid", "abc")

        then:
        response instanceof AuthenticationFailed
        response.message.get() == "Credentials Do Not Match"
    }
}
