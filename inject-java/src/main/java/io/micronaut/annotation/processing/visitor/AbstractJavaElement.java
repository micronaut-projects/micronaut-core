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
package io.micronaut.annotation.processing.visitor;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.inject.ast.annotation.AbstractAnnotationElement;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ElementModifier;
import io.micronaut.inject.ast.PrimitiveElement;
import io.micronaut.inject.ast.TypedElement;
import io.micronaut.inject.ast.WildcardElement;
import io.micronaut.inject.ast.annotation.ElementAnnotationMetadataFactory;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.IntersectionType;
import javax.lang.model.type.NoType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.type.UnionType;
import javax.lang.model.type.WildcardType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PROTECTED;
import static javax.lang.model.element.Modifier.PUBLIC;

/**
 * An abstract class for other elements to extend from.
 *
 * @author James Kleeh
 * @author graemerocher
 * @since 1.0
 */
@Internal
public abstract class AbstractJavaElement extends AbstractAnnotationElement {

    protected final JavaVisitorContext visitorContext;
    private final JavaNativeElement nativeElement;

    /**
     * @param nativeElement             The {@link Element}
     * @param annotationMetadataFactory The annotation metadata factory
     * @param visitorContext            The Java visitor context
     */
    AbstractJavaElement(JavaNativeElement nativeElement, ElementAnnotationMetadataFactory annotationMetadataFactory, JavaVisitorContext visitorContext) {
        super(annotationMetadataFactory);
        this.nativeElement = nativeElement;
        this.visitorContext = visitorContext;
    }

    /**
     * @return copy of this element
     */
    protected abstract AbstractJavaElement copyThis();

    /**
     * @param element the values to be copied to
     */
    protected void copyValues(AbstractJavaElement element) {
        element.presetAnnotationMetadata = presetAnnotationMetadata;
    }

    protected final AbstractJavaElement makeCopy() {
        AbstractJavaElement element = copyThis();
        copyValues(element);
        return element;
    }

    @Override
    public io.micronaut.inject.ast.Element withAnnotationMetadata(AnnotationMetadata annotationMetadata) {
        AbstractJavaElement abstractJavaElement = makeCopy();
        abstractJavaElement.presetAnnotationMetadata = annotationMetadata;
        return abstractJavaElement;
    }

    @Override
    public boolean isPackagePrivate() {
        Set<Modifier> modifiers = nativeElement.element().getModifiers();
        return !(modifiers.contains(PUBLIC)
                || modifiers.contains(PROTECTED)
                || modifiers.contains(PRIVATE));
    }

    @Override
    public String getName() {
        return nativeElement.element().getSimpleName().toString();
    }

    @Override
    public Set<ElementModifier> getModifiers() {
        return nativeElement.element()
                .getModifiers().stream()
                .map(m -> ElementModifier.valueOf(m.name()))
                .collect(Collectors.toSet());
    }

    @Override
    public Optional<String> getDocumentation() {
        String doc = visitorContext.getElements().getDocComment(nativeElement.element());
        return Optional.ofNullable(doc != null ? doc.trim() : null);
    }

    @Override
    public boolean isAbstract() {
        return hasModifier(Modifier.ABSTRACT);
    }

    @Override
    public boolean isStatic() {
        return hasModifier(Modifier.STATIC);
    }

    @Override
    public boolean isPublic() {
        return hasModifier(Modifier.PUBLIC);
    }

    @Override
    public boolean isPrivate() {
        return hasModifier(Modifier.PRIVATE);
    }

    @Override
    public boolean isFinal() {
        return hasModifier(Modifier.FINAL);
    }

    @Override
    public boolean isProtected() {
        return hasModifier(Modifier.PROTECTED);
    }

    @Override
    public JavaNativeElement getNativeType() {
        return nativeElement;
    }

    @Override
    public String toString() {
        return nativeElement.element().toString();
    }

