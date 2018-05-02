package example.security.providers

import groovy.transform.CompileStatic
import io.micronaut.context.annotation.Value
import io.micronaut.security.authentication.AuthenticationFailed
import io.micronaut.security.authentication.AuthenticationProvider
import io.micronaut.security.authentication.AuthenticationRequest
import io.micronaut.security.authentication.AuthenticationResponse
import io.micronaut.security.authentication.UserDetails
import org.ldaptive.ConnectionConfig
import org.ldaptive.DefaultConnectionFactory
import org.ldaptive.auth.Authenticator
import org.ldaptive.auth.FormatDnResolver
import org.ldaptive.auth.PooledBindAuthenticationHandler
import org.ldaptive.pool.BlockingConnectionPool
import org.ldaptive.pool.IdlePruneStrategy
import org.ldaptive.pool.PoolConfig
import org.ldaptive.pool.PooledConnectionFactory
import org.ldaptive.pool.SearchValidator
import io.micronaut.security.authentication.UsernamePasswordCredentials
import org.pac4j.core.profile.CommonProfile
import org.pac4j.ldap.profile.LdapProfile
import org.pac4j.ldap.profile.service.LdapProfileService
import javax.inject.Singleton
import java.time.Duration

@CompileStatic
@Singleton
class LdapService implements AuthenticationProvider {

    static final List<String> MATHEMATICIANS = ["riemann", "gauss", "euler", "euclid"]
    static final List<String> SCIENTISTS = ["einstein", "newton", "galieleo", "tesla"]

    @Value('${ldap.bindDN}')
    String baseDn

    @Value('${ldap.server}')
    String ldapServer

    @Value('${ldap.port}')
    Integer ldapPort

    @Override
    AuthenticationResponse authenticate(AuthenticationRequest authenticationRequest) {
        try {
            FormatDnResolver dnResolver = new FormatDnResolver()
            dnResolver.setFormat(baseDn)
            ConnectionConfig connectionConfig = new ConnectionConfig()
            connectionConfig.with {
                setConnectTimeout(Duration.ofSeconds(500))
                setResponseTimeout(Duration.ofSeconds(1000))
                setLdapUrl("ldap://" + ldapServer + ":" + ldapPort)
            }
            DefaultConnectionFactory connectionFactory = new DefaultConnectionFactory()
            connectionFactory.setConnectionConfig(connectionConfig)
            PoolConfig poolConfig = new PoolConfig()
            poolConfig.with {
                setMinPoolSize(1)
                setMaxPoolSize(2)
                setValidateOnCheckOut(true)
                setValidateOnCheckIn(true)
                setValidatePeriodically(false)
            }
            SearchValidator searchValidator = new SearchValidator()
            IdlePruneStrategy pruneStrategy = new IdlePruneStrategy()
            BlockingConnectionPool connectionPool = new BlockingConnectionPool()
            connectionPool.with {
                setPoolConfig(poolConfig)
                setBlockWaitTime(Duration.ofSeconds(1000))
                setValidator(searchValidator)
                setPruneStrategy(pruneStrategy)
                setConnectionFactory(connectionFactory)
                initialize()
            }
            PooledConnectionFactory pooledConnectionFactory = new PooledConnectionFactory()
            pooledConnectionFactory.setConnectionPool(connectionPool)
            PooledBindAuthenticationHandler handler = new PooledBindAuthenticationHandler()
            handler.setConnectionFactory(pooledConnectionFactory)
            Authenticator ldaptiveAuthenticator = new Authenticator()
            ldaptiveAuthenticator.setDnResolver(dnResolver)
            ldaptiveAuthenticator.setAuthenticationHandler(handler)
            LdapProfileService ldapProfileService = new LdapProfileService(connectionFactory, ldaptiveAuthenticator, '', baseDn)
            final String username = authenticationRequest.getIdentity() as String
            final String password = authenticationRequest.getSecret() as String
            org.pac4j.core.credentials.UsernamePasswordCredentials credentials =
                    new org.pac4j.core.credentials.UsernamePasswordCredentials(username, password, null)
            ldapProfileService.validate(credentials, null)
            CommonProfile profile = credentials.getUserProfile()

            if (profile instanceof LdapProfile) {

                return new UserDetails(username, rolesByUsername(username))
            }
        } catch (Exception e ) {
            return new AuthenticationFailed()
        }
    }

    private List<String> rolesByUsername(String username) {
        if ( MATHEMATICIANS.contains(username) ) {
            return ['ROLE_GRAILS']
        }
        if ( SCIENTISTS.contains(username) ) {
            return ['ROLE_GROOVY']
        }
        return new ArrayList<>()
    }
}
