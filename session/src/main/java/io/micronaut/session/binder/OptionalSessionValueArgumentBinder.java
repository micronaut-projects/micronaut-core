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
import io.micronaut.http.bind.binders.AnnotatedRequestArgumentBinder;
import io.micronaut.http.bind.binders.TypedRequestArgumentBinder;
import io.micronaut.session.Session;
import io.micronaut.session.annotation.SessionValue;
import io.micronaut.session.http.HttpSessionFilter;

import javax.inject.Singleton;
import java.util.Optional;

/**
 * @author Graeme Rocher
 * @since 1.0
 */
@Singleton
@Requires(classes = HttpServerConfiguration.class)
public class OptionalSessionValueArgumentBinder implements TypedRequestArgumentBinder<Optional>, AnnotatedRequestArgumentBinder<SessionValue, Optional> {

    private static final Argument<Optional> OPTIONAL_ARGUMENT = Argument.of(Optional.class);

    @Override
    public Argument<Optional> argumentType() {
        return OPTIONAL_ARGUMENT;
    }

    @Override
    public Class<SessionValue> getAnnotationType() {
        return SessionValue.class;
    }

    @Override
    public ArgumentBinder.BindingResult<Optional> bind(ArgumentConversionContext<Optional> context, HttpRequest<?> source) {
        MutableConvertibleValues<Object> attrs = source.getAttributes();
        if (!attrs.contains(OncePerRequestHttpServerFilter.getKey(HttpSessionFilter.class))) {
            // the filter hasn't been executed but the argument is not satisfied
            return ArgumentBinder.BindingResult.UNSATISFIED;
        }

        Argument<Optional> argument = context.getArgument();
        String name = context.getAnnotationMetadata().stringValue(SessionValue.class).orElse(argument.getName());
        Optional<Session> existing = attrs.get(HttpSessionFilter.SESSION_ATTRIBUTE, Session.class);
        if (existing.isPresent()) {
            String finalName = name;
            return () -> Optional.of(
                existing.get().get(finalName, context.getFirstTypeVariable().orElse(Argument.OBJECT_ARGUMENT))
            );
        } else {
            return ArgumentBinder.BindingResult.EMPTY;
        }
    }
}
