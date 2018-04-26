package io.micronaut.security.token;

import io.micronaut.context.event.ApplicationEventPublisher;
import io.micronaut.http.HttpRequest;
import io.micronaut.security.authentication.Authentication;
import io.micronaut.security.event.TokenValidatedEvent;
import io.micronaut.security.filters.AuthenticationFetcher;
import io.micronaut.security.token.reader.TokenReader;
import io.micronaut.security.token.validator.TokenValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Singleton;
import java.util.Collection;
import java.util.Optional;

@Singleton
public class TokenAuthenticationFetcher implements AuthenticationFetcher {
    /**
     * The order of the fetcher.
     */
    public static final Integer ORDER = 0;

    private static final Logger LOG = LoggerFactory.getLogger(TokenAuthenticationFetcher.class);

    protected final Collection<TokenReader> tokenReaders;
    protected final Collection<TokenValidator> tokenValidators;
    protected final ApplicationEventPublisher eventPublisher;

    /**
     * @param tokenValidators The list of {@link TokenValidator} which attempt to validate the request
     * @param tokenReaders The list {@link TokenReader} which attempt to read the request
     * @param eventPublisher The Application event publiser
     */
    public TokenAuthenticationFetcher(Collection<TokenValidator> tokenValidators,
                                      Collection<TokenReader> tokenReaders,
                                      ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
        this.tokenValidators = tokenValidators;
        this.tokenReaders = tokenReaders;
    }

    @Override
    public Optional<Authentication> fetchAuthentication(HttpRequest<?> request) {

        String method = request.getMethod().toString();
        String path = request.getPath();

        Optional<String> token = Optional.empty();
        for (TokenReader tokenReader : tokenReaders) {
            token = tokenReader.findToken(request);
            if (token.isPresent()) {
                break;
            }
        }
        if (LOG.isDebugEnabled()) {
            if (token.isPresent()) {
                LOG.debug("Token {} found in request {} {}", token.get(), method, path);
            } else {
                LOG.debug("Unauthenticated request {}, {}, no token found.", method, path);
            }
        }

        Optional<Authentication> authentication = Optional.empty();
        for (TokenValidator tokenValidator : tokenValidators) {
            authentication = token.flatMap(tokenValidator::validateToken);
            if (authentication.isPresent()) {
                token.ifPresent(s -> eventPublisher.publishEvent(new TokenValidatedEvent(s)));
                break;
            }
        }
        return authentication;
    }


    @Override
    public int getOrder() {
        return ORDER;
    }
}
