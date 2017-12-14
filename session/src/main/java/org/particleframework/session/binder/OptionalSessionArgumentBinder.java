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
package org.particleframework.session.binder;

import org.particleframework.context.annotation.Requires;
import org.particleframework.core.convert.ArgumentConversionContext;
import org.particleframework.core.convert.value.MutableConvertibleValues;
import org.particleframework.core.type.Argument;
import org.particleframework.http.HttpRequest;
import org.particleframework.http.filter.OncePerRequestHttpServerFilter;
import org.particleframework.http.server.HttpServerConfiguration;
import org.particleframework.http.server.binding.binders.TypedRequestArgumentBinder;
import org.particleframework.session.Session;
import org.particleframework.session.http.HttpSessionFilter;

import javax.inject.Singleton;
import java.util.Optional;

/**
 * @author Graeme Rocher
 * @since 1.0
 */
@Singleton
@Requires(classes = HttpServerConfiguration.class)
public class OptionalSessionArgumentBinder implements TypedRequestArgumentBinder<Optional<Session>> {
    @SuppressWarnings("unchecked")
    @Override
    public Argument<Optional<Session>> argumentType() {
        Argument argument = Argument.of(Optional.class, Session.class);
        return argument;
    }

    @Override
    public BindingResult<Optional<Session>> bind(ArgumentConversionContext<Optional<Session>> context, HttpRequest<?> source) {
        if(!source.getAttributes().contains(OncePerRequestHttpServerFilter.getKey(HttpSessionFilter.class))) {
            // the filter hasn't been executed but the argument is not satisfied
            return BindingResult.UNSATISFIED;
        }

        MutableConvertibleValues<Object> attrs = source.getAttributes();
        Optional<Session> existing = attrs.get(HttpSessionFilter.SESSION_ATTRIBUTE, Session.class);
        if(existing.isPresent()) {
            return ()-> Optional.of(existing);
        }
        else {
            return ()-> Optional.empty();
        }
    }
}
