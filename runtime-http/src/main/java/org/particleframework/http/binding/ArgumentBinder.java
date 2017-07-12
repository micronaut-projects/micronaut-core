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
package org.particleframework.http.binding;

import org.particleframework.inject.Argument;

import java.util.Optional;

/**
 * An interface capable of binding the value of an {@link Argument} to a source
 *
 * @param <T> The argument type
 * @param <S> The source type
 * @author Graeme Rocher
 * @since 1.0
 */
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
