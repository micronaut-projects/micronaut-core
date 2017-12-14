/*
 * Copyright 2017 original authors
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
package org.particleframework.session.http;

import io.reactivex.Flowable;
import io.reactivex.functions.Function;
import org.particleframework.core.async.publisher.Publishers;
import org.particleframework.core.util.CollectionUtils;
import org.particleframework.http.HttpRequest;
import org.particleframework.http.MutableHttpResponse;
import org.particleframework.http.annotation.Filter;
import org.particleframework.http.filter.HttpServerFilter;
import org.particleframework.http.filter.OncePerRequestHttpServerFilter;
import org.particleframework.session.Session;
import org.particleframework.session.SessionStore;
import org.reactivestreams.Publisher;

import java.util.List;
import java.util.Optional;

/**
 * A {@link HttpServerFilter} that resolves the current user {@link Session} if present and encodes the Session ID in the response
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Filter("/**")
public class HttpSessionFilter extends OncePerRequestHttpServerFilter {

    public static final CharSequence SESSION_ATTRIBUTE = "particle.SESSION";

    final SessionStore<Session> sessionStore;
    final HttpSessionIdResolver[] resolvers;
    final HttpSessionIdEncoder[] encoders;

    public HttpSessionFilter(SessionStore<Session> sessionStore, HttpSessionIdResolver[] resolvers, HttpSessionIdEncoder[] encoders) {
        this.sessionStore = sessionStore;
        this.resolvers = resolvers;
        this.encoders = encoders;
    }

    @Override
    protected Publisher<MutableHttpResponse<?>> doFilterOnce(HttpRequest<?> request, ServerFilterChain chain) {
        for (HttpSessionIdResolver resolver : resolvers) {
            List<String> ids = resolver.resolveIds(request);
            if(CollectionUtils.isNotEmpty(ids)) {
                String id = ids.get(0);
                Publisher<Optional<Session>> sessionLookup = Publishers.fromCompletableFuture(() -> sessionStore.findSession(id));
                Flowable<MutableHttpResponse<?>> storeSessionInAttributes = Flowable.fromPublisher(sessionLookup)
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
                .switchMap(mutableHttpResponse -> {
                    Optional<Session> opt = request.getAttributes().get(SESSION_ATTRIBUTE, Session.class);
                    if (opt.isPresent()) {
                        Session session = opt.get();
                        if (session.isNew()) {
                            return Flowable.fromPublisher(Publishers.fromCompletableFuture(() -> sessionStore.save(session)))
                                    .map((s) -> new SessionAndResponse(Optional.of(s), mutableHttpResponse));
                        }
                    }
                    return Flowable.just(new SessionAndResponse(opt, mutableHttpResponse));
                });

        return responseFlowable.map(sessionAndResponse -> {
            Optional<Session> session = sessionAndResponse.session;
            MutableHttpResponse<?> response = sessionAndResponse.response;
            if(session.isPresent()) {
                Session s = session.get();
                for (HttpSessionIdEncoder encoder : encoders) {
                    encoder.encodeId(request, response, s);
                }
            }
            return response;
        });
    }

    class SessionAndResponse {
        final Optional<Session> session;
        final MutableHttpResponse<?> response;

        public SessionAndResponse(Optional<Session> session, MutableHttpResponse<?> response) {
            this.session = session;
            this.response = response;
        }
    }
}
