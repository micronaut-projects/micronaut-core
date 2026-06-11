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

/**
 * Unsafe bean property interface adds write methods which don't validate the input/output.
 * It's the responsibility of the caller to validate the value.
 * <p>
 * Primitive unsafe write methods are part of the generated-introspection dispatch contract.
 * Their default implementations keep custom {@link UnsafeBeanWriteProperty} implementations
 * compatible by boxing the primitive value and delegating to {@link #setUnsafe(Object, Object)}.
 * Generated Micronaut bean properties override these methods and route them through primitive
 * dispatch methods so hot paths can write primitive values without allocating boxed wrappers.
 *
 * @param <B> The bean type
 * @param <T> The bean property type
 * @author Denis Stepanov
 * @since 4.4.0
 */
@Experimental
public interface UnsafeBeanWriteProperty<B, T> extends BeanWriteProperty<B, T> {

    /**
     * Unsafe version of {@link #withValue(Object, Object)}.
     *
     * @param bean  The bean
     * @param value The new value
     * @return Either the existing instance or the property is mutable or a newly created instance via the copy constructor pattern.
     */
    B withValueUnsafe(B bean, @Nullable T value);

    /**
     * Unsafe version of {@link #set(Object, Object)}.
     *
     * @param bean  The bean
     * @param value The value to write
     */
    void setUnsafe(B bean, @Nullable T value);

    /**
     * Unsafe primitive version of {@link #set(Object, Object)} for {@code boolean} values.
     * <p>
     * The default implementation boxes and delegates to {@link #setUnsafe(Object, Object)}.
     * Generated introspections override this method when a primitive dispatch target is available.
     *
     * @param bean The bean
     * @param value The primitive value
     * @since 5.1.0
     */
    @SuppressWarnings("unchecked")
    default void setBooleanUnsafe(B bean, boolean value) {
        setUnsafe(bean, (T) Boolean.valueOf(value));
    }

    /**
     * Unsafe primitive version of {@link #set(Object, Object)} for {@code byte} values.
     * <p>
     * The default implementation boxes and delegates to {@link #setUnsafe(Object, Object)}.
     * Generated introspections override this method when a primitive dispatch target is available.
     *
     * @param bean The bean
     * @param value The primitive value
     * @since 5.1.0
     */
    @SuppressWarnings("unchecked")
    default void setByteUnsafe(B bean, byte value) {
        setUnsafe(bean, (T) Byte.valueOf(value));
    }

    /**
     * Unsafe primitive version of {@link #set(Object, Object)} for {@code short} values.
     * <p>
     * The default implementation boxes and delegates to {@link #setUnsafe(Object, Object)}.
     * Generated introspections override this method when a primitive dispatch target is available.
     *
     * @param bean The bean
     * @param value The primitive value
     * @since 5.1.0
     */
    @SuppressWarnings("unchecked")
    default void setShortUnsafe(B bean, short value) {
        setUnsafe(bean, (T) Short.valueOf(value));
    }

    /**
     * Unsafe primitive version of {@link #set(Object, Object)} for {@code char} values.
     * <p>
     * The default implementation boxes and delegates to {@link #setUnsafe(Object, Object)}.
     * Generated introspections override this method when a primitive dispatch target is available.
     *
     * @param bean The bean
     * @param value The primitive value
     * @since 5.1.0
     */
    @SuppressWarnings("unchecked")
    default void setCharUnsafe(B bean, char value) {
        setUnsafe(bean, (T) Character.valueOf(value));
    }

    /**
     * Unsafe primitive version of {@link #set(Object, Object)} for {@code int} values.
     * <p>
     * The default implementation boxes and delegates to {@link #setUnsafe(Object, Object)}.
     * Generated introspections override this method when a primitive dispatch target is available.
     *
     * @param bean The bean
     * @param value The primitive value
     * @since 5.1.0
     */
    @SuppressWarnings("unchecked")
    default void setIntUnsafe(B bean, int value) {
        setUnsafe(bean, (T) Integer.valueOf(value));
    }

    /**
     * Unsafe primitive version of {@link #set(Object, Object)} for {@code long} values.
     * <p>
     * The default implementation boxes and delegates to {@link #setUnsafe(Object, Object)}.
     * Generated introspections override this method when a primitive dispatch target is available.
     *
     * @param bean The bean
     * @param value The primitive value
     * @since 5.1.0
     */
    @SuppressWarnings("unchecked")
    default void setLongUnsafe(B bean, long value) {
        setUnsafe(bean, (T) Long.valueOf(value));
    }

    /**
     * Unsafe primitive version of {@link #set(Object, Object)} for {@code float} values.
     * <p>
     * The default implementation boxes and delegates to {@link #setUnsafe(Object, Object)}.
     * Generated introspections override this method when a primitive dispatch target is available.
     *
     * @param bean The bean
     * @param value The primitive value
     * @since 5.1.0
     */
    @SuppressWarnings("unchecked")
    default void setFloatUnsafe(B bean, float value) {
        setUnsafe(bean, (T) Float.valueOf(value));
    }

    /**
     * Unsafe primitive version of {@link #set(Object, Object)} for {@code double} values.
     * <p>
     * The default implementation boxes and delegates to {@link #setUnsafe(Object, Object)}.
     * Generated introspections override this method when a primitive dispatch target is available.
     *
     * @param bean The bean
     * @param value The primitive value
     * @since 5.1.0
     */
    @SuppressWarnings("unchecked")
    default void setDoubleUnsafe(B bean, double value) {
        setUnsafe(bean, (T) Double.valueOf(value));
    }

}
