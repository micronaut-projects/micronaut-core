/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.core.beans;

import io.micronaut.core.annotation.Experimental;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * Unsafe bean property interface adds read methods which don't validate the input/output.
 * It's the responsibility of the caller to validate the value.
 * <p>
 * Primitive unsafe read methods are part of the generated-introspection dispatch contract.
 * Their default implementations keep custom {@link UnsafeBeanReadProperty} implementations
 * compatible by delegating to {@link #getUnsafe(Object)} and unboxing the result. Generated
 * Micronaut bean properties override these methods and route them through primitive dispatch
 * methods so hot paths can read primitive values without allocating boxed wrappers.
 *
 * @param <B> The bean type
 * @param <T> The bean property type
 * @author Denis Stepanov
 * @since 4.4.0
 */
@Experimental
public interface UnsafeBeanReadProperty<B, T> extends BeanReadProperty<B, T> {

    /**
     * Unsafe version of {@link #get(Object)}.
     *
     * @param bean The bean to read from
     * @return The value
     */
    @Nullable T getUnsafe(B bean);

    /**
     * Unsafe primitive version of {@link #get(Object)} for {@code boolean} values.
     * <p>
     * The default implementation boxes through {@link #getUnsafe(Object)}. Generated
     * introspections override this method when a primitive dispatch target is available.
     *
     * @param bean The bean
     * @return The primitive value
     * @since 5.1.0
     */
    default boolean getBooleanUnsafe(B bean) {
        return Objects.requireNonNull((Boolean) getUnsafe(bean));
    }

    /**
     * Unsafe primitive version of {@link #get(Object)} for {@code byte} values.
     * <p>
     * The default implementation boxes through {@link #getUnsafe(Object)}. Generated
     * introspections override this method when a primitive dispatch target is available.
     *
     * @param bean The bean
     * @return The primitive value
     * @since 5.1.0
     */
    default byte getByteUnsafe(B bean) {
        return Objects.requireNonNull((Byte) getUnsafe(bean));
    }

    /**
     * Unsafe primitive version of {@link #get(Object)} for {@code short} values.
     * <p>
     * The default implementation boxes through {@link #getUnsafe(Object)}. Generated
     * introspections override this method when a primitive dispatch target is available.
     *
     * @param bean The bean
     * @return The primitive value
     * @since 5.1.0
     */
    default short getShortUnsafe(B bean) {
        return Objects.requireNonNull((Short) getUnsafe(bean));
    }

    /**
     * Unsafe primitive version of {@link #get(Object)} for {@code char} values.
     * <p>
     * The default implementation boxes through {@link #getUnsafe(Object)}. Generated
     * introspections override this method when a primitive dispatch target is available.
     *
     * @param bean The bean
     * @return The primitive value
     * @since 5.1.0
     */
    default char getCharUnsafe(B bean) {
        return Objects.requireNonNull((Character) getUnsafe(bean));
    }

    /**
     * Unsafe primitive version of {@link #get(Object)} for {@code int} values.
     * <p>
     * The default implementation boxes through {@link #getUnsafe(Object)}. Generated
     * introspections override this method when a primitive dispatch target is available.
     *
     * @param bean The bean
     * @return The primitive value
     * @since 5.1.0
     */
    default int getIntUnsafe(B bean) {
        return Objects.requireNonNull((Integer) getUnsafe(bean));
    }

    /**
     * Unsafe primitive version of {@link #get(Object)} for {@code long} values.
     * <p>
     * The default implementation boxes through {@link #getUnsafe(Object)}. Generated
     * introspections override this method when a primitive dispatch target is available.
     *
     * @param bean The bean
     * @return The primitive value
     * @since 5.1.0
     */
    default long getLongUnsafe(B bean) {
        return Objects.requireNonNull((Long) getUnsafe(bean));
    }

    /**
     * Unsafe primitive version of {@link #get(Object)} for {@code float} values.
     * <p>
     * The default implementation boxes through {@link #getUnsafe(Object)}. Generated
     * introspections override this method when a primitive dispatch target is available.
     *
     * @param bean The bean
     * @return The primitive value
     * @since 5.1.0
     */
    default float getFloatUnsafe(B bean) {
        return Objects.requireNonNull((Float) getUnsafe(bean));
    }

    /**
     * Unsafe primitive version of {@link #get(Object)} for {@code double} values.
     * <p>
     * The default implementation boxes through {@link #getUnsafe(Object)}. Generated
     * introspections override this method when a primitive dispatch target is available.
     *
     * @param bean The bean
     * @return The primitive value
     * @since 5.1.0
     */
    default double getDoubleUnsafe(B bean) {
        return Objects.requireNonNull((Double) getUnsafe(bean));
    }

}
