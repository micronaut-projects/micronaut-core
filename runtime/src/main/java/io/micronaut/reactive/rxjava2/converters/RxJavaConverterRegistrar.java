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
package io.micronaut.reactive.rxjava2.converters;

import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.TypeConverterRegistrar;
import io.reactivex.*;
import org.reactivestreams.Publisher;

import javax.inject.Singleton;

/**
 * Converters for RxJava.
 *
 * @author graemerocher
 * @since 1.0
 */
@Singleton
@Requires(classes = Flowable.class)
@BootstrapContextCompatible
public class RxJavaConverterRegistrar implements TypeConverterRegistrar {

    @SuppressWarnings("unchecked")
    @Override
    public void register(ConversionService<?> conversionService) {

        // Completable
        conversionService.addConverter(Completable.class, Publisher.class, Completable::toFlowable);
        conversionService.addConverter(Completable.class, Single.class, completable -> completable.toSingleDefault(new Object()));
        conversionService.addConverter(Completable.class, Maybe.class, Completable::toMaybe);
        conversionService.addConverter(Completable.class, Observable.class, Completable::toObservable);
        conversionService.addConverter(Object.class, Completable.class, obj -> Completable.complete());

        // Maybe
        conversionService.addConverter(Maybe.class, Publisher.class, Maybe::toFlowable);
        conversionService.addConverter(Maybe.class, Single.class, Maybe::toSingle);
        conversionService.addConverter(Maybe.class, Observable.class, Maybe::toObservable);
        conversionService.addConverter(Maybe.class, Completable.class, Completable::fromMaybe);
        conversionService.addConverter(Object.class, Maybe.class, Maybe::just);

        // Observable
        conversionService.addConverter(Observable.class, Publisher.class, observable -> observable.toFlowable(BackpressureStrategy.BUFFER));
        conversionService.addConverter(Observable.class, Single.class, Observable::firstOrError);
        conversionService.addConverter(Observable.class, Maybe.class, Observable::firstElement);
        conversionService.addConverter(Observable.class, Completable.class, Completable::fromObservable);
        conversionService.addConverter(Object.class, Observable.class, o -> {
            if (o instanceof Iterable) {
                return Observable.fromIterable((Iterable) o);
            } else {
                return Observable.just(o);
            }
        });

        // Single
        conversionService.addConverter(Single.class, Publisher.class, Single::toFlowable);
        conversionService.addConverter(Single.class, Maybe.class, Single::toMaybe);
        conversionService.addConverter(Single.class, Observable.class, Single::toObservable);
        conversionService.addConverter(Single.class, Completable.class, Completable::fromSingle);
        conversionService.addConverter(Object.class, Single.class, Single::just);

        // Flowable
        conversionService.addConverter(Flowable.class, Single.class, Flowable::firstOrError);
        conversionService.addConverter(Flowable.class, Maybe.class, Flowable::firstElement);
        conversionService.addConverter(Flowable.class, Observable.class, Flowable::toObservable);
        conversionService.addConverter(Flowable.class, Completable.class, Completable::fromPublisher);
        conversionService.addConverter(Object.class, Flowable.class, o -> {
            if (o instanceof Iterable) {
                return Flowable.fromIterable((Iterable) o);
            } else {
                return Flowable.just(o);
            }
        });

        // Publisher
        conversionService.addConverter(
            Publisher.class, Flowable.class,
            publisher -> {
                if (publisher instanceof Flowable) {
                    return (Flowable) publisher;
                }
                return Flowable.fromPublisher(publisher);
            }
        );
        conversionService.addConverter(Publisher.class, Single.class, Single::fromPublisher);
        conversionService.addConverter(Publisher.class, Observable.class, Observable::fromPublisher);
        conversionService.addConverter(Publisher.class, Maybe.class, publisher -> Flowable.fromPublisher(publisher).firstElement());
        conversionService.addConverter(Publisher.class, Completable.class, Completable::fromPublisher);
    }
}
