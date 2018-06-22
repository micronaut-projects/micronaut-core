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

package io.micronaut.reactive.rxjava2.converters;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.TypeConverterRegistrar;
import io.reactivex.BackpressureStrategy;
import io.reactivex.Flowable;
import io.reactivex.Maybe;
import io.reactivex.Observable;
import io.reactivex.Single;
import org.reactivestreams.Publisher;

import javax.inject.Singleton;
import java.util.function.Function;

/**
 * Converters for RxJava.
 *
 * @author graemerocher
 * @since 1.0
 */
@Singleton
@Requires(classes = Flowable.class)
public class RxJavaConverterRegistrar implements TypeConverterRegistrar {

    @SuppressWarnings("unchecked")
    @Override
    public void register(ConversionService<?> conversionService) {

        // Maybe
        conversionService.addConverter(Maybe.class, Publisher.class, (Function<Maybe, Publisher>) Maybe::toFlowable);
        conversionService.addConverter(Maybe.class, Single.class, (Function<Maybe, Single>) Maybe::toSingle);
        conversionService.addConverter(Maybe.class, Observable.class, (Function<Maybe, Observable>) Maybe::toObservable);
        conversionService.addConverter(Object.class, Maybe.class, (Function<Object, Maybe>) Maybe::just);

        // Observable
        conversionService.addConverter(Observable.class, Publisher.class, (Function<Observable, Publisher>) observable -> observable.toFlowable(BackpressureStrategy.BUFFER));
        conversionService.addConverter(Observable.class, Single.class, (Function<Observable, Single>) Observable::firstOrError);
        conversionService.addConverter(Observable.class, Maybe.class, (Function<Observable, Maybe>) Observable::firstElement);
        conversionService.addConverter(Object.class, Observable.class, (Function<Object, Observable>) o -> {
            if (o instanceof Iterable) {
                return Observable.fromIterable((Iterable) o);
            } else {
                return Observable.just(o);
            }
        });

        // Single
        conversionService.addConverter(Single.class, Publisher.class, (Function<Single, Publisher>) Single::toFlowable);
        conversionService.addConverter(Single.class, Maybe.class, (Function<Single, Maybe>) Single::toMaybe);
        conversionService.addConverter(Single.class, Observable.class, (Function<Single, Observable>) Single::toObservable);
        conversionService.addConverter(Object.class, Single.class, (Function<Object, Single>) Single::just);

        // Flowable
        conversionService.addConverter(Flowable.class, Single.class, (Function<Flowable, Single>) Flowable::firstOrError);
        conversionService.addConverter(Flowable.class, Maybe.class, (Function<Flowable, Maybe>) Flowable::firstElement);
        conversionService.addConverter(Flowable.class, Observable.class, (Function<Flowable, Observable>) Flowable::toObservable);
        conversionService.addConverter(Object.class, Flowable.class, (Function<Object, Flowable>) o -> {
            if (o instanceof Iterable) {
                return Flowable.fromIterable((Iterable) o);
            } else {
                return Flowable.just(o);
            }
        });

        // Publisher
        conversionService.addConverter(
            Publisher.class, Flowable.class,
            (Function<Publisher, Flowable>) publisher -> {
                if (publisher instanceof Flowable) {
                    return (Flowable) publisher;
                }
                return Flowable.fromPublisher(publisher);
            }
        );
        conversionService.addConverter(Publisher.class, Single.class, (Function<Publisher, Single>) Single::fromPublisher);
        conversionService.addConverter(Publisher.class, Observable.class, (Function<Publisher, Observable>) Observable::fromPublisher);
        conversionService.addConverter(Publisher.class, Maybe.class, (Function<Publisher, Maybe>) publisher -> Maybe.fromSingle(Single.fromPublisher(publisher)));
    }
}
