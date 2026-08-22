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
package io.micronaut.inject.reflection;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.beans.BeanConstructor;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanMethod;
import io.micronaut.core.type.Argument;
import org.jspecify.annotations.Nullable;

import java.lang.annotation.ElementType;
import java.lang.reflect.Method;
import java.lang.reflect.Field;
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
 * @since 5.1
 */
@Experimental
public interface ReflectiveIntrospection<T> extends BeanIntrospection<T> {

    /**
     * @return Every constructor of the type, {@link #getConstructor()} first
     */
    List<BeanConstructor<T>> getConstructors();

    /**
     * @param propertyName The property name
     * @return The members of the property, the most specific first: the fields declaring it in the type
     * and its super classes, its getters and its setters, each with its own metadata
     */
    List<PropertyMember> getPropertyMembers(String propertyName);

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
     * A member of a property.
     *
     * @param elementType        {@link ElementType#FIELD} for a field, {@link ElementType#METHOD} for a getter or a setter
     * @param declaringType      The class or interface declaring the member
     * @param annotationMetadata The metadata of the member, the type-use annotations of its type included
     * @param argument           The type of the member as it declares it — an interface getter can declare
     *                           {@code Iterable<@NotNull String>} where the implementation returns a sub type —
     *                           with the type-use annotations of its type arguments
     * @param member             The field or method
     */
    record PropertyMember(ElementType elementType,
                          Class<?> declaringType,
                          AnnotationMetadata annotationMetadata,
                          Argument<?> argument,
                          AnnotatedElement member) {

        /**
         * @return Whether the member yields the property value: a field or a getter, not a setter
         */
        public boolean isReadable() {
            return member instanceof Field || member instanceof Method method && method.getParameterCount() == 0;
        }

        /**
         * Reads the value of the member on a bean: the field or the getter, the way the constraints declared
         * on each are validated against what that member holds.
         *
         * @param bean The bean
         * @return The value
         * @throws IllegalStateException When the member is not readable or fails
         */
        @Nullable
        public Object read(Object bean) {
            try {
                if (member instanceof Field field) {
                    field.trySetAccessible();
                    return field.get(bean);
                }
                if (member instanceof Method method && method.getParameterCount() == 0) {
                    method.trySetAccessible();
                    return method.invoke(bean);
                }
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Cannot read " + member + " of " + bean.getClass().getName(), e);
            }
            throw new IllegalStateException("The member " + member + " does not yield the property value");
        }
    }
}
