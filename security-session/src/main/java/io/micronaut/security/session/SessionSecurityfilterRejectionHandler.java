/*
 * Copyright 2017-2018 original authors
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

package io.micronaut.security.session;

import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.security.handlers.HttpStatusCodeRejectionHandler;
import io.micronaut.security.handlers.RejectionHandler;
import org.reactivestreams.Publisher;

import javax.inject.Singleton;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * {@link RejectionHandler} implementation for Session-based Authentication.
 *
 * @deprecated use {@link io.micronaut.security.handlers.RedirectRejectionHandler} instead
 * @author Sergio del Amo
 * @since 1.0
 */
@Deprecated
@Requires(property = SecuritySessionConfigurationProperties.PREFIX + ".legacy-rejection-handler", notEquals = StringUtils.FALSE)
@Replaces(HttpStatusCodeRejectionHandler.class)
@Singleton
public class SessionSecurityfilterRejectionHandler implements RejectionHandler {

    protected final SecuritySessionConfiguration securitySessionConfiguration;

    /**
     * Constructor.
     *
     * @param securitySessionConfiguration Security Session Configuration session store
     */
    public SessionSecurityfilterRejectionHandler(SecuritySessionConfiguration securitySessionConfiguration) {
        this.securitySessionConfiguration = securitySessionConfiguration;
    }

    /**
     * If request is HTML then do a 303 Redirect, if not respond with correspondent HTTP Status code.
     * @param request {@link HttpRequest} being processed
     * @param forbidden if true indicates that although the user was authenticated he did not had the necessary access priviledges.
     * @return Return a HTTP Response
     */
    @Override
    public Publisher<MutableHttpResponse<?>> reject(HttpRequest<?> request, boolean forbidden) {
        if (request.getHeaders().accept().stream().anyMatch(mediaType -> mediaType.equals(MediaType.TEXT_HTML_TYPE))) {
            try {
                String uri = forbidden ? securitySessionConfiguration.getForbiddenTargetUrl() :
                        securitySessionConfiguration.getUnauthorizedTargetUrl();
                if (uri == null) {
                    uri = "/";
                }
                URI location = new URI(uri);
                return Publishers.just(HttpResponse.seeOther(location));
            } catch (URISyntaxException e) {
                return Publishers.just(HttpResponse.serverError());
            }
        }
        return Publishers.just(HttpResponse.status(forbidden ? HttpStatus.FORBIDDEN : HttpStatus.UNAUTHORIZED));
    }
}
