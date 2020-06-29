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
package io.micronaut.http.server.netty.binders;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.bind.binders.DefaultBodyAnnotationBinder;
import io.micronaut.http.bind.binders.NonBlockingBodyArgumentBinder;
import io.micronaut.http.server.netty.HttpContentProcessorResolver;
import io.reactivex.Maybe;
import io.reactivex.MaybeSource;
import io.reactivex.Single;
import org.reactivestreams.Publisher;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Bindings {@link io.micronaut.http.annotation.Body} arguments of type {@link Maybe}.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
public class MaybeBodyBinder extends DefaultBodyAnnotationBinder<Maybe> implements NonBlockingBodyArgumentBinder<Maybe> {

    public static final Argument<Maybe> TYPE = Argument.of(Maybe.class);

    private PublisherBodyBinder publisherBodyBinder;

    /**
     * @param conversionService            The conversion service
     * @param httpContentProcessorResolver The http content processor resolver
     */
    public MaybeBodyBinder(ConversionService conversionService,
                           HttpContentProcessorResolver httpContentProcessorResolver) {
        super(conversionService);
        this.publisherBodyBinder = new PublisherBodyBinder(conversionService, httpContentProcessorResolver);
    }

    @NonNull
    @Override
    public List<Class<?>> superTypes() {
        return Collections.singletonList(MaybeSource.class);
    }

    @Override
    public Argument<Maybe> argumentType() {
        return TYPE;
    }

    @SuppressWarnings("unchecked")
    @Override
    public BindingResult<Maybe> bind(ArgumentConversionContext<Maybe> context, HttpRequest<?> source) {
        Collection<Argument<?>> typeVariables = context.getArgument().getTypeVariables().values();

        BindingResult<Publisher> result = publisherBodyBinder.bind(
                ConversionContext.of(Argument.of(Publisher.class, typeVariables.toArray(Argument.ZERO_ARGUMENTS))),
                source
        );
        if (result.isPresentAndSatisfied()) {
            return () -> Optional.of(Single.fromPublisher(result.get()).toMaybe());
        }
        return () -> Optional.of(Maybe.empty());
    }
}
