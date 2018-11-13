/**
 * @author Sergio del Amo
 * @since 1.0
 */

package io.micronaut.security.handlers;

import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpResponse;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Singleton;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * If beans {@link UnauthorizedRejectionUriProvider} and {@link ForbiddenRejectionUriProvider} exists provides
 * a {@link RejectionHandler} implementation which performs redirects.
 *
 * It can be used for session based authentication flows or Open ID flows.
 *
 * @author Sergio del Amo
 * @since 1.0
 */
@Singleton
@Replaces(HttpStatusCodeRejectionHandler.class)
@Requires(beans = {UnauthorizedRejectionUriProvider.class, ForbiddenRejectionUriProvider.class})
public class RedirectRejectionHandler implements RejectionHandler {
    private static final Logger LOG = LoggerFactory.getLogger(RedirectRejectionHandler.class);

    private final UnauthorizedRejectionUriProvider unauthorizedRejectionUriProvider;
    private final ForbiddenRejectionUriProvider forbiddenRejectionUriProvider;

    /**
     * Constructor.
     *
     * @param unauthorizedRejectionUriProvider URI Provider to redirect to if unauthenticated
     * @param forbiddenRejectionUriProvider URI Provider to redirect to if authenticated but not enough authorization level.
     */
    public RedirectRejectionHandler(UnauthorizedRejectionUriProvider unauthorizedRejectionUriProvider,
                                    ForbiddenRejectionUriProvider forbiddenRejectionUriProvider) {
        this.unauthorizedRejectionUriProvider = unauthorizedRejectionUriProvider;
        this.forbiddenRejectionUriProvider = forbiddenRejectionUriProvider;
    }

    /**
     * Handles rejection of a request.
     *
     * @param request {@link HttpRequest} being processed
     * @param forbidden if true indicates that although the user was authenticated he did not had the necessary access privileges.
     * @return
     */
    @Override
    public Publisher<MutableHttpResponse<?>> reject(HttpRequest<?> request, boolean forbidden) {

        if (shouldHandleRequest(request)) {
            try {
                String uri = redirectUri(forbidden);
                MutableHttpResponse<?> rsp = httpResponseWithUri(uri);
                return Publishers.just(rsp);
            } catch (URISyntaxException e) {
                return Publishers.just(HttpResponse.serverError());
            }
        }
        return Publishers.just(HttpResponse.status(forbidden ? HttpStatus.FORBIDDEN : HttpStatus.UNAUTHORIZED));
    }

    /**
     * Decides whether the request should be handled with a redirect.
     *
     * @param request The HTTP Request
     * @return true if the request accepts text/html
     */
    protected boolean shouldHandleRequest(HttpRequest<?> request) {
        return request.getHeaders()
                .accept()
                .stream()
                .anyMatch(mediaType -> mediaType.equals(MediaType.TEXT_HTML_TYPE));
    }

    /**
     * Builds a 303 HTTP Response to the supplied location.
     *
     * @param uri The Uri to redirect to
     * @return a 303 HTTP response with the Uri as location
     * @throws URISyntaxException if the supplied uri String cannot be used with URI.
     */
    protected MutableHttpResponse<?> httpResponseWithUri(String uri) throws URISyntaxException {
        URI location = new URI(uri);
        return HttpResponse.seeOther(location);
    }

    /**
     * Returns the redirection uri.
     *
     * @param forbidden if true indicates that although the user was authenticated he did not had the necessary access privileges.
     * @return the uri to redirect to
     */
    protected String redirectUri(boolean forbidden) {
        String uri = forbidden ? getForbiddenRejectionUriProvider().forbiddenRedirectUri() :
                getUnauthorizedRejectionUriProvider().unauthorizedRedirectUri();
        if (LOG.isDebugEnabled()) {
            LOG.debug("redirect uri: {}", uri);
        }
        return uri;
    }

    /**
     * unauthorizedRejectionUriProvider Getter.
     *
     * @return URI Provider to redirect to if unauthenticated
     */
    public UnauthorizedRejectionUriProvider getUnauthorizedRejectionUriProvider() {
        return this.unauthorizedRejectionUriProvider;
    }

    /**
     * forbiddenRejectionUriProvider Getter.
     * @return URI Provider to redirect to if authenticated but not enough authorization level.
     */
    public ForbiddenRejectionUriProvider getForbiddenRejectionUriProvider() {
        return this.forbiddenRejectionUriProvider;
    }
}
