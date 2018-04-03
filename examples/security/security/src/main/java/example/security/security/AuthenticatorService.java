package example.security.security;

import io.micronaut.context.annotation.Value;
import io.micronaut.security.AuthenticationRequest;
import io.micronaut.security.AuthenticationResponse;
import io.micronaut.security.DefaultUserDetails;
import io.micronaut.security.UsernamePassword;
import org.ldaptive.ConnectionConfig;
import org.ldaptive.DefaultConnectionFactory;
import org.ldaptive.auth.Authenticator;
import org.ldaptive.auth.FormatDnResolver;
import org.ldaptive.auth.PooledBindAuthenticationHandler;
import org.ldaptive.pool.*;
import org.pac4j.core.credentials.UsernamePasswordCredentials;
import org.pac4j.core.profile.CommonProfile;
import org.pac4j.ldap.profile.LdapProfile;
import org.pac4j.ldap.profile.service.LdapProfileService;

import javax.inject.Singleton;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Singleton
public class AuthenticatorService implements io.micronaut.security.Authenticator {

    static final List<String> MATHEMATICIANS = Arrays.asList("riemann", "gauss", "euler", "euclid");
    static final List<String> SCIENTISTS = Arrays.asList("einstein", "newton", "galieleo", "tesla");

    @Value("${ldap.bindDN}")
    String baseDn;

    @Value("${ldap.server}")
    String ldapServer;

    @Value("${ldap.port}")
    Integer ldapPort;

    @Override
    public AuthenticationResponse authenticate(AuthenticationRequest req) {
        if ( req instanceof UsernamePassword ) {
            UsernamePassword usernamePassword = (UsernamePassword) req;

            try {
                FormatDnResolver dnResolver = new FormatDnResolver();
                dnResolver.setFormat(baseDn);
                ConnectionConfig connectionConfig = new ConnectionConfig();
                connectionConfig.setConnectTimeout(Duration.ofSeconds(500));
                connectionConfig.setResponseTimeout(Duration.ofSeconds(1000));
                connectionConfig.setLdapUrl("ldap://" + ldapServer + ":" + ldapPort);
                DefaultConnectionFactory connectionFactory = new DefaultConnectionFactory();
                connectionFactory.setConnectionConfig(connectionConfig);
                PoolConfig poolConfig = new PoolConfig();
                poolConfig.setMinPoolSize(1);
                poolConfig.setMaxPoolSize(2);
                poolConfig.setValidateOnCheckOut(true);
                poolConfig.setValidateOnCheckIn(true);
                poolConfig.setValidatePeriodically(false);
                SearchValidator searchValidator = new SearchValidator();
                IdlePruneStrategy pruneStrategy = new IdlePruneStrategy();
                BlockingConnectionPool connectionPool = new BlockingConnectionPool();
                connectionPool.setPoolConfig(poolConfig);
                connectionPool.setBlockWaitTime(Duration.ofSeconds(1000));
                connectionPool.setValidator(searchValidator);
                connectionPool.setPruneStrategy(pruneStrategy);
                connectionPool.setConnectionFactory(connectionFactory);
                connectionPool.initialize();
                PooledConnectionFactory pooledConnectionFactory = new PooledConnectionFactory();
                pooledConnectionFactory.setConnectionPool(connectionPool);
                PooledBindAuthenticationHandler handler = new PooledBindAuthenticationHandler();
                handler.setConnectionFactory(pooledConnectionFactory);
                Authenticator ldaptiveAuthenticator = new Authenticator();
                ldaptiveAuthenticator.setDnResolver(dnResolver);
                ldaptiveAuthenticator.setAuthenticationHandler(handler);
// pac4j:
                LdapProfileService ldapProfileService = new LdapProfileService(connectionFactory, ldaptiveAuthenticator, "", baseDn);
                UsernamePasswordCredentials credentials = new UsernamePasswordCredentials(usernamePassword.getUsername(), usernamePassword.getPassword(), null);
                ldapProfileService.validate(credentials, null);
                CommonProfile profile = credentials.getUserProfile();

                if (profile instanceof LdapProfile) {
                    final LdapProfile ldapProfile = (LdapProfile) profile;
                    String username = usernamePassword.getUsername();
                    return new DefaultUserDetails(username, rolesByUsername(username));
                }
            } catch (Exception e ) {
                return new LdapAuthenticationFailed();
            }
        }
        return new LdapAuthenticationFailed();
    }

    List<String> rolesByUsername(String username) {
        if ( MATHEMATICIANS.contains(username) ) {
            return Collections.singletonList("ROLE_GRAILS");
        }
        if ( SCIENTISTS.contains(username) ) {
            return Collections.singletonList("ROLE_GROOVY");
        }
        return new ArrayList<>();
    }
}
