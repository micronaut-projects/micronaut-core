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

package io.micronaut.security.authentication;

import io.micronaut.core.bind.ArgumentBinder;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.value.MutableConvertibleValues;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.filter.OncePerRequestHttpServerFilter;
import io.micronaut.http.server.binding.binders.TypedRequestArgumentBinder;
import io.micronaut.security.filters.SecurityFilter;

import javax.inject.Singleton;
import java.security.Principal;
import java.util.Optional;

/**
 * Responsible for binding a {@link Principal} to a route argument.
 *
 * @author Sergio del Amo
 * @since 1.0
 */
@Singleton
public class PrincipalArgumentBinder implements TypedRequestArgumentBinder<Principal> {

    @Override
    public Argument<Principal> argumentType() {
        return Argument.of(Principal.class);
    }

    @Override
    public BindingResult<Principal> bind(ArgumentConversionContext<Principal> context, HttpRequest<?> source) {
        if (source.getAttributes().contains(OncePerRequestHttpServerFilter.getKey(SecurityFilter.class))) {
            MutableConvertibleValues<Object> attrs = source.getAttributes();
            Optional<Authentication> existing = attrs.get(SecurityFilter.AUTHENTICATION, Authentication.class);
            if (existing.isPresent()) {
                return () -> existing.map(e -> (Principal) e::getName);
            }
        }

        return ArgumentBinder.BindingResult.EMPTY;
    }
}
