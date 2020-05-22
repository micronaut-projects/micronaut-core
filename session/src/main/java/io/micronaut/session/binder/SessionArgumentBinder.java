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
package io.micronaut.session.binder;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.bind.ArgumentBinder;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.value.MutableConvertibleValues;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.filter.OncePerRequestHttpServerFilter;
import io.micronaut.http.server.HttpServerConfiguration;
import io.micronaut.http.bind.binders.TypedRequestArgumentBinder;
import io.micronaut.session.Session;
import io.micronaut.session.SessionStore;
import io.micronaut.session.http.HttpSessionFilter;

import javax.inject.Singleton;
import java.util.Optional;

/**
 * Binds an argument of type {@link Session} for controllers.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@SuppressWarnings("unused")
@Singleton
@Requires(classes = HttpServerConfiguration.class)
public class SessionArgumentBinder implements TypedRequestArgumentBinder<Session> {

    private static final Argument<Session> TYPE = Argument.of(Session.class);

    private final SessionStore<Session> sessionStore;

    /**
     * Constructor.
     *
     * @param sessionStore The session store
     */
    public SessionArgumentBinder(SessionStore<Session> sessionStore) {
        this.sessionStore = sessionStore;
    }

    @Override
    public Argument<Session> argumentType() {
        return TYPE;
    }

    @Override
    public boolean supportsSuperTypes() {
        return false;
    }

    @Override
    public ArgumentBinder.BindingResult<Session> bind(ArgumentConversionContext<Session> context, HttpRequest<?> source) {
        if (!source.getAttributes().contains(OncePerRequestHttpServerFilter.getKey(HttpSessionFilter.class))) {
            // the filter hasn't been executed
            //noinspection unchecked
            return ArgumentBinder.BindingResult.EMPTY;
        }

        MutableConvertibleValues<Object> attrs = source.getAttributes();
        Optional<Session> existing = attrs.get(HttpSessionFilter.SESSION_ATTRIBUTE, Session.class);
        if (existing.isPresent()) {
            return () -> existing;
        } else {
            // create a new session store it in the attribute
            if (!context.getArgument().isNullable()) {
                Session newSession = sessionStore.newSession();
                attrs.put(HttpSessionFilter.SESSION_ATTRIBUTE, newSession);
                return () -> Optional.of(newSession);
            } else {
                //noinspection unchecked
                return BindingResult.EMPTY;
            }
        }
    }
}
