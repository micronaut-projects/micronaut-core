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
import java.util.IdentityHashMap;
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
    private final int[][] methodLevels;
    private final BeanIntrospection<?> introspection;
    @SuppressWarnings("java:S3077") // an immutable record: a racing read resolves an equal one
    private volatile @Nullable Resolved resolved;

    /**
     * The hierarchy as the generated introspection describes it.
     *
     * @param beanType The introspected type
     * @param types The types, each once, the introspected one first
     * @param superTypes For each type, the indexes into {@code types} of the types it extends and implements
     * itself: the super class first, {@link #NO_SUPERCLASS} for an interface and {@link #OBJECT_SUPERCLASS} for
     * a class extending {@link Object}, then the interfaces
     * @param methodLevels For each bean method of the introspection, in their order, the indexes into {@code types}
     * of its declaring levels: the type declaring it, then the types declaring a method it overrides, nearest first
     * @param introspection The introspection describing the type
     */
    @UsedByGeneratedCode
    public GeneratedBeanTypeHierarchy(Class<?> beanType, AnnotationClassValue<?>[] types, int[][] superTypes,
                                      int[][] methodLevels, BeanIntrospection<?> introspection) {
        this.beanType = beanType;
        this.typeValues = types;
        this.superTypes = superTypes;
        this.methodLevels = methodLevels;
        this.introspection = introspection;
    }

    @Override
    public Class<?> getBeanType() {
        return beanType;
    }

    @Override
    public List<Class<?>> getTypes() {
        return resolved().typeList;
    }

    @Override
    public boolean contains(Class<?> type) {
        return resolved().types.containsKey(type);
    }

    @Override
    public Optional<Class<?>> getSuperclass(Class<?> type) {
        TypeEntry entry = resolved().types.get(type);
        return entry == null ? Optional.empty() : Optional.ofNullable(entry.superclass());
    }

    @Override
    public List<Class<?>> getInterfaces(Class<?> type) {
        TypeEntry entry = resolved().types.get(type);
        return entry == null ? List.of() : entry.interfaces();
    }

    @Override
    public List<Class<?>> getDeclaringTypes(BeanMethod<?, ?> method) {
        Resolved r = resolved();
        Integer index = r.methodIndexes.get(method);
        return index == null ? List.of() : r.methodLevels.get(index);
    }

    @Override
    public boolean isDeclared(BeanMethod<?, ?> method) {
        Integer index = resolved().methodIndexes.get(method);
        // the introspected type is the first of the types
        return index != null && methodLevels[index].length > 0 && methodLevels[index][0] == 0;
    }

    @Override
    public List<BeanMethod<?, ?>> getDeclaredMethods() {
        return resolved().declaredMethods;
    }

    private Resolved resolved() {
        Resolved r = resolved;
        if (r == null) {
            r = resolve();
            resolved = r;
        }
        return r;
    }

    private Resolved resolve() {
        // each type is loaded once, here, and found by its index after that
        Class<?>[] classes = new Class<?>[typeValues.length];
        for (int i = 0; i < typeValues.length; i++) {
            AnnotationClassValue<?> value = typeValues[i];
            classes[i] = value.getType().orElseThrow(() -> new IllegalStateException(
                "Type " + value.getName() + " of the hierarchy of " + beanType.getName() + " cannot be loaded"));
        }

        Map<Class<?>, TypeEntry> types = new LinkedHashMap<>(classes.length);
        for (int i = 0; i < classes.length; i++) {
            int[] parents = superTypes[i];
            Class<?> superclass = switch (parents[0]) {
                case NO_SUPERCLASS -> null;
                case OBJECT_SUPERCLASS -> Object.class;
                default -> classes[parents[0]];
            };
            Class<?>[] interfaces = new Class<?>[parents.length - 1];
            for (int j = 1; j < parents.length; j++) {
                interfaces[j - 1] = classes[parents[j]];
            }
            types.put(classes[i], new TypeEntry(superclass, List.of(interfaces)));
        }

        List<? extends BeanMethod<?, ?>> beanMethods = List.copyOf(introspection.getBeanMethods());
        if (beanMethods.size() != methodLevels.length) {
            throw new IllegalStateException("The hierarchy of " + beanType.getName() + " describes " + methodLevels.length
                + " bean methods, the introspection has " + beanMethods.size());
        }
        Map<BeanMethod<?, ?>, Integer> methodIndexes = new IdentityHashMap<>(beanMethods.size());
        List<List<Class<?>>> levels = new ArrayList<>(beanMethods.size());
        List<BeanMethod<?, ?>> declared = new ArrayList<>();
        for (int i = 0; i < beanMethods.size(); i++) {
            BeanMethod<?, ?> beanMethod = beanMethods.get(i);
            methodIndexes.put(beanMethod, i);
            int[] methodLevel = methodLevels[i];
            Class<?>[] declaring = new Class<?>[methodLevel.length];
            for (int j = 0; j < methodLevel.length; j++) {
                declaring[j] = classes[methodLevel[j]];
            }
            levels.add(List.of(declaring));
            if (methodLevel.length > 0 && methodLevel[0] == 0) {
                declared.add(beanMethod);
            }
        }
        return new Resolved(
            Collections.unmodifiableMap(types),
            List.of(classes),
            methodIndexes,
            levels,
            List.copyOf(declared)
        );
    }

    @Override
    public String toString() {
        return "BeanTypeHierarchy{" + beanType.getName() + ", types=" + getTypes() + ", methodLevels=" + Arrays.deepToString(methodLevels) + '}';
    }

    private record TypeEntry(@Nullable Class<?> superclass, List<Class<?>> interfaces) {
    }

    /**
     * Everything derived from the generated indexes, published at once.
     *
     * @param types The entry of each type
     * @param typeList The types
     * @param methodIndexes The index of each bean method, by identity; only read after publication
     * @param methodLevels The declaring levels of each bean method
     * @param declaredMethods The bean methods the introspected type declares
     */
    private record Resolved(Map<Class<?>, TypeEntry> types,
                            List<Class<?>> typeList,
                            Map<BeanMethod<?, ?>, Integer> methodIndexes,
                            List<List<Class<?>>> methodLevels,
                            List<BeanMethod<?, ?>> declaredMethods) {
    }
}
