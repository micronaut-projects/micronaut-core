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
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;

/**
 * Unsafe bean property interface adds write methods which don't validate the input/output.
 * It's the responsibility of the caller to validate the value.
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
