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
package org.particleframework.bind;

import org.particleframework.inject.Argument;

import java.util.Optional;

/**
 * <p>An interface capable of binding the value of an {@link Argument} from a source</p>
 *
 * <p>The selection of an {@link ArgumentBinder} is done by the {@link ArgumentBinderRegistry}. Selection could
 * be based on type, annotation or other factors such as the request media type</p>
 *
 * <p>Unlike {@link org.particleframework.core.convert.TypeConverter} instances binders can potentially handle complex
 *  objects and typically work on conjunction with a {@link org.particleframework.core.convert.ConvertibleValues} instance</p>
 *
 * <p>An {@link ArgumentBinder} can either be registered as a bean or by META-INF/services. In the case of the latter
 * it will be globally available at all times, whilst the former is only present when a {@link org.particleframework.context.BeanContext} is initialized</p>
 *
 * @see org.particleframework.core.convert.TypeConverter
 * @see org.particleframework.core.convert.ConvertibleValues
 *
 * @param <T> The argument type
 * @param <S> The source type
 * @author Graeme Rocher
 * @since 1.0
 */
@FunctionalInterface
public interface ArgumentBinder<T, S> {

    /**
     * Bind the given argument from the given source
     *
     * @param argument The argument
     * @param source The source
     * @return An {@link Optional} of the value. If no binding was possible {@link Optional#empty()}
     */
    Optional<T> bind(Argument<T> argument, S source);
}