    /**
     * Obtain the ClassElement for the given mirror.
     *
     * @param owner                        The owner
     * @param type                         The type
     * @param declaredElementTypeArguments The type arguments of the declaring element (method, class)
     * @return The class element
     */
    @NonNull
    protected final ClassElement newClassElement(JavaNativeElement owner,
                                                 TypeMirror type,
                                                 Map<String, ClassElement> declaredElementTypeArguments) {
        return newClassElement(owner, type, declaredElementTypeArguments, new HashSet<>(), false);
    }

    /**
     * Obtain the ClassElement for the given mirror.
     *
     * @param type                         The type
     * @param declaredElementTypeArguments The type arguments of the declaring element (method, class)
     * @return The class element
     */
    @NonNull
    protected final ClassElement newClassElement(TypeMirror type,
                                                 Map<String, ClassElement> declaredElementTypeArguments) {
        return newClassElement(null, type, declaredElementTypeArguments, new HashSet<>(), false);
    }

    @NonNull
    private ClassElement newClassElement(JavaNativeElement owner,
                                         TypeMirror type,
                                         Map<String, ClassElement> declaredTypeArguments,
                                         Set<TypeMirror> visitedTypes,
                                         boolean isTypeVariable) {
        return newClassElement(owner, type, declaredTypeArguments, visitedTypes, isTypeVariable, false, null);
    }

    @NonNull
    private ClassElement newClassElement(JavaNativeElement owner,
                                         TypeMirror type,
                                         Map<String, ClassElement> declaredTypeArguments,
                                         Set<TypeMirror> visitedTypes,
                                         boolean isTypeVariable,
                                         boolean isRawTypeParameter,
                                         @Nullable
                                         TypeParameterElement representedTypeParameter) {
        if (declaredTypeArguments == null) {
            declaredTypeArguments = Collections.emptyMap();
        }
        if (type instanceof NoType) {
            return PrimitiveElement.VOID;
        }
        if (type instanceof DeclaredType dt) {
            Element element = dt.asElement();
            // Declared types can wrap other types, like primitives
            if (!(element.asType() instanceof DeclaredType)) {
                return newClassElement(owner, element.asType(), declaredTypeArguments, visitedTypes, isTypeVariable);
            }
            if (element instanceof TypeElement typeElement) {
                List<? extends TypeMirror> typeMirrorArguments = dt.getTypeArguments();
                Map<String, ClassElement> resolvedTypeArguments;
                if (visitedTypes.contains(dt) || typeElement.equals(nativeElement.element())) {
                    ClassElement objectElement = visitorContext.getClassElement(Object.class.getName())
                        .orElseThrow(() -> new IllegalStateException("java.lang.Object element not found"));
                    List<? extends TypeParameterElement> typeParameters = typeElement.getTypeParameters();
                    Map<String, ClassElement> resolved = CollectionUtils.newHashMap(typeMirrorArguments.size());
                    for (TypeParameterElement typeParameter : typeParameters) {
                        String variableName = typeParameter.getSimpleName().toString();
                        resolved.put(variableName, objectElement);
                    }
                    resolvedTypeArguments = resolved;
                } else {
                    visitedTypes.add(dt);
                    resolvedTypeArguments = resolveTypeArguments(typeElement.getTypeParameters(), typeMirrorArguments, declaredTypeArguments, visitedTypes);
                }
                if (visitorContext.getModelUtils().resolveKind(typeElement, ElementKind.ENUM).isPresent()) {
                    return new JavaEnumElement(
                            new JavaNativeElement.Class(typeElement, type, owner),
                            elementAnnotationMetadataFactory,
                            visitorContext
                    );
                }
                return new JavaClassElement(
                        new JavaNativeElement.Class(typeElement, type, owner),
                        elementAnnotationMetadataFactory,
                        visitorContext,
                        typeMirrorArguments,
                        resolvedTypeArguments,
                        0,
                        isTypeVariable
                );
            }
            return PrimitiveElement.VOID;
        }
        if (type instanceof TypeVariable tv) {
            return resolveTypeVariable(owner, declaredTypeArguments, visitedTypes, tv, isRawTypeParameter);
        }
        if (type instanceof ArrayType at) {
            TypeMirror componentType = at.getComponentType();
            return newClassElement(owner, componentType, declaredTypeArguments, visitedTypes, isTypeVariable)
                    .toArray();
        }
        if (type instanceof PrimitiveType pt) {
            return PrimitiveElement.valueOf(pt.getKind().name());
        }
        if (type instanceof WildcardType wt) {
            return resolveWildcard(owner, declaredTypeArguments, visitedTypes, representedTypeParameter, wt);
        }
        return PrimitiveElement.VOID;
    }

