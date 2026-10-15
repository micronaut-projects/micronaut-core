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
package io.micronaut.context;

import io.micronaut.context.condition.Condition;
import io.micronaut.core.annotation.AnnotationValue;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.Map;

/**
 * What the descriptor of a bean definition says, as {@link BeanDefinitionDescriptorReader} read it from the
 * {@code META-INF/micronaut} entry of the definition: the answers its reference gives before the definition is
 * loaded. Immutable, and shared by the references of every context that reads the same entry.
 *
 * <p>Every type is named as {@link Class#getName()} names it, so that a name is looked up with
 * {@link Class#forName(String, boolean, ClassLoader)}, a primitive type excepted. The class values of the conditions
 * are names only, which a reference resolves when it first checks them.</p>
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 5.3.0
 */
final class BeanDefinitionDescriptorData {

    final int flags;
    final String beanTypeName;
    final String[] exposedTypeNames;
    final String[] indexNames;
    /**
     * The annotations and stereotypes, sorted for a binary search, with their membership bits in {@link #memberships}.
     */
    final String[] annotationNames;
    final byte[] memberships;
    final Map<String, String> repeatableContainers;
    final String[] nonBindingMembers;
    final AnnotationValue<?>[] qualifiers;
    final Condition[] conditions;
    final String definitionName;
    final int order;
    final String writer;
    final int revision;
    /**
     * The default values the definition registers for its qualifier annotations, as the values of one annotation
     * for each.
     */
    final AnnotationValue<?>[] qualifierDefaults;

    @SuppressWarnings("java:S107") // one argument for each field of the format
    BeanDefinitionDescriptorData(int flags,
                                 String beanTypeName,
                                 String[] exposedTypeNames,
                                 String[] indexNames,
                                 String[] annotationNames,
                                 byte[] memberships,
                                 Map<String, String> repeatableContainers,
                                 String[] nonBindingMembers,
                                 AnnotationValue<?>[] qualifiers,
                                 Condition[] conditions,
                                 String definitionName,
                                 int order,
                                 String writer,
                                 int revision,
                                 AnnotationValue<?>[] qualifierDefaults) {
        this.flags = flags;
        this.beanTypeName = beanTypeName;
        this.exposedTypeNames = exposedTypeNames;
        this.indexNames = indexNames;
        this.annotationNames = annotationNames;
        this.memberships = memberships;
        this.repeatableContainers = repeatableContainers;
        this.nonBindingMembers = nonBindingMembers;
        this.qualifiers = qualifiers;
        this.conditions = conditions;
        this.definitionName = definitionName;
        this.order = order;
        this.writer = writer;
        this.revision = revision;
        this.qualifierDefaults = qualifierDefaults;
    }

    /**
     * @param flag One of the {@code FLAG_} constants of {@link BeanDefinitionDescriptorFormat}
     * @return Whether the reference answers true for it
     */
    boolean is(int flag) {
        return (flags & flag) != 0;
    }

    /**
     * @param annotation The name of an annotation
     * @return The membership bits of the annotation, 0 when the bean has no such annotation or stereotype
     */
    int membership(@Nullable String annotation) {
        if (annotation == null) {
            return 0;
        }
        int index = Arrays.binarySearch(annotationNames, annotation);
        return index < 0 ? 0 : memberships[index];
    }

    /**
     * @param annotation The name of an annotation
     * @param membership One of the {@code MEMBERSHIP_} constants of {@link BeanDefinitionDescriptorFormat}, or several
     * @return Whether the annotation metadata of the bean answers true for one of them
     */
    boolean has(@Nullable String annotation, int membership) {
        return (membership(annotation) & membership) != 0;
    }

    /**
     * @param annotation The name of an annotation
     * @return The qualifier the bean declares with that annotation, or {@code null}
     */
    @Nullable
    AnnotationValue<?> qualifier(String annotation) {
        for (AnnotationValue<?> qualifier : qualifiers) {
            if (qualifier.getAnnotationName().equals(annotation)) {
                return qualifier;
            }
        }
        return null;
    }

    /**
     * @param type The name of a type
     * @return Whether the bean is exposed as that type
     */
    boolean exposes(String type) {
        for (String exposed : exposedTypeNames) {
            if (exposed.equals(type)) {
                return true;
            }
        }
        return false;
    }

    /**
     * @param type The name of a type
     * @return Whether the bean is exposed as, or indexed by, that type: whether a lookup of the type finds it
     */
    boolean isFoundBy(String type) {
        if (exposes(type)) {
            return true;
        }
        for (String index : indexNames) {
            if (index.equals(type)) {
                return true;
            }
        }
        return false;
    }
}
