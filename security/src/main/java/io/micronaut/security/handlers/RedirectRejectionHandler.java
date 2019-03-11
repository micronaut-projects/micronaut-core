/*
 * Copyright 2017-2019 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/**
 * @author Sergio del Amo
 * @since 1.0
 */

package io.micronaut.security.handlers;

import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Secondary;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpResponseFactory;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpResponse;
import io.reactivex.BackpressureStrategy;
import io.reactivex.Flowable;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Singleton;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;

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
@Secondary
@Replaces(HttpStatusCodeRejectionHandler.class)
@Requires(beans = {UnauthorizedRejectionUriProvider.class, ForbiddenRejectionUriProvider.class, RedirectRejectionHandlerConfiguration.class})
@Requires(property = RedirectRejectionHandlerConfigurationProperties.PREFIX + ".enabled", notEquals = StringUtils.FALSE)
public class RedirectRejectionHandler implements RejectionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(RedirectRejectionHandler.class);

    private final UnauthorizedRejectionUriProvider unauthorizedRejectionUriProvider;
    private final ForbiddenRejectionUriProvider forbiddenRejectionUriProvider;
    private final RedirectRejectionHandlerConfiguration redirectRejectionHandlerConfiguration;

    /**
     * Constructor.
     *
     * @param unauthorizedRejectionUriProvider URI Provider to redirect to if unauthenticated
     * @param forbiddenRejectionUriProvider URI Provider to redirect to if authenticated but not enough authorization level.
     * @param redirectRejectionHandlerConfiguration Redirect Rejection Handler Configuration
     */
    public RedirectRejectionHandler(UnauthorizedRejectionUriProvider unauthorizedRejectionUriProvider,
                                    ForbiddenRejectionUriProvider forbiddenRejectionUriProvider,
                                    RedirectRejectionHandlerConfiguration redirectRejectionHandlerConfiguration) {
        this.unauthorizedRejectionUriProvider = unauthorizedRejectionUriProvider;
        this.forbiddenRejectionUriProvider = forbiddenRejectionUriProvider;
        this.redirectRejectionHandlerConfiguration = redirectRejectionHandlerConfiguration;
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
        return Flowable.create(emitter -> {
            if (shouldHandleRequest(request)) {
                try {
                    String uri = getRedirectUri(request, forbidden).orElse("/");
                    emitter.onNext(httpResponseWithUri(uri));
                } catch (URISyntaxException e) {
                    emitter.onError(e);
                }
            } else {
                emitter.onNext(HttpResponse.status(forbidden ? HttpStatus.FORBIDDEN : HttpStatus.UNAUTHORIZED));
            }
            emitter.onComplete();
        }, BackpressureStrategy.ERROR);
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
     * Builds a HTTP Response redirection to the supplied location.
     *
     * @param uri The Uri to redirect to
     * @return a 303 HTTP response with the Uri as location
     * @throws URISyntaxException if the supplied uri String cannot be used with URI.
     */
    protected MutableHttpResponse<?> httpResponseWithUri(String uri) throws URISyntaxException {
        URI location = new URI(uri);
        return HttpResponseFactory.INSTANCE.status(redirectionHttpStatus())
                .headers((headers) ->
                        headers.location(location)
                );
    }

    /**
     *
     * @return return the Http status code which will be used for the redirection
     */
    protected HttpStatus redirectionHttpStatus() {
        return redirectRejectionHandlerConfiguration.getHttpStatus();
    }

    /**
     * Returns the redirection uri.
     *
     * @param request {@link HttpRequest} being processed
     * @param forbidden if true indicates that although the user was authenticated he did not had the necessary access privileges.
     * @return the uri to redirect to
     */
    protected Optional<String> getRedirectUri(HttpRequest<?> request, boolean forbidden) {
        Optional<String> uri = forbidden ? forbiddenRejectionUriProvider.getForbiddenRedirectUri(request) :
                unauthorizedRejectionUriProvider.getUnauthorizedRedirectUri(request);
        if (LOG.isDebugEnabled()) {
            LOG.debug("redirect uri: {}", uri);
        }
        return uri;
    }

}
