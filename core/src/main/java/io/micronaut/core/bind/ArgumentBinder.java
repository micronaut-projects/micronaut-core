/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.core.bind;

import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionError;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * <p>An interface capable of binding the value of an {@link io.micronaut.core.type.Argument} from a source</p>.
 * <p>
 * <p>The selection of an {@link ArgumentBinder} is done by the {@link ArgumentBinderRegistry}. Selection could
 * be based on type, annotation or other factors such as the request media type</p>
 * <p>
 * <p>Unlike {@link io.micronaut.core.convert.TypeConverter} instances binders can potentially handle complex
 * objects and typically work on conjunction with a {@link io.micronaut.core.convert.value.ConvertibleValues} instance</p>
 * <p>
 * <p>An {@link ArgumentBinder} can be registered as a bean. In this case it will be globally available at all times.
 *
 * @param <T> The argument type
 * @param <S> The source type
 * @author Graeme Rocher
 * @see io.micronaut.core.convert.TypeConverter
 * @see io.micronaut.core.convert.value.ConvertibleValues
 * @since 1.0
 */
@FunctionalInterface
public interface ArgumentBinder<T, S> {

    /**
     * Bind the given argument from the given source.
     *
     * @param context The {@link ArgumentConversionContext}
     * @param source  The source
     * @return An {@link Optional} of the value. If no binding was possible {@link Optional#empty()}
     */
    BindingResult<T> bind(ArgumentConversionContext<T> context, S source);

    /**
     * The result of binding.
     *
     * @param <T>
     */
    interface BindingResult<T> {
        /**
         * An empty but satisfied result.
         */
        BindingResult EMPTY = Optional::empty;

        /**
         * An empty but unsatisfied result.
         */
        BindingResult UNSATISFIED = new BindingResult() {
            @Override
            public Optional getValue() {
                return Optional.empty();
            }

            @Override
            public boolean isSatisfied() {
                return false;
            }
        };

        /**
         * @return The bound value
         */
        Optional<T> getValue();

        /**
         * @return The {@link ConversionError} instances that occurred
         */
        default List<ConversionError> getConversionErrors() {
            return Collections.emptyList();
        }

        /**
         * @return Was the binding requirement satisfied
         */
        default boolean isSatisfied() {
            return getConversionErrors() == Collections.EMPTY_LIST;
        }

        /**
         * @return Is the value present and satisfied
         */
        default boolean isPresentAndSatisfied() {
            return isSatisfied() && getValue().isPresent();
        }

        /**
         * Obtains the value. Callers should call {@link #isPresentAndSatisfied()} first.
         *
         * @return The value
         */
        default T get() {
            return getValue().get();
        }
    }
}
