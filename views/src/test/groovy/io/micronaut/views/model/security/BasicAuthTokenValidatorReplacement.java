package io.micronaut.views.model.security;

import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Requires;
import io.micronaut.security.authentication.Authentication;
import io.micronaut.security.authentication.AuthenticationResponse;
import io.micronaut.security.authentication.AuthenticationUserDetailsAdapter;
import io.micronaut.security.authentication.Authenticator;
import io.micronaut.security.authentication.UserDetails;
import io.micronaut.security.authentication.UsernamePasswordCredentials;
import io.micronaut.security.token.basicauth.BasicAuthTokenValidator;
import io.reactivex.Flowable;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Singleton;
import java.io.UnsupportedEncodingException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Requires(property = "spec.name", value = "SecurityViewModelProcessorSpec")
@Replaces(BasicAuthTokenValidator.class)
@Singleton
class BasicAuthTokenValidatorReplacement extends BasicAuthTokenValidator {
    private static final Logger LOG = LoggerFactory.getLogger(BasicAuthTokenValidator.class);

    /**
     * @param authenticator The Authenticator
     */
    public BasicAuthTokenValidatorReplacement(Authenticator authenticator) {
        super(authenticator);
    }

    @Override
    public Publisher<Authentication> validateToken(String encodedToken) {
        Optional<UsernamePasswordCredentials> creds = credsFromEncodedToken(encodedToken);
        if (creds.isPresent()) {
            Flowable<AuthenticationResponse> authenticationResponse = Flowable.fromPublisher(authenticator.authenticate(creds.get()));

            return authenticationResponse.switchMap(response -> {
            if (response.isAuthenticated()) {
                UserDetailsEmail userDetails = (UserDetailsEmail) response;
                return Flowable.just(new Authentication() {
                    @Override
                    public Map<String, Object> getAttributes() {
                        Map<String, Object> attributes = new HashMap<>();
                        attributes.put("email", userDetails.getEmail());
                        attributes.put("roles", userDetails.getRoles());
                        return attributes;
                    }

                    @Override
                    public String getName() {
                        return userDetails.getUsername();
                    }
                });
            } else {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Could not authenticate {}", creds.get().getUsername());
                }
                return Flowable.empty();
            }

        });
        }
        return Flowable.empty();
    }

    private Optional<UsernamePasswordCredentials> credsFromEncodedToken(String encodedToken) {
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(encodedToken);
        } catch (IllegalArgumentException e) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Error while trying to Base 64 decode: {}", encodedToken);
            }
            return Optional.empty();
        }

        String token;
        try {
            token = new String(decoded, "UTF-8");
        } catch (final UnsupportedEncodingException e) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Bad format of the basic auth header");
            }
            return Optional.empty();
        }

        final int delim = token.indexOf(":");
        if (delim < 0) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Bad format of the basic auth header - Delimiter : not found");
            }
            return Optional.empty();
        }

        final String username = token.substring(0, delim);
        final String password = token.substring(delim + 1);
        return Optional.of(new UsernamePasswordCredentials(username, password));
    }
}