    private ClassElement resolveWildcard(JavaNativeElement owner,
                                         Map<String, ClassElement> declaredTypeArguments,
                                         Set<TypeMirror> visitedTypes,
                                         TypeParameterElement representedTypeParameter,
                                         WildcardType wt) {
        TypeMirror superBound = wt.getSuperBound();
        Stream<? extends TypeMirror> lowerBounds;
        if (superBound instanceof UnionType unionType) {
            lowerBounds = unionType.getAlternatives().stream();
        } else {
            lowerBounds = Stream.ofNullable(superBound);
        }
        TypeMirror extendsBound = wt.getExtendsBound();
        Stream<? extends TypeMirror> upperBounds;
        if (extendsBound instanceof IntersectionType it) {
            upperBounds = it.getBounds().stream();
        } else if (extendsBound == null) {
            upperBounds = Stream.of(visitorContext.getElements().getTypeElement(Object.class.getName()).asType());
        } else {
            upperBounds = Stream.of(extendsBound);
        }
        List<ClassElement> upperBoundsAsElements = upperBounds
                .map(tm -> newClassElement(owner, tm, declaredTypeArguments, visitedTypes, true))
                .toList();
        List<ClassElement> lowerBoundsAsElements = lowerBounds
                .map(tm -> newClassElement(owner, tm, declaredTypeArguments, visitedTypes, true))
                .toList();
        ClassElement upperType = WildcardElement.findUpperType(upperBoundsAsElements, lowerBoundsAsElements);
        if (upperType.getType().getName().equals(Object.class.getName())) {
            // Not bounded wildcard: <?>
            if (representedTypeParameter != null) {
                ClassElement definedTypeBound = newClassElement(owner, representedTypeParameter.asType(), declaredTypeArguments, visitedTypes, true);
                // Use originating parameter to extract the bound defined
                if (definedTypeBound instanceof JavaGenericPlaceholderElement javaGenericPlaceholderElement) {
                    upperType = WildcardElement.findUpperType(javaGenericPlaceholderElement.getBounds(), Collections.emptyList());
                }
            }
        }
        if (upperType.isPrimitive()) {
            // TODO: Support primitives for wildcards (? extends byte[])
            return upperType;
        }
        return new JavaWildcardElement(
                elementAnnotationMetadataFactory,
                wt,
                (JavaClassElement) upperType,
                upperBoundsAsElements.stream().map(JavaClassElement.class::cast).toList(),
                lowerBoundsAsElements.stream().map(JavaClassElement.class::cast).toList()
        );
    }

    protected final Map<String, ClassElement> resolveTypeArguments(TypeElement typeElement,
                                                                   @Nullable
                                                                   List<? extends TypeMirror> typeMirrorArguments) {
        return resolveTypeArguments(typeElement.getTypeParameters(), typeMirrorArguments, Collections.emptyMap(), new HashSet<>());
    }

    protected final Map<String, ClassElement> resolveTypeArguments(ExecutableElement executableElement, Map<String, ClassElement> parentTypeArguments) {
        return resolveTypeArguments(executableElement.getTypeParameters(), null, parentTypeArguments, new HashSet<>());
    }

