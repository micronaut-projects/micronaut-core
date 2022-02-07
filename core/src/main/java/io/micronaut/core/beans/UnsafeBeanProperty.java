package io.micronaut.core.beans;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;

/**
 * Unsafe bean property interface adds read/write methods which don't validate the input/output.
 * It's the responsibility of the caller to validate the value.
 *
 * @param <B> The bean type
 * @param <T> The bean property type
 * @author Denis Stepanov
 * @since 3.3.1
 */
@Experimental
public interface UnsafeBeanProperty<B, T> extends BeanProperty<B, T> {

    /**
     * Unsafe version of {@link #get(Object)}.
     *
     * @param bean The bean to read from
     * @return The value
     */
    T getUnsafe(@NonNull B bean);

    /**
     * Unsafe version of {@link #withValue(Object, Object)}.
     *
     * @param bean  The bean
     * @param value The new value
     * @return Either the existing instance or the property is mutable or a newly created instance via the copy constructor pattern.
     * @since 2.3.0
     */
    @NonNull
    B withValueUnsafe(@NonNull B bean, @Nullable T value);

    /**
     * Unsafe version of {@link #set(Object, Object)}.
     *
     * @param bean  The bean
     * @param value The value to write
     */
    void setUnsafe(@NonNull B bean, @Nullable T value);

}
