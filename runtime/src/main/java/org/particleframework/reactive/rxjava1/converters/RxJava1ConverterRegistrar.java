/*
 * Copyright 2018 original authors
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
package org.particleframework.reactive.rxjava1.converters;

import hu.akarnokd.rxjava.interop.RxJavaInterop;
import io.reactivex.BackpressureStrategy;
import org.particleframework.context.annotation.Requires;
import org.particleframework.core.convert.ConversionService;
import org.particleframework.core.convert.TypeConverterRegistrar;
import org.reactivestreams.Publisher;
import rx.Observable;
import rx.Single;

import javax.inject.Singleton;
import java.util.function.Function;


/**
 * Allows conversion between RxJava 1.x types and 2.x
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

        conversionService.addConverter(Observable.class, io.reactivex.Observable.class, RxJavaInterop::toV2Observable);

        conversionService.addConverter(Publisher.class, Observable.class, RxJavaInterop::toV1Observable);

        conversionService.addConverter(io.reactivex.Observable.class, Single.class, observable ->
                RxJavaInterop.toV1Observable(observable, BackpressureStrategy.BUFFER).toSingle()
        );

        // Single
        conversionService.addConverter(Single.class, Publisher.class,  single ->
                RxJavaInterop.toV2Single(single).toFlowable()
        );

        conversionService.addConverter(Single.class, io.reactivex.Single.class, RxJavaInterop::toV2Single);

        conversionService.addConverter(io.reactivex.Single.class, Single.class, RxJavaInterop::toV1Single);

        conversionService.addConverter(Publisher.class, Single.class, publisher ->
                RxJavaInterop.toV1Observable(publisher).toSingle()
        );
    }
}
