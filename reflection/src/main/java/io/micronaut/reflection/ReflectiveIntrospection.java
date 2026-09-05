/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.reflection;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.beans.BeanConstructor;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanMethod;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.core.beans.BeanPropertyMember;

import java.lang.reflect.AnnotatedElement;
import java.util.List;
import java.util.Optional;

/**
 * What a reflective introspection knows beyond the {@link BeanIntrospection} contract: every constructor of
 * the type, and the members a property is made of, each with the class declaring it and its own metadata.
 *
 * <p>A specification that describes a type — the constraint metadata of Jakarta Validation, for instance —
 * names constructors by their parameter types and distinguishes the constraints declared on a field from the
 * ones declared on a getter, or in the type from the ones inherited from a super type. A generated
 * introspection merges the members of a property into one metadata; this contract keeps them apart.</p>
 *
 * @param <T> The bean type
 * @author Denis Stepanov
 * @since 5.2.0
 */
@Experimental
public interface ReflectiveIntrospection<T> extends BeanIntrospection<T> {

    /**
     * @return Every constructor of the type, {@link #getConstructor()} first
     */
    @Override
    List<BeanConstructor<T>> getConstructors();

    /**
     * Finds the method the introspected type itself declares, with the annotations of that declaration only:
     * the generated metadata of a method merges the annotations of the methods it overrides.
     *
     * @param name           The method name
     * @param parameterTypes The parameter types
     * @return The method declared by the type, empty when the type inherits it
     */
    Optional<BeanMethod<T, Object>> findDeclaredMethod(String name, Class<?>... parameterTypes);

    /**
     * A member of a property, with the field or method reflection read it from.
     *
     * <p>{@link BeanProperty#getMembers()} reports these for every reflective property: a member is described
     * only when it is asked for, so a description that never asks for one pays nothing for them.</p>
     *
     * @param <B> The bean type
     */
    @Experimental
    interface ReflectivePropertyMember<B> extends BeanPropertyMember<B, Object> {

        /**
         * @return The field or method the member was read from
         */
        AnnotatedElement getMember();
    }
}
