package io.micronaut.security.ldap;

import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.core.convert.value.ConvertibleValues;
import io.micronaut.security.authentication.*;
import io.micronaut.security.ldap.context.ContextBuilder;
import io.micronaut.security.ldap.context.LdapSearchResult;
import io.micronaut.security.ldap.context.LdapSearchService;
import io.micronaut.security.ldap.group.LdapGroupProcessor;
import io.reactivex.Flowable;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PreDestroy;
import javax.inject.Singleton;
import javax.naming.AuthenticationException;
import javax.naming.NamingException;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import java.io.Closeable;
import java.io.IOException;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;

@EachBean(LdapConfigurationProperties.class)
public class LdapAuthenticationProvider implements AuthenticationProvider, Closeable {

    private static final Logger LOG = LoggerFactory.getLogger(LdapAuthenticationProvider.class);

    private final LdapConfigurationProperties configuration;
    private final LdapSearchService ldapSearchService;
    private final ContextBuilder contextBuilder;
    private final ContextAuthenticationMapper contextAuthenticationMapper;
    private final LdapGroupProcessor ldapGroupProcessor;
    private DirContext managerContext;

    public LdapAuthenticationProvider(LdapConfigurationProperties configuration,
                                      LdapSearchService ldapSearchService,
                                      ContextBuilder contextBuilder,
                                      ContextAuthenticationMapper contextAuthenticationMapper,
                                      LdapGroupProcessor ldapGroupProcessor) {
        this.configuration = configuration;
        this.ldapSearchService = ldapSearchService;
        this.contextBuilder = contextBuilder;
        this.contextAuthenticationMapper = contextAuthenticationMapper;
        this.ldapGroupProcessor = ldapGroupProcessor;
    }

    @Override
    public Publisher<AuthenticationResponse> authenticate(AuthenticationRequest authenticationRequest) {
        String username = authenticationRequest.getIdentity().toString();
        String password = authenticationRequest.getSecret().toString();

        if (LOG.isDebugEnabled()) {
            LOG.debug("Starting authentication with configuration [{}]", configuration.getName());
        }

        if (managerContext == null) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Attempting to initialize manager context");
            }
            try {
                this.managerContext = contextBuilder.build(configuration.getContext().getManagerSettings());
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Manager context initialized successfully");
                }
            } catch (NamingException e) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Failed to create manager context. Returning unknown authentication failure. Encountered {}", e);
                }
                return Flowable.just(new AuthenticationFailed(AuthenticationFailureReason.UNKNOWN));
            }
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug("Attempting to authenticate with user [{}]", username);
        }

        AuthenticationResponse response = new AuthenticationFailed(AuthenticationFailureReason.USER_NOT_FOUND);

        try {
            Optional<LdapSearchResult> optionalResult = ldapSearchService.searchFirst(managerContext, configuration.getSearch().getSettings(new Object[]{username}));

            if (optionalResult.isPresent()) {
                LdapSearchResult result = optionalResult.get();
                if (LOG.isDebugEnabled()) {
                    LOG.debug("User found in context [{}]. Attempting to bind.", result.getDn());
                }

                DirContext userContext = null;
                try {
                    String dn = result.getDn();
                    result.setUsername(username);
                    userContext = contextBuilder.build(configuration.getContext().getSettings(result.getDn(), password));
                    if (result.getAttributes() == null) {
                        result.setAttributes(userContext.getAttributes(dn));
                    }
                } finally {
                    contextBuilder.close(userContext);
                }

                if (LOG.isDebugEnabled()) {
                    LOG.debug("Successfully bound user [{}]. Attempting to retrieving groups.", result.getDn());
                }

                Set<String> groups = Collections.emptySet();

                LdapConfigurationProperties.GroupProperties groupSettings = configuration.getGroup();
                if (groupSettings.isEnabled()) {
                    groups = ldapGroupProcessor.process(groupSettings.getAttribute(), result, () -> {
                        return ldapSearchService.search(managerContext, groupSettings.getSearchSettings(new Object[]{result.getDn()}));
                    });
                } else {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Group search is disabled for configuration [{}]", configuration.getName());
                    }
                }

                if (LOG.isTraceEnabled()) {
                    LOG.trace("Attempting to map [{}] with groups {} to an authentication response.", username, groups);
                }

                response = contextAuthenticationMapper.map(result.getAttributes(), username, groups);

                if (LOG.isDebugEnabled()) {
                    LOG.debug("Response successfully created for [{}]. Response is authenticated: [{}]", username, response.isAuthenticated());
                }
            } else {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("User not found [{}]", username);
                }
            }
        } catch (NamingException e) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Failed to authenticate with user [{}].  {}", username, e);
            }
            if (e instanceof AuthenticationException) {
                response = new AuthenticationFailed(AuthenticationFailureReason.CREDENTIALS_DO_NOT_MATCH);
            }
        }
        return Flowable.just(response);
    }

    @Override
    public void close() {
        contextBuilder.close(managerContext);
    }
}