    private Map<String, ClassElement> resolveTypeArguments(List<? extends TypeParameterElement> typeParameters,
                                                           @Nullable
                                                           List<? extends TypeMirror> typeMirrorArguments,
                                                           Map<String, ClassElement> parentTypeArguments,
                                                           Set<TypeMirror> visitedTypes) {
        if (typeParameters.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, ClassElement> resolved = CollectionUtils.newLinkedHashMap(typeParameters.size());
        if (typeMirrorArguments != null && typeMirrorArguments.size() == typeParameters.size()) {
            Iterator<? extends TypeMirror> i = typeMirrorArguments.iterator();
            for (TypeParameterElement typeParameter : typeParameters) {
                TypeMirror typeParameterMirror = i.next();
                String variableName = typeParameter.getSimpleName().toString();
                resolved.put(
                        variableName,
                        newClassElement(getNativeType(), typeParameterMirror, parentTypeArguments, visitedTypes, typeParameterMirror instanceof TypeVariable, false, typeParameter)
                );
            }
        } else {
            // Not null means raw type definition: "List myMethod()"
            // Null value means a class definition: "class List<T> {}"
            boolean isRaw = typeMirrorArguments != null;
            for (TypeParameterElement typeParameter : typeParameters) {
                String variableName = typeParameter.getSimpleName().toString();
                resolved.put(
                        variableName,
                        newClassElement(getNativeType(), typeParameter.asType(), parentTypeArguments, visitedTypes, true, isRaw, null)
                );
            }
        }
        return resolved;
    }

    private ClassElement resolveTypeVariable(JavaNativeElement owner,
                                             Map<String, ClassElement> parentTypeArguments,
                                             Set<TypeMirror> visitedTypes,
                                             TypeVariable tv,
                                             boolean isRawType) {
        String variableName = tv.toString();
        ClassElement resolvedBound = parentTypeArguments.get(variableName);
        List<JavaClassElement> bounds = null;
        io.micronaut.inject.ast.Element declaredElement = this;
        JavaClassElement resolved = null;
        int arrayDimensions = 0;
        if (resolvedBound != null) {
            if (resolvedBound instanceof WildcardElement wildcardElement) {
                if (wildcardElement.isBounded()) {
                    return wildcardElement;
                }
            } else if (resolvedBound instanceof JavaGenericPlaceholderElement javaGenericPlaceholderElement) {
                bounds = javaGenericPlaceholderElement.getBounds();
                declaredElement = javaGenericPlaceholderElement.getRequiredDeclaringElement();
                resolved = javaGenericPlaceholderElement.getResolvedInternal();
                isRawType = javaGenericPlaceholderElement.isRawType();
                arrayDimensions = javaGenericPlaceholderElement.getArrayDimensions();
            } else if (resolvedBound instanceof JavaClassElement resolvedClassElement) {
                resolved = resolvedClassElement;
                isRawType = resolvedClassElement.isRawType();
                arrayDimensions = resolvedClassElement.getArrayDimensions();
            } else {
                // Most likely primitive array
                return resolvedBound;
            }
        }
        if (bounds == null) {
            bounds = new ArrayList<>();
            TypeMirror upperBound = tv.getUpperBound();
            // type variable is still free.
            List<? extends TypeMirror> boundsUnresolved = upperBound instanceof IntersectionType it ?
                    it.getBounds() :
                    Collections.singletonList(upperBound);
            boundsUnresolved.stream()
                    .map(tm -> (JavaClassElement) newClassElement(owner, tm, parentTypeArguments, visitedTypes, true))
                    .forEach(bounds::add);
        }
        return new JavaGenericPlaceholderElement(new JavaNativeElement.Placeholder(tv.asElement(), tv, getNativeType()), tv, declaredElement, resolved, bounds, elementAnnotationMetadataFactory, arrayDimensions, isRawType);
    }

    private boolean hasModifier(Modifier modifier) {
        return nativeElement.element().getModifiers().contains(modifier);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null) {
            return false;
        }
        io.micronaut.inject.ast.Element that = (io.micronaut.inject.ast.Element) o;
        if (that instanceof TypedElement element && element.isPrimitive()) {
            return false;
        }
        // Do not check if classes match, sometimes it's an anonymous one
        if (!(that instanceof AbstractJavaElement abstractJavaElement)) {
            return false;
        }
        // We allow to match different subclasses like JavaClassElement, JavaPlaceholder, JavaWildcard etc
        return nativeElement.element().equals(abstractJavaElement.getNativeType().element());
    }

    @Override
    public int hashCode() {
        return nativeElement.element().hashCode();
    }
}
