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
package io.micronaut.inject.beans;

import io.micronaut.core.annotation.AnnotationClassValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.UsedByGeneratedCode;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanMethod;
import io.micronaut.core.beans.BeanTypeHierarchy;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The {@link BeanTypeHierarchy} a generated introspection describes: the types as class values, each once, and
 * for each type the indexes of the types it extends and implements itself. The classes are loaded when the types
 * are first asked for.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
public final class GeneratedBeanTypeHierarchy implements BeanTypeHierarchy {

    /**
     * The first index of the super types of an interface: it has no super class.
     */
    public static final int NO_SUPERCLASS = -1;
    /**
     * The first index of the super types of a class extending {@link Object} directly.
     */
    public static final int OBJECT_SUPERCLASS = -2;

    private final Class<?> beanType;
    private final AnnotationClassValue<?>[] typeValues;
    private final int[][] superTypes;
    private final int[] declaredMethodIndexes;
    private final BeanIntrospection<?> introspection;
    private volatile @Nullable List<BeanMethod<?, ?>> declaredMethods;
    private volatile @Nullable Map<Class<?>, TypeEntry> types;

    /**
     * The hierarchy as the generated introspection describes it.
     *
     * @param beanType The introspected type
     * @param types The types, each once, the introspected one first
     * @param superTypes For each type, the indexes into {@code types} of the types it extends and implements
     * itself: the super class first, {@link #NO_SUPERCLASS} for an interface and {@link #OBJECT_SUPERCLASS} for
     * a class extending {@link Object}, then the interfaces
     * @param declaredMethods The indexes, in the bean methods of the introspection, of the ones the type declares
     * @param introspection The introspection describing the type
     */
    @UsedByGeneratedCode
    public GeneratedBeanTypeHierarchy(Class<?> beanType, AnnotationClassValue<?>[] types, int[][] superTypes,
                                      int[] declaredMethods, BeanIntrospection<?> introspection) {
        this.beanType = beanType;
        this.typeValues = types;
        this.superTypes = superTypes;
        this.declaredMethodIndexes = declaredMethods;
        this.introspection = introspection;
    }

    @Override
    public Class<?> getBeanType() {
        return beanType;
    }

    @Override
    public List<Class<?>> getTypes() {
        return List.copyOf(types().keySet());
    }

    @Override
    public boolean contains(Class<?> type) {
        return types().containsKey(type);
    }

    @Override
    public Optional<Class<?>> getSuperclass(Class<?> type) {
        TypeEntry entry = types().get(type);
        return entry == null ? Optional.empty() : Optional.ofNullable(entry.superclass());
    }

    @Override
    public List<Class<?>> getInterfaces(Class<?> type) {
        TypeEntry entry = types().get(type);
        return entry == null ? List.of() : entry.interfaces();
    }

    @Override
    public List<BeanMethod<?, ?>> getDeclaredMethods() {
        List<BeanMethod<?, ?>> resolved = declaredMethods;
        if (resolved == null) {
            List<? extends BeanMethod<?, ?>> beanMethods = List.copyOf(introspection.getBeanMethods());
            List<BeanMethod<?, ?>> declared = new ArrayList<>(declaredMethodIndexes.length);
            for (int index : declaredMethodIndexes) {
                declared.add(beanMethods.get(index));
            }
            resolved = Collections.unmodifiableList(declared);
            declaredMethods = resolved;
        }
        return resolved;
    }

    private Map<Class<?>, TypeEntry> types() {
        Map<Class<?>, TypeEntry> resolved = types;
        if (resolved == null) {
            // each type is loaded once, here, and found by its index after that
            Class<?>[] classes = new Class<?>[typeValues.length];
            for (int i = 0; i < typeValues.length; i++) {
                classes[i] = typeValues[i].getType().orElse(null);
            }
            Map<Class<?>, TypeEntry> map = new LinkedHashMap<>();
            for (int i = 0; i < classes.length; i++) {
                Class<?> type = classes[i];
                if (type == null) {
                    continue;
                }
                int[] parents = superTypes[i];
                Class<?> superclass = switch (parents[0]) {
                    case NO_SUPERCLASS -> null;
                    case OBJECT_SUPERCLASS -> Object.class;
                    default -> classes[parents[0]];
                };
                List<Class<?>> interfaces = new ArrayList<>(parents.length - 1);
                for (int j = 1; j < parents.length; j++) {
                    Class<?> anInterface = classes[parents[j]];
                    if (anInterface != null) {
                        interfaces.add(anInterface);
                    }
                }
                map.put(type, new TypeEntry(superclass, Collections.unmodifiableList(interfaces)));
            }
            resolved = Collections.unmodifiableMap(map);
            types = resolved;
        }
        return resolved;
    }

    @Override
    public String toString() {
        return "BeanTypeHierarchy{" + beanType.getName() + ", types=" + types().keySet() + ", declaredMethods=" + Arrays.toString(declaredMethodIndexes) + '}';
    }

    private record TypeEntry(@Nullable Class<?> superclass, List<Class<?>> interfaces) {
    }
}
