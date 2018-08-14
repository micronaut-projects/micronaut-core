package io.micronaut.security.ldap;

import io.micronaut.core.convert.value.ConvertibleValues;
import io.micronaut.security.authentication.*;
import io.micronaut.security.ldap.context.ContextBuilder;
import io.micronaut.security.ldap.context.LdapSearchService;
import io.reactivex.Flowable;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PreDestroy;
import javax.inject.Singleton;
import javax.naming.NamingException;
import javax.naming.directory.DirContext;

@Singleton
public class LdapAuthenticationProvider implements AuthenticationProvider {

    private static final Logger LOG = LoggerFactory.getLogger(LdapAuthenticationProvider.class);

    private final LdapSearchService ldapSearchService;
    private final ContextBuilder contextBuilder;
    private final ContextAuthenticationMapper contextAuthenticationMapper;
    private DirContext managerContext;

    public LdapAuthenticationProvider(LdapSearchService ldapSearchService,
                                      ContextBuilder contextBuilder,
                                      ContextAuthenticationMapper contextAuthenticationMapper) {
        this.ldapSearchService = ldapSearchService;
        this.contextBuilder = contextBuilder;
        this.contextAuthenticationMapper = contextAuthenticationMapper;
    }

    @Override
    public Publisher<AuthenticationResponse> authenticate(AuthenticationRequest authenticationRequest) {
        String username = authenticationRequest.getIdentity().toString();
        String password = authenticationRequest.getSecret().toString();

        if (LOG.isDebugEnabled()) {
            LOG.debug("Attempting to authentication with user [{}]", username);
        }

        if (managerContext == null) {
            try {
                this.managerContext = contextBuilder.buildManager();
            } catch (NamingException e) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Failed to create manager context. Returning unknown authentication failure. Encountered {}", e);
                }
                return Flowable.just(new AuthenticationFailed(AuthenticationFailureReason.UNKNOWN));
            }
        }

        try {
            ConvertibleValues<Object> attributes = ldapSearchService.search(managerContext, username, password);
            AuthenticationResponse response;
            if (attributes != null) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("User found in context [{}]. Attempting to map to an AuthenticationResponse", username);
                }
                response = contextAuthenticationMapper.map(attributes, username);
            } else {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("User not found in context [{}]. Returning credentials do not match response", username);
                }
                response = new AuthenticationFailed(AuthenticationFailureReason.CREDENTIALS_DO_NOT_MATCH);
            }
            return Flowable.just(response);
        } catch (NamingException e) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Encountered an exception while searching for user [{}].  {}", username, e);
            }
            return Flowable.just(new AuthenticationFailed(AuthenticationFailureReason.USER_NOT_FOUND));
        }
    }

    @PreDestroy
    void close() {

    }
}
