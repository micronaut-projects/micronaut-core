/*
 * Copyright 2017-2021 original authors
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
package io.micronaut.inject.ast;

import io.micronaut.context.annotation.Bean;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

@Internal
final class DefaultElementQuery<T extends Element> implements ElementQuery<T>, ElementQuery.Result<T> {
    private static final ClassElement ONLY_ACCESSIBLE_MARKER = ClassElement.of(DefaultElementQuery.class);
    private final Class<T> elementType;
    private final ClassElement onlyAccessibleType;
    private final boolean onlyDeclared;
    private final boolean onlyAbstract;
    private final boolean onlyConcrete;
    private final boolean onlyInjected;
    private final List<Predicate<String>> namePredicates;
    private final List<Predicate<AnnotationMetadata>> annotationPredicates;
    private final List<Predicate<Set<ElementModifier>>> modifiersPredicates;
    private final List<Predicate<T>> elementPredicates;
    private final List<Predicate<ClassElement>> typePredicates;
    private final boolean onlyInstance;
    private final boolean onlyStatic;
    private final boolean includeEnumConstants;
    private final boolean includeOverriddenMethods;
    private final boolean includeHiddenElements;
    private final boolean excludePropertyElements;

    DefaultElementQuery(Class<T> elementType) {
        this(elementType, null, false, false, false, false, false, false, false, false, false, false, null, null, null, null, null);
    }

    @SuppressWarnings("checkstyle:ParameterNumber")
    DefaultElementQuery(
        Class<T> elementType,
        ClassElement onlyAccessibleType,
        boolean onlyDeclared,
        boolean onlyAbstract,
        boolean onlyConcrete,
        boolean onlyInjected,
        boolean onlyInstance,
        boolean onlyStatic,
        boolean includeEnumConstants,
        boolean includeOverriddenMethods,
        boolean includeHiddenElements,
        boolean excludePropertyElements,
        List<Predicate<AnnotationMetadata>> annotationPredicates,
        List<Predicate<Set<ElementModifier>>> modifiersPredicates,
        List<Predicate<T>> elementPredicates,
        List<Predicate<String>> namePredicates, List<Predicate<ClassElement>> typePredicates) {
        this.elementType = elementType;
        this.onlyAccessibleType = onlyAccessibleType;
        this.onlyDeclared = onlyDeclared;
        this.onlyAbstract = onlyAbstract;
        this.onlyConcrete = onlyConcrete;
        this.onlyInjected = onlyInjected;
        this.namePredicates = namePredicates;
        this.annotationPredicates = annotationPredicates;
        this.modifiersPredicates = modifiersPredicates;
        this.elementPredicates = elementPredicates;
        this.onlyInstance = onlyInstance;
        this.onlyStatic = onlyStatic;
        this.includeEnumConstants = includeEnumConstants;
        this.includeOverriddenMethods = includeOverriddenMethods;
        this.includeHiddenElements = includeHiddenElements;
        this.excludePropertyElements = excludePropertyElements;
        this.typePredicates = typePredicates;
    }

    @Override
    public boolean isOnlyAbstract() {
        return onlyAbstract;
    }

    @Override
    public boolean isOnlyInjected() {
        return onlyInjected;
    }

    @Override
    public boolean isOnlyConcrete() {
        return onlyConcrete;
    }

    @Override
    public Class<T> getElementType() {
        return elementType;
    }

    @Override
    public boolean isOnlyAccessible() {
        return onlyAccessibleType != null;
    }

    @Override
    public Optional<ClassElement> getOnlyAccessibleFromType() {
        if (onlyAccessibleType != ONLY_ACCESSIBLE_MARKER) {
            return Optional.ofNullable(onlyAccessibleType);
        }
        return Optional.empty();
    }

    @Override
    public boolean isOnlyDeclared() {
        return onlyDeclared;
    }

    @Override
    public boolean isOnlyInstance() {
        return onlyInstance;
    }

    @Override
    public boolean isOnlyStatic() {
        return onlyStatic;
    }

    @Override
    public boolean isIncludeEnumConstants() {
        return includeEnumConstants;
    }

    @Override
    public boolean isIncludeOverriddenMethods() {
        return includeOverriddenMethods;
    }

    @Override
    public boolean isIncludeHiddenElements() {
        return includeHiddenElements;
    }

    @Override
    public boolean isExcludePropertyElements() {
        return excludePropertyElements;
    }

    @Override
    public List<Predicate<String>> getNamePredicates() {
        if (namePredicates == null) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(namePredicates);
    }

    @NonNull
    @Override
    public List<Predicate<ClassElement>> getTypePredicates() {
        if (typePredicates == null) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(typePredicates);
    }

    @Override
    public List<Predicate<AnnotationMetadata>> getAnnotationPredicates() {
        if (annotationPredicates == null) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(annotationPredicates);
    }

    @Override
    public List<Predicate<Set<ElementModifier>>> getModifierPredicates() {
        if (modifiersPredicates == null) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(modifiersPredicates);
    }

    @Override
    public List<Predicate<T>> getElementPredicates() {
        if (elementPredicates == null) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(elementPredicates);
    }

    @NonNull
    @Override
    public ElementQuery<T> onlyDeclared() {
        return new DefaultElementQuery<>(
            elementType, onlyAccessibleType,
            true,
            onlyAbstract, onlyConcrete,
            onlyInjected,
            onlyInstance,
            onlyStatic,
            includeEnumConstants,
            includeOverriddenMethods,
            includeHiddenElements,
            excludePropertyElements,
            annotationPredicates, modifiersPredicates, elementPredicates, namePredicates,
            typePredicates);
    }

    @Override
    public ElementQuery<T> onlyInjected() {
        final List<Predicate<AnnotationMetadata>> annotationPredicates = this.annotationPredicates != null ? new ArrayList<>(this.annotationPredicates) : new ArrayList<>(1);
        annotationPredicates.add((metadata) ->
            metadata.hasDeclaredAnnotation(AnnotationUtil.INJECT) ||
                (metadata.hasDeclaredStereotype(AnnotationUtil.QUALIFIER) && !metadata.hasDeclaredAnnotation(Bean.class)) ||
                metadata.hasDeclaredAnnotation(AnnotationUtil.PRE_DESTROY) ||
                metadata.hasDeclaredAnnotation(AnnotationUtil.POST_CONSTRUCT));
        return new DefaultElementQuery<>(
            elementType, onlyAccessibleType,
            onlyDeclared,
            onlyAbstract,
            onlyConcrete,
            true,
            onlyInstance,
            onlyStatic,
            includeEnumConstants,
            includeOverriddenMethods,
            includeHiddenElements,
            excludePropertyElements,
            annotationPredicates, modifiersPredicates, elementPredicates, namePredicates,
            typePredicates);
    }

    @NonNull
    @Override
    public ElementQuery<T> onlyConcrete() {
        return new DefaultElementQuery<>(
            elementType, onlyAccessibleType,
            onlyDeclared,
            onlyAbstract, true,
            onlyInjected,
            onlyInstance,
            onlyStatic,
            includeEnumConstants,
            includeOverriddenMethods,
            includeHiddenElements,
            excludePropertyElements,
            annotationPredicates, modifiersPredicates, elementPredicates, namePredicates,
            typePredicates);
    }

    @NonNull
    @Override
    public ElementQuery<T> onlyAbstract() {
        return new DefaultElementQuery<>(
            elementType, onlyAccessibleType,
            onlyDeclared,
            true, onlyConcrete,
            onlyInjected,
            onlyInstance,
            onlyStatic,
            includeEnumConstants,
            includeOverriddenMethods,
            includeHiddenElements,
            excludePropertyElements,
            annotationPredicates, modifiersPredicates, elementPredicates, namePredicates,
            typePredicates);
    }

    @NonNull
    @Override
    public ElementQuery<T> onlyAccessible() {
        return new DefaultElementQuery<>(
            elementType,
            ONLY_ACCESSIBLE_MARKER,
            onlyDeclared,
            onlyAbstract, onlyConcrete,
            onlyInjected,
            onlyInstance,
            onlyStatic,
            includeEnumConstants,
            includeOverriddenMethods,
            includeHiddenElements,
            excludePropertyElements,
            annotationPredicates, modifiersPredicates, elementPredicates, namePredicates,
            typePredicates);
    }

    @NonNull
    @Override
    public ElementQuery<T> onlyAccessible(ClassElement fromType) {
        return new DefaultElementQuery<>(
            elementType,
            fromType,
            onlyDeclared,
            onlyAbstract, onlyConcrete,
            onlyInjected,
            onlyInstance,
            onlyStatic,
            includeEnumConstants,
            includeOverriddenMethods,
            includeHiddenElements,
            excludePropertyElements,
            annotationPredicates, modifiersPredicates, elementPredicates, namePredicates,
            typePredicates);
    }

    @Override
    public ElementQuery<T> onlyInstance() {
        return new DefaultElementQuery<>(
            elementType, onlyAccessibleType,
            onlyDeclared,
            onlyAbstract, onlyConcrete,
            onlyInjected,
            true,
            onlyStatic,
            includeEnumConstants,
            includeOverriddenMethods,
            includeHiddenElements,
            excludePropertyElements,
            annotationPredicates, modifiersPredicates, elementPredicates, namePredicates,
            typePredicates);
    }

    @Override
    public ElementQuery<T> onlyStatic() {
        return new DefaultElementQuery<>(
            elementType, onlyAccessibleType,
            onlyDeclared,
            onlyAbstract, onlyConcrete,
            onlyInjected,
            onlyInstance,
            true,
            includeEnumConstants,
            includeOverriddenMethods,
            includeHiddenElements,
            excludePropertyElements,
            annotationPredicates, modifiersPredicates, elementPredicates, namePredicates,
            typePredicates);
    }

    @Override
    public ElementQuery<T> includeEnumConstants() {
        return new DefaultElementQuery<>(
            elementType, onlyAccessibleType,
            onlyDeclared,
            onlyAbstract, onlyConcrete,
            onlyInjected,
            onlyInstance,
            onlyStatic,
            true,
            includeOverriddenMethods,
            includeHiddenElements,
            excludePropertyElements,
            annotationPredicates, modifiersPredicates, elementPredicates, namePredicates,
            typePredicates);
    }

    @Override
    public ElementQuery<T> includeOverriddenMethods() {
        return new DefaultElementQuery<>(
            elementType, onlyAccessibleType,
            onlyDeclared,
            onlyAbstract, onlyConcrete,
            onlyInjected,
            onlyInstance,
            onlyStatic,
            includeEnumConstants,
            true,
            includeHiddenElements,
            excludePropertyElements,
            annotationPredicates, modifiersPredicates, elementPredicates, namePredicates,
            typePredicates);
    }

    @Override
    public ElementQuery<T> includeHiddenElements() {
        return new DefaultElementQuery<>(
            elementType, onlyAccessibleType,
            onlyDeclared,
            onlyAbstract, onlyConcrete,
            onlyInjected,
            onlyInstance,
            onlyStatic,
            includeEnumConstants,
            includeOverriddenMethods,
            true,
            excludePropertyElements,
            annotationPredicates, modifiersPredicates, elementPredicates, namePredicates,
            typePredicates);
    }

    @Override
    public ElementQuery<T> excludePropertyElements() {
        return new DefaultElementQuery<>(
            elementType, onlyAccessibleType,
            onlyDeclared,
            onlyAbstract, onlyConcrete,
            onlyInjected,
            onlyInstance,
            onlyStatic,
            includeEnumConstants,
            includeOverriddenMethods,
            includeHiddenElements,
            true,
            annotationPredicates, modifiersPredicates, elementPredicates, namePredicates,
            typePredicates);
    }

    @NonNull
    @Override
    public ElementQuery<T> named(@NonNull Predicate<String> predicate) {
        Objects.requireNonNull(predicate, "Predicate cannot be null");
        List<Predicate<String>> namePredicates;
        if (this.namePredicates != null) {
            namePredicates = new ArrayList<>(this.namePredicates);
            namePredicates.add(predicate);
        } else {
            namePredicates = Collections.singletonList(predicate);
        }
        return new DefaultElementQuery<>(
            elementType,
            onlyAccessibleType,
            onlyDeclared,
            onlyAbstract,
            onlyConcrete,
            onlyInjected,
            onlyInstance,
            onlyStatic,
            includeEnumConstants,
            includeOverriddenMethods,
            includeHiddenElements,
            excludePropertyElements,
            annotationPredicates,
            modifiersPredicates,
            elementPredicates,
            namePredicates,
            typePredicates);
    }

    @NonNull
    @Override
    public ElementQuery<T> typed(@NonNull Predicate<ClassElement> predicate) {
        Objects.requireNonNull(predicate, "Predicate cannot be null");
        List<Predicate<ClassElement>> typePredicates;
        if (this.typePredicates != null) {
            typePredicates = new ArrayList<>(this.typePredicates);
            typePredicates.add(predicate);
        } else {
            typePredicates = Collections.singletonList(predicate);
        }
        return new DefaultElementQuery<>(
            elementType,
            onlyAccessibleType,
            onlyDeclared,
            onlyAbstract,
            onlyConcrete,
            onlyInjected,
            onlyInstance,
            onlyStatic,
            includeEnumConstants,
            includeOverriddenMethods,
            includeHiddenElements,
            excludePropertyElements,
            annotationPredicates,
            modifiersPredicates,
            elementPredicates,
            namePredicates,
            typePredicates);
    }

    @NonNull
    @Override
    public ElementQuery<T> annotated(@NonNull Predicate<AnnotationMetadata> predicate) {
        Objects.requireNonNull(predicate, "Predicate cannot be null");
        List<Predicate<AnnotationMetadata>> annotationPredicates;
        if (this.annotationPredicates != null) {
            annotationPredicates = new ArrayList<>(this.annotationPredicates);
            annotationPredicates.add(predicate);
        } else {
            annotationPredicates = Collections.singletonList(predicate);
        }
        return new DefaultElementQuery<>(
            elementType, onlyAccessibleType,
            onlyDeclared,
            onlyAbstract, onlyConcrete,
            onlyInjected,
            onlyInstance,
            onlyStatic,
            includeEnumConstants,
            includeOverriddenMethods,
            includeHiddenElements,
            excludePropertyElements,
            annotationPredicates, modifiersPredicates, elementPredicates, namePredicates,
            typePredicates);
    }

    @NonNull
    @Override
    public ElementQuery<T> modifiers(@NonNull Predicate<Set<ElementModifier>> predicate) {
        Objects.requireNonNull(predicate, "Predicate cannot be null");
        List<Predicate<Set<ElementModifier>>> modifierPredicates;
        if (this.modifiersPredicates != null) {
            modifierPredicates = new ArrayList<>(this.modifiersPredicates);
            modifierPredicates.add(predicate);
        } else {
            modifierPredicates = Collections.singletonList(predicate);
        }
        return new DefaultElementQuery<>(
            elementType, onlyAccessibleType,
            onlyDeclared, onlyAbstract, onlyConcrete,
            onlyInjected,
            onlyInstance,
            onlyStatic,
            includeEnumConstants,
            includeOverriddenMethods,
            includeHiddenElements,
            excludePropertyElements,
            annotationPredicates, modifierPredicates, elementPredicates, namePredicates,
            typePredicates);
    }

    @NonNull
    @Override
    public ElementQuery<T> filter(@NonNull Predicate<T> predicate) {
        Objects.requireNonNull(predicate, "Predicate cannot be null");
        List<Predicate<T>> elementPredicates;
        if (this.elementPredicates != null) {
            elementPredicates = new ArrayList<>(this.elementPredicates);
            elementPredicates.add(predicate);
        } else {
            elementPredicates = Collections.singletonList(predicate);
        }
        return new DefaultElementQuery<>(
            elementType, onlyAccessibleType,
            onlyDeclared, onlyAbstract, onlyConcrete,
            onlyInjected,
            onlyInstance,
            onlyStatic,
            includeEnumConstants,
            includeOverriddenMethods,
            includeHiddenElements,
            excludePropertyElements,
            annotationPredicates, modifiersPredicates, elementPredicates, namePredicates,
            typePredicates);
    }

    @NonNull
    @Override
    public Result<T> result() {
        return this;
    }
}
