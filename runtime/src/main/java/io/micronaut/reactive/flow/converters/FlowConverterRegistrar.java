/*
 * Copyright 2017-2019 original authors
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
package io.micronaut.reactive.flow.converters;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.TypeConverterRegistrar;
import io.reactivex.Flowable;
import kotlinx.coroutines.flow.Flow;
import kotlinx.coroutines.reactive.ReactiveFlowKt;

import javax.inject.Singleton;

/**
 * Converts between a {@link Flow} and a {@link Flowable}.
 *
 * @author Konrad Kamiński
 * @since 1.3
 */
@Singleton
@Requires(classes = Flow.class)
public class FlowConverterRegistrar implements TypeConverterRegistrar {
    @Override
    public void register(ConversionService<?> conversionService) {
        // Flow
        conversionService.addConverter(Flow.class, Flowable.class,
                flow -> Flowable.fromPublisher(ReactiveFlowKt.asPublisher(flow)));
        conversionService.addConverter(Flowable.class, Flow.class, ReactiveFlowKt::asFlow);
    }
}
