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
package io.micronaut.core.beans;

import io.micronaut.core.annotation.AnnotationMetadataProvider;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.ArgumentCoercible;
import io.micronaut.core.naming.Named;
import org.jspecify.annotations.Nullable;

import java.lang.annotation.ElementType;

/**
 * A single member (a field, a getter or a setter) that contributed to a {@link BeanProperty}.
 *
 * <p>A {@link BeanProperty} merges the field, the read method and the write method of a property into a single
 * element with a single merged {@link io.micronaut.core.annotation.AnnotationMetadata}. Some specifications, most
 * notably Jakarta Bean Validation, treat those members as distinct elements: a constraint declared on the field is
 * validated against the value the field holds, while a constraint declared on the getter is validated against the
 * value the getter returns, and the two can differ.</p>
 *
 * <p>The members of a property are only available if the introspection was generated with
 * {@link io.micronaut.core.annotation.Introspected#members()} set to {@code true}, otherwise
 * {@link BeanProperty#getMembers()} is empty.</p>
 *
 * @param <B> The bean type
 * @param <T> The member type
 * @author Denis Stepanov
 * @since 5.2.0
 * @see BeanProperty#getMembers()
 */
@Experimental
public interface BeanPropertyMember<B, T> extends AnnotationMetadataProvider, ArgumentCoercible<T>, Named {

    /**
     * The name of the member. For a field this is the field name, for a method this is the method name.
     *
     * @return The member name
     */
    @Override
    String getName();

    /**
     * The kind of the member, either {@link ElementType#FIELD} or {@link ElementType#METHOD}.
     *
     * @return The element type
     */
    ElementType getElementType();

    /**
     * The type that declares this member. Unlike {@link BeanProperty#getDeclaringType()} this is the type the member
     * is actually declared on, which can be a supertype of the introspected bean type.
     *
     * @return The declaring type
     */
    Class<?> getDeclaringType();

    /**
     * The type of the member as an argument, including any generic type information and type annotations. For a field
     * this is the field type, for a getter the return type and for a setter the type of the single parameter.
     *
     * @return The argument
     */
    @Override
    Argument<T> asArgument();

    /**
     * @return The type of the member
     */
    default Class<T> getType() {
        return asArgument().getType();
    }

    /**
     * Whether the value of this member can be read with {@link #read(Object)}. Fields and getters are readable,
     * setters are not.
     *
     * @return True if the member can be read
     */
    boolean isReadable();

    /**
     * Read the value held by this member. For a field this reads the field directly, bypassing any getter.
     *
     * @param bean The bean to read from
     * @return The value
     * @throws UnsupportedOperationException If the member is not {@link #isReadable() readable}
     * @throws IllegalArgumentException If the bean instance is not of the correct type
     */
    @Nullable
    T read(B bean);
}
