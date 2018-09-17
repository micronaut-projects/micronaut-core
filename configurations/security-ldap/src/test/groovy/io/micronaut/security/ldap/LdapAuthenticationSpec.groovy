package io.micronaut.security.ldap

import io.micronaut.configuration.security.ldap.LdapAuthenticationProvider
import io.micronaut.context.ApplicationContext
import io.micronaut.inject.qualifiers.Qualifiers
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
                'micronaut.security.ldap.foo.enabled': true,
                'micronaut.security.ldap.foo.context.server': "ldap://localhost:${s.listenPort}",
                'micronaut.security.ldap.foo.context.managerDn': "cn=admin,dc=example,dc=com",
                'micronaut.security.ldap.foo.context.managerPassword': "password",
                'micronaut.security.ldap.foo.search.base': "dc=example,dc=com",
                'micronaut.security.ldap.foo.groups.enabled': true,
                'micronaut.security.ldap.foo.groups.base': "ou=groups,dc=example,dc=com",
                'micronaut.security.ldap.foo.groups.filter': "member={0}",
        ], "test")

        when:
        LdapAuthenticationProvider authenticationProvider = ctx.getBean(LdapAuthenticationProvider, Qualifiers.byName('foo'))
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

        cleanup:
        ctx.close()
        s.shutDown(true)
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

        cleanup:
        ctx.close()
        s.shutDown(true)
    }

    void "test configuring multiple servers"() {
        given:
        def s = createServer("basic.ldif")
        def s2 = createServer("member.ldif")
        s.startListening()
        s2.startListening()
        def ctx = ApplicationContext.run([
                'micronaut.security.enabled': true,
                'micronaut.security.ldap.basic.enabled': true,
                'micronaut.security.ldap.basic.context.server': "ldap://localhost:${s.listenPort}",
                'micronaut.security.ldap.basic.context.managerDn': "cn=admin,dc=example,dc=com",
                'micronaut.security.ldap.basic.context.managerPassword': "password",
                'micronaut.security.ldap.basic.search.base': "dc=example,dc=com",
                'micronaut.security.ldap.basic.groups.enabled': true,
                'micronaut.security.ldap.basic.groups.base': "dc=example,dc=com",
                'micronaut.security.ldap.member.enabled': true,
                'micronaut.security.ldap.member.context.server': "ldap://localhost:${s2.listenPort}",
                'micronaut.security.ldap.member.context.managerDn': "cn=admin,dc=example,dc=com",
                'micronaut.security.ldap.member.context.managerPassword': "password",
                'micronaut.security.ldap.member.search.base': "dc=example,dc=com",
                'micronaut.security.ldap.member.groups.enabled': true,
                'micronaut.security.ldap.member.groups.base': "ou=groups,dc=example,dc=com",
                'micronaut.security.ldap.member.groups.filter': "member={0}",
        ], "test")

        when:
        LdapAuthenticationProvider authenticationProvider = ctx.getBean(LdapAuthenticationProvider, Qualifiers.byName('member'))
        AuthenticationResponse response = authenticate(authenticationProvider,"gauss")

        then:
        response.authenticated
        ((UserDetails) response).username == "gauss"
        ((UserDetails) response).roles.size() == 2
        ((UserDetails) response).roles.contains("users")
        ((UserDetails) response).roles.contains("admins")

        when:
        authenticationProvider = ctx.getBean(LdapAuthenticationProvider, Qualifiers.byName('basic'))
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
        s2.shutDown(true)
    }

}
