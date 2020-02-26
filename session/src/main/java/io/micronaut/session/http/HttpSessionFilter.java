/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.session.http;

import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.HttpAttributes;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.filter.OncePerRequestHttpServerFilter;
import io.micronaut.http.filter.ServerFilterChain;
import io.micronaut.http.server.exceptions.InternalServerException;
import io.micronaut.inject.MethodExecutionHandle;
import io.micronaut.session.Session;
import io.micronaut.session.SessionStore;
import io.micronaut.session.annotation.SessionValue;
import io.reactivex.Flowable;
import org.reactivestreams.Publisher;

import java.util.List;
import java.util.Optional;

/**
 * A {@link io.micronaut.http.filter.HttpServerFilter} that resolves the current user {@link Session} if present and encodes the Session ID in
 * the response.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Filter("/**")
public class HttpSessionFilter extends OncePerRequestHttpServerFilter {

    /**
     * The order of the filter.
     */
    public static final Integer ORDER = 0;

    /**
     * Constant for Micronaut SESSION attribute.
     */
    public static final CharSequence SESSION_ATTRIBUTE = "micronaut.SESSION";

    private final SessionStore<Session> sessionStore;
    private final HttpSessionIdResolver[] resolvers;
    private final HttpSessionIdEncoder[] encoders;

    /**
     * Constructor.
     *
     * @param sessionStore The session store
     * @param resolvers The HTTP session id resolvers
     * @param encoders The HTTP session id encoders
     */
    public HttpSessionFilter(SessionStore<Session> sessionStore, HttpSessionIdResolver[] resolvers, HttpSessionIdEncoder[] encoders) {
        this.sessionStore = sessionStore;
        this.resolvers = resolvers;
        this.encoders = encoders;
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    @Override
    protected Publisher<MutableHttpResponse<?>> doFilterOnce(HttpRequest<?> request, ServerFilterChain chain) {
        for (HttpSessionIdResolver resolver : resolvers) {
            List<String> ids = resolver.resolveIds(request);
            if (CollectionUtils.isNotEmpty(ids)) {
                String id = ids.get(0);
                Publisher<Optional<Session>> sessionLookup = Publishers.fromCompletableFuture(() -> sessionStore.findSession(id));
                Flowable<MutableHttpResponse<?>> storeSessionInAttributes = Flowable
                    .fromPublisher(sessionLookup)
                    .switchMap(session -> {
                        session.ifPresent(entries -> request.getAttributes().put(SESSION_ATTRIBUTE, entries));
                        return chain.proceed(request);
                    });
                return encodeSessionId(request, storeSessionInAttributes);
            }
        }
        return encodeSessionId(request, chain.proceed(request));
    }

    private Publisher<MutableHttpResponse<?>> encodeSessionId(HttpRequest<?> request, Publisher<MutableHttpResponse<?>> responsePublisher) {
        Flowable<SessionAndResponse> responseFlowable = Flowable.fromPublisher(responsePublisher)
            .switchMap(response -> {

                Optional<MethodExecutionHandle> routeMatch = request.getAttribute(HttpAttributes.ROUTE_MATCH, MethodExecutionHandle.class);
                Optional<?> body = response.getBody();

                String sessionAttr;

                if (body.isPresent()) {
                    sessionAttr = routeMatch.flatMap((m) -> {
                        if (!m.hasAnnotation(SessionValue.class)) {
                            return Optional.empty();
                        } else {
                            String attributeName = m.stringValue(SessionValue.class).orElse(null);
                            if (!StringUtils.isEmpty(attributeName)) {
                                return Optional.of(attributeName);
                            } else {
                                throw new InternalServerException("@SessionValue on a return type must specify an attribute name");
                            }
                        }
                    }).orElse(null);
                } else {
                    sessionAttr = null;
                }

                Optional<Session> opt = request.getAttributes().get(SESSION_ATTRIBUTE, Session.class);
                if (opt.isPresent()) {
                    Session session = opt.get();
                    if (sessionAttr != null) {
                       session.put(sessionAttr, body.get());
                    }

                    if (session.isNew() || session.isModified()) {
                        return Flowable
                            .fromPublisher(Publishers.fromCompletableFuture(() -> sessionStore.save(session)))
                            .map((s) -> new SessionAndResponse(Optional.of(s), response));
                    }
                } else if (sessionAttr != null) {
                    Session newSession = sessionStore.newSession();
                    newSession.put(sessionAttr, body.get());
                    return Flowable
                            .fromPublisher(Publishers.fromCompletableFuture(() -> sessionStore.save(newSession)))
                            .map((s) -> new SessionAndResponse(Optional.of(s), response));
                }
                return Flowable.just(new SessionAndResponse(opt, response));
            });

        return responseFlowable.map(sessionAndResponse -> {
            Optional<Session> session = sessionAndResponse.session;
            MutableHttpResponse<?> response = sessionAndResponse.response;
            if (session.isPresent()) {
                Session s = session.get();
                for (HttpSessionIdEncoder encoder : encoders) {
                    encoder.encodeId(request, response, s);
                }
            }
            return response;
        });
    }

    /**
     * Store the session and the response.
     */
    class SessionAndResponse {
        final Optional<Session> session;
        final MutableHttpResponse<?> response;

        /**
         * Constructor.
         *
         * @param session The optional session
         * @param response The mutable HTTP response
         */
        SessionAndResponse(Optional<Session> session, MutableHttpResponse<?> response) {
            this.session = session;
            this.response = response;
        }
    }
}
