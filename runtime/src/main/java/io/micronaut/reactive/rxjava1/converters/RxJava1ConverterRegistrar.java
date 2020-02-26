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
package io.micronaut.reactive.rxjava1.converters;

import hu.akarnokd.rxjava.interop.RxJavaInterop;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.TypeConverterRegistrar;
import io.reactivex.BackpressureStrategy;
import org.reactivestreams.Publisher;
import rx.Observable;
import rx.Single;

import javax.inject.Singleton;

/**
 * Allows conversion between RxJava 1.x types and 2.x.
 *
 * @author graemerocher
 * @since 1.0
 */
@Requires(classes = {RxJavaInterop.class, Observable.class})
@Singleton
public class RxJava1ConverterRegistrar implements TypeConverterRegistrar {

    @SuppressWarnings("unchecked")
    @Override
    public void register(ConversionService<?> conversionService) {

        // Observable
        conversionService.addConverter(Observable.class, Publisher.class, RxJavaInterop::toV2Flowable);
        conversionService.addConverter(Observable.class, io.reactivex.Single.class, observable -> RxJavaInterop.toV2Single(observable.toSingle()));
        conversionService.addConverter(Observable.class, io.reactivex.Maybe.class, observable -> RxJavaInterop.toV2Observable(observable).firstElement());
        conversionService.addConverter(Observable.class, io.reactivex.Observable.class, RxJavaInterop::toV2Observable);
        conversionService.addConverter(Publisher.class, Observable.class, RxJavaInterop::toV1Observable);
        conversionService.addConverter(io.reactivex.Observable.class, Single.class, observable ->
            RxJavaInterop.toV1Observable(observable, BackpressureStrategy.BUFFER).toSingle()
        );
        conversionService.addConverter(io.reactivex.Observable.class, Observable.class, observable ->
            RxJavaInterop.toV1Observable(observable, BackpressureStrategy.BUFFER)
        );

        // Single
        conversionService.addConverter(Single.class, Publisher.class, single ->
            RxJavaInterop.toV2Single(single).toFlowable()
        );
        conversionService.addConverter(Single.class, io.reactivex.Single.class, RxJavaInterop::toV2Single);
        conversionService.addConverter(io.reactivex.Single.class, Single.class, RxJavaInterop::toV1Single);
        conversionService.addConverter(io.reactivex.Single.class, Observable.class, single ->
            RxJavaInterop.toV1Single(single).toObservable()
        );
        conversionService.addConverter(Publisher.class, Single.class, publisher ->
            RxJavaInterop.toV1Observable(publisher).toSingle()
        );

        // Maybe
        conversionService.addConverter(io.reactivex.Maybe.class, Observable.class, single ->
            RxJavaInterop.toV1Observable(single.toObservable(), BackpressureStrategy.BUFFER)
        );
        conversionService.addConverter(io.reactivex.Maybe.class, Single.class, RxJavaInterop::toV1Single);
    }
}
