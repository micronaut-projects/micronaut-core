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

package io.micronaut.aop;

import io.micronaut.core.annotation.AnnotationMetadataDelegate;
import io.micronaut.core.attr.MutableAttributeHolder;
import io.micronaut.core.type.ArgumentValue;
import io.micronaut.core.type.Executable;
import io.micronaut.core.type.MutableArgumentValue;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * <p>An InvocationContext passed to one or many {@link Interceptor} instances. Attributes can be stored within the
 * context and shared between multiple {@link Interceptor} implementations. The {@link #proceed()} method should be
 * called to proceed to the next {@link Interceptor} with the last interceptor in the chain being the original
 * decorated method implementation.</p>
 * <p>
 * <p>The parameters to pass to the next {@link Interceptor} can be mutated using {@link MutableArgumentValue}
 * interface returned by the {@link #getParameters()} method</p>
 *
 * @param <T> The declaring type
 * @param <R> The result of the method call
 * @author Graeme Rocher
 * @since 1.0
 */
public interface InvocationContext<T, R> extends Executable<T, R>, AnnotationMetadataDelegate, MutableAttributeHolder {

    /**
     * @return The bound {@link ArgumentValue} instances
     */
    Map<String, MutableArgumentValue<?>> getParameters();

    /**
     * @return The target object
     */
    T getTarget();

    /**
     * Proceeds with the invocation. If this is the last interceptor in the chain then the final implementation method is invoked
     *
     * @return The return value of the method
     * @throws RuntimeException chain may throw RTE
     */
    R proceed() throws RuntimeException;

    /**
     * Proceeds with the invocation using the given interceptor as a position to start from. Mainly useful for {@link Introduction} advise where you want to
     * invoke the target multiple times or where you want to repeat the entire chain.
     *
     * @param from The interceptor to start from (note: will not be included in the execution)
     * @return The return value of the method
     * @throws RuntimeException chain may throw RTE
     */
    R proceed(Interceptor from) throws RuntimeException;

    @SuppressWarnings("unchecked")
    @Override
    default InvocationContext<T, R> setAttribute(CharSequence name, Object value) {
        return (InvocationContext<T, R>) MutableAttributeHolder.super.setAttribute(name, value);
    }

    /**
     * Returns the current state of the parameters as an array by parameter index. Note that mutations to the array have no effect.
     * If you wish to mutate the parameters use {@link #getParameters()} and the {@link MutableArgumentValue} interface instead
     *
     * @return The bound {@link ArgumentValue} instances
     */
    default Object[] getParameterValues() {
        return getParameters()
            .values()
            .stream()
            .map(ArgumentValue::getValue)
            .toArray();
    }

    /**
     * Returns the current state of the parameters as an array by parameter index. Note that mutations to the array have no effect.
     * If you wish to mutate the parameters use {@link #getParameters()} and the {@link MutableArgumentValue} interface instead
     *
     * @return The bound {@link ArgumentValue} instances
     */
    default Map<String, Object> getParameterValueMap() {
        Map<String, MutableArgumentValue<?>> parameters = getParameters();
        Map<String, Object> valueMap = new LinkedHashMap<>(parameters.size());
        for (Map.Entry<String, MutableArgumentValue<?>> entry : parameters.entrySet()) {
            MutableArgumentValue<?> value = entry.getValue();
            String key = entry.getKey();
            valueMap.put(key, value.getValue());
        }
        return valueMap;
    }
}
