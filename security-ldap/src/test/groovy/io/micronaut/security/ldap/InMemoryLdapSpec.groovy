package io.micronaut.security.ldap

import com.unboundid.ldap.listener.InMemoryDirectoryServer
import com.unboundid.ldap.listener.InMemoryDirectoryServerConfig
import com.unboundid.ldif.LDIFReader
import io.micronaut.security.authentication.AuthenticationRequest
import io.micronaut.security.authentication.AuthenticationResponse
import io.reactivex.Flowable
import spock.lang.Specification

abstract class InMemoryLdapSpec extends Specification {

    InMemoryDirectoryServer createServer(String ldifPath) {
        InMemoryDirectoryServerConfig config =
                new InMemoryDirectoryServerConfig("dc=example,dc=com")
        config.addAdditionalBindCredentials("cn=admin,dc=example,dc=com", "password")
        InMemoryDirectoryServer ds = new InMemoryDirectoryServer(config)
        InputStream inputStream = this.getClass().getClassLoader().getResourceAsStream(ldifPath)
        ds.importFromLDIF(true, new LDIFReader(inputStream))
        ds
    }

    AuthenticationResponse authenticate(LdapAuthenticationProvider authenticationProvider, String username, String password = "password") {
        Flowable.fromPublisher(authenticationProvider.authenticate(new AuthenticationRequest() {
            @Override
            Object getIdentity() {
                return username
            }

            @Override
            Object getSecret() {
                return password
            }
        })).blockingFirst()
    }

}