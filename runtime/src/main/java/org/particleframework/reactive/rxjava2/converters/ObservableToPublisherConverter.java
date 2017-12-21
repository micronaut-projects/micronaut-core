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
package org.particleframework.reactive.rxjava2.converters;

import io.reactivex.BackpressureStrategy;
import io.reactivex.Observable;
import org.particleframework.core.convert.ConversionContext;
import org.particleframework.core.convert.TypeConverter;
import org.reactivestreams.Publisher;

import javax.inject.Singleton;
import java.util.Optional;

/**
 * Defaults conversion strategy for {@link Observable} to {@link Publisher}. By default a buffering back pressure strategy is used
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Singleton
public class ObservableToPublisherConverter implements TypeConverter<Observable, Publisher> {
    @Override
    public Optional<Publisher> convert(Observable object, Class<Publisher> targetType, ConversionContext context) {
        return Optional.of(
                object.toFlowable(BackpressureStrategy.BUFFER)
        );
    }
}
