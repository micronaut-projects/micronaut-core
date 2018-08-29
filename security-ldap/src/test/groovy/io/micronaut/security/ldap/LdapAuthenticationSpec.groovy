package io.micronaut.security.ldap

import io.micronaut.context.ApplicationContext
import io.micronaut.http.ssl.ClientAuthentication
import io.micronaut.inject.qualifiers.Qualifiers
import io.micronaut.security.authentication.AuthenticationFailed
import io.micronaut.security.authentication.AuthenticationResponse
import io.micronaut.security.authentication.UserDetails
import io.micronaut.security.ldap.configuration.LdapConfiguration
import spock.lang.Ignore

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

    void "test multiple servers with ssl configuration"() {
        def ctx = ApplicationContext.run([
                'micronaut.security.enabled': true,
                'micronaut.security.ldap.basic.enabled': true,
                'micronaut.security.ldap.basic.ssl.enabled': false,
                'micronaut.security.ldap.basic.ssl.build-self-signed': false,
                'micronaut.security.ldap.basic.ssl.client-authentication': 'WANT',
                'micronaut.security.ldap.basic.ssl.key.password': 'a',
                'micronaut.security.ldap.basic.ssl.key.alias': 'b',
                'micronaut.security.ldap.basic.ssl.key-store.path': 'c',
                'micronaut.security.ldap.basic.ssl.key-store.password': 'd',
                'micronaut.security.ldap.basic.ssl.trust-store.path': 'e',
                'micronaut.security.ldap.basic.ssl.trust-store.password': 'f',
                'micronaut.security.ldap.member.enabled': true,
                'micronaut.security.ldap.member.ssl.enabled': true,
                'micronaut.security.ldap.member.ssl.build-self-signed': true,
                'micronaut.security.ldap.member.ssl.client-authentication': 'NEED',
                'micronaut.security.ldap.member.ssl.key.password': 'g',
                'micronaut.security.ldap.member.ssl.key.alias': 'h',
                'micronaut.security.ldap.member.ssl.key-store.path': 'i',
                'micronaut.security.ldap.member.ssl.key-store.password': 'j',
                'micronaut.security.ldap.member.ssl.trust-store.path': 'k',
                'micronaut.security.ldap.member.ssl.trust-store.password': 'l',
        ], "test")

        when:
        LdapConfiguration config = ctx.getBean(LdapConfiguration, Qualifiers.byName('basic'))

        then:
        config.enabled
        !config.getSsl().enabled
        !config.getSsl().buildSelfSigned()
        config.getSsl().getClientAuthentication().get() == ClientAuthentication.WANT
        config.getSsl().getKey().getPassword().get() == 'a'
        config.getSsl().getKey().getAlias().get() == 'b'
        config.getSsl().getKeyStore().getPath().get() == 'c'
        config.getSsl().getKeyStore().getPassword().get() == 'd'
        config.getSsl().getTrustStore().getPath().get() == 'e'
        config.getSsl().getTrustStore().getPassword().get() == 'f'

        when:
        config = ctx.getBean(LdapConfiguration, Qualifiers.byName('member'))

        then:
        config.enabled
        config.getSsl().enabled
        config.getSsl().buildSelfSigned()
        config.getSsl().getClientAuthentication().get() == ClientAuthentication.NEED
        config.getSsl().getKey().getPassword().get() == 'g'
        config.getSsl().getKey().getAlias().get() == 'h'
        config.getSsl().getKeyStore().getPath().get() == 'i'
        config.getSsl().getKeyStore().getPassword().get() == 'j'
        config.getSsl().getTrustStore().getPath().get() == 'k'
        config.getSsl().getTrustStore().getPassword().get() == 'l'
    }

    void "test authenticating with SSL"() {
        given:
        def s = createServer("basic.ldif", true)
        s.startListening()
        def ctx = ApplicationContext.run([
                'micronaut.security.enabled': true,
                'micronaut.security.ldap.default.enabled': true,
                'micronaut.security.ldap.default.context.server': "ldaps://localhost:${s.listenPort}",
                'micronaut.security.ldap.default.context.managerDn': "cn=admin,dc=example,dc=com",
                'micronaut.security.ldap.default.context.managerPassword': "password",
                'micronaut.security.ldap.default.search.base': "dc=example,dc=com",
                'micronaut.security.ldap.default.groups.enabled': true,
                'micronaut.security.ldap.default.groups.base': "dc=example,dc=com",
                'micronaut.security.ldap.default.ssl.key-store.path': 'classpath:keystore.p12',
                'micronaut.security.ldap.default.ssl.key-store.password': 'foobar',
                'micronaut.security.ldap.default.ssl.key-store.type': 'PKCS12',
                'micronaut.security.ldap.default.ssl.ciphers': 'TLS_DH_anon_WITH_AES_128_CBC_SHA',
                'micronaut.security.ldap.default.ssl.protocol': 'TLS',
                'micronaut.security.ldap.default.ssl.protocols': 'TLSv1.1',
        ], "test")

        when:
        LdapAuthenticationProvider authenticationProvider = ctx.getBean(LdapAuthenticationProvider)
        AuthenticationResponse response = authenticate(authenticationProvider,"riemann")

        then:
        response.authenticated
        ((UserDetails) response).username == "riemann"
        ((UserDetails) response).roles.size() == 1
        ((UserDetails) response).roles.contains("Mathematicians")
    }
}
