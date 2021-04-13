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

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.Predicate;

@Internal
final class DefaultElementQuery<T extends Element> implements ElementQuery<T>, ElementQuery.Result<T> {
    private final Class<T> elementType;
    private final boolean onlyAccessible;
    private final boolean onlyDeclared;
    private final boolean onlyAbstract;
    private final boolean onlyConcrete;
    private final List<Predicate<String>> namePredicates;
    private final List<Predicate<AnnotationMetadata>> annotationPredicates;
    private final List<Predicate<Set<ElementModifier>>> modifiersPredicates;
    private final List<Predicate<T>> elementPredicates;
    private final boolean onlyInstance;

    DefaultElementQuery(Class<T> elementType) {
        this(elementType, false, false, false, false, false, null, null, null, null);
    }

    DefaultElementQuery(
            Class<T> elementType,
            boolean onlyAccessible,
            boolean onlyDeclared,
            boolean onlyAbstract, boolean onlyConcrete, boolean onlyInstance, List<Predicate<AnnotationMetadata>> annotationPredicates, List<Predicate<Set<ElementModifier>>> modifiersPredicates, List<Predicate<T>> elementPredicates, List<Predicate<String>> namePredicates) {
        this.elementType = elementType;
        this.onlyAccessible = onlyAccessible;
        this.onlyDeclared = onlyDeclared;
        this.onlyAbstract = onlyAbstract;
        this.onlyConcrete = onlyConcrete;
        this.namePredicates = namePredicates;
        this.annotationPredicates = annotationPredicates;
        this.modifiersPredicates = modifiersPredicates;
        this.elementPredicates = elementPredicates;
        this.onlyInstance = onlyInstance;
    }

    @Override
    public boolean isOnlyAbstract() {
        return onlyAbstract;
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
        return onlyAccessible;
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
    public List<Predicate<String>> getNamePredicates() {
        if (namePredicates == null) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(namePredicates);
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

    @NotNull
    @Override
    public ElementQuery<T> onlyDeclared() {
        return new DefaultElementQuery<>(
                elementType, onlyAccessible,
                true,
                onlyAbstract, onlyConcrete, onlyInstance, annotationPredicates, modifiersPredicates, elementPredicates, namePredicates
        );
    }

    @NotNull
    @Override
    public ElementQuery<T> onlyConcrete() {
        return new DefaultElementQuery<>(
                elementType, onlyAccessible,
                onlyDeclared,
                onlyAbstract, true, onlyInstance, annotationPredicates, modifiersPredicates, elementPredicates, namePredicates
        );
    }

    @NotNull
    @Override
    public ElementQuery<T> onlyAbstract() {
        return new DefaultElementQuery<>(
                elementType, onlyAccessible,
                onlyDeclared,
                true, onlyConcrete, onlyInstance, annotationPredicates, modifiersPredicates, elementPredicates, namePredicates
        );
    }

    @NotNull
    @Override
    public ElementQuery<T> onlyAccessible() {
        return new DefaultElementQuery<>(
                elementType, true,
                onlyDeclared,
                onlyAbstract, onlyConcrete, onlyInstance, annotationPredicates, modifiersPredicates, elementPredicates, namePredicates
        );
    }

    @Override
    public ElementQuery<T> onlyInstance() {
        return new DefaultElementQuery<>(
                elementType, onlyAccessible,
                onlyDeclared,
                onlyAbstract, onlyConcrete, true, annotationPredicates, modifiersPredicates, elementPredicates, namePredicates
        );
    }

    @NotNull
    @Override
    public ElementQuery<T> named(@NotNull Predicate<String> predicate) {
        Objects.requireNonNull(predicate, "Predicate cannot be null");
        List<Predicate<String>> namePredicates;
        if (this.namePredicates != null) {
            namePredicates = new ArrayList<>(this.namePredicates);
            namePredicates.add(predicate);
        } else {
            namePredicates = Collections.singletonList(predicate);
        }
        return new DefaultElementQuery<>(
                elementType, onlyAccessible,
                onlyDeclared,
                onlyAbstract, onlyConcrete, onlyInstance, annotationPredicates, modifiersPredicates, elementPredicates, namePredicates
        );
    }

    @NotNull
    @Override
    public ElementQuery<T> annotated(@NotNull Predicate<AnnotationMetadata> predicate) {
        Objects.requireNonNull(predicate, "Predicate cannot be null");
        List<Predicate<AnnotationMetadata>> annotationPredicates;
        if (this.annotationPredicates != null) {
            annotationPredicates = new ArrayList<>(this.annotationPredicates);
            annotationPredicates.add(predicate);
        } else {
            annotationPredicates = Collections.singletonList(predicate);
        }
        return new DefaultElementQuery<>(
                elementType, onlyAccessible,
                onlyDeclared,
                onlyAbstract, onlyConcrete, onlyInstance, annotationPredicates, modifiersPredicates, elementPredicates, namePredicates
        );
    }

    @NotNull
    @Override
    public ElementQuery<T> modifiers(@NotNull Predicate<Set<ElementModifier>> predicate) {
        Objects.requireNonNull(predicate, "Predicate cannot be null");
        List<Predicate<Set<ElementModifier>>> modifierPredicates;
        if (this.modifiersPredicates != null) {
            modifierPredicates = new ArrayList<>(this.modifiersPredicates);
            modifierPredicates.add(predicate);
        } else {
            modifierPredicates = Collections.singletonList(predicate);
        }
        return new DefaultElementQuery<>(
                elementType, onlyAccessible,
                onlyDeclared, onlyAbstract, onlyConcrete, onlyInstance, annotationPredicates, modifierPredicates, elementPredicates, namePredicates
        );
    }

    @NotNull
    @Override
    public ElementQuery<T> filter(@NotNull Predicate<T> predicate) {
        Objects.requireNonNull(predicate, "Predicate cannot be null");
        List<Predicate<T>> elementPredicates;
        if (this.elementPredicates != null) {
            elementPredicates = new ArrayList<>(this.elementPredicates);
            elementPredicates.add(predicate);
        } else {
            elementPredicates = Collections.singletonList(predicate);
        }
        return new DefaultElementQuery<>(
                elementType, onlyAccessible,
                onlyDeclared, onlyAbstract, onlyConcrete, onlyInstance, annotationPredicates, modifiersPredicates, elementPredicates, namePredicates
        );
    }

    @NotNull
    @Override
    public Result<T> result() {
        return this;
    }
}
