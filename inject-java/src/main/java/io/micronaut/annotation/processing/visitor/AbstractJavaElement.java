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

import io.micronaut.annotation.processing.AnnotationUtils;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.AnnotationValueBuilder;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ElementModifier;
import io.micronaut.inject.ast.PrimitiveElement;
import io.micronaut.inject.ast.TypedElement;
import io.micronaut.inject.ast.WildcardElement;
import io.micronaut.inject.ast.annotation.ElementAnnotationMetadata;
import io.micronaut.inject.ast.annotation.ElementAnnotationMetadataFactory;
import io.micronaut.inject.ast.annotation.ElementMutableAnnotationMetadataDelegate;
import io.micronaut.inject.ast.annotation.MutableAnnotationMetadataDelegate;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.IntersectionType;
import javax.lang.model.type.NoType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.type.UnionType;
import javax.lang.model.type.WildcardType;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
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
public abstract class AbstractJavaElement implements io.micronaut.inject.ast.Element, ElementMutableAnnotationMetadataDelegate<io.micronaut.inject.ast.Element> {

    protected final JavaVisitorContext visitorContext;
    protected final ElementAnnotationMetadataFactory elementAnnotationMetadataFactory;
    @Nullable
    protected AnnotationMetadata presetAnnotationMetadata;
    private final Element element;
    @Nullable
    private ElementAnnotationMetadata elementAnnotationMetadata;

    /**
     * @param element                   The {@link Element}
     * @param annotationMetadataFactory The annotation metadata factory
     * @param visitorContext            The Java visitor context
     */
    AbstractJavaElement(Element element, ElementAnnotationMetadataFactory annotationMetadataFactory, JavaVisitorContext visitorContext) {
        this.element = element;
        this.elementAnnotationMetadataFactory = annotationMetadataFactory;
        this.visitorContext = visitorContext;
    }

    @Override
    public io.micronaut.inject.ast.Element getReturnInstance() {
        return this;
    }

    @Override
    public MutableAnnotationMetadataDelegate<?> getAnnotationMetadata() {
        if (elementAnnotationMetadata == null) {
            if (presetAnnotationMetadata == null) {
                elementAnnotationMetadata = elementAnnotationMetadataFactory.build(this);
            } else {
                elementAnnotationMetadata = elementAnnotationMetadataFactory.build(this, presetAnnotationMetadata);
            }
        }
        return elementAnnotationMetadata;
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
    public <T extends Annotation> io.micronaut.inject.ast.Element annotate(String annotationType, Consumer<AnnotationValueBuilder<T>> consumer) {
        return ElementMutableAnnotationMetadataDelegate.super.annotate(annotationType, consumer);
    }

    @Override
    public io.micronaut.inject.ast.Element removeAnnotation(String annotationType) {
        return ElementMutableAnnotationMetadataDelegate.super.removeAnnotation(annotationType);
    }

    @Override
    public <T extends Annotation> io.micronaut.inject.ast.Element removeAnnotation(Class<T> annotationType) {
        return ElementMutableAnnotationMetadataDelegate.super.removeAnnotation(annotationType);
    }

    @Override
    public <T extends Annotation> io.micronaut.inject.ast.Element removeAnnotationIf(Predicate<AnnotationValue<T>> predicate) {
        return ElementMutableAnnotationMetadataDelegate.super.removeAnnotationIf(predicate);
    }

    @Override
    public io.micronaut.inject.ast.Element removeStereotype(String annotationType) {
        return ElementMutableAnnotationMetadataDelegate.super.removeStereotype(annotationType);
    }

    @Override
    public <T extends Annotation> io.micronaut.inject.ast.Element removeStereotype(Class<T> annotationType) {
        return ElementMutableAnnotationMetadataDelegate.super.removeStereotype(annotationType);
    }

    @Override
    public io.micronaut.inject.ast.Element annotate(String annotationType) {
        return ElementMutableAnnotationMetadataDelegate.super.annotate(annotationType);
    }

    @Override
    public <T extends Annotation> io.micronaut.inject.ast.Element annotate(Class<T> annotationType, Consumer<AnnotationValueBuilder<T>> consumer) {
        return ElementMutableAnnotationMetadataDelegate.super.annotate(annotationType, consumer);
    }

    @Override
    public <T extends Annotation> io.micronaut.inject.ast.Element annotate(Class<T> annotationType) {
        return ElementMutableAnnotationMetadataDelegate.super.annotate(annotationType);
    }

    @Override
    public <T extends Annotation> io.micronaut.inject.ast.Element annotate(AnnotationValue<T> annotationValue) {
        return ElementMutableAnnotationMetadataDelegate.super.annotate(annotationValue);
    }

    @Override
    public boolean isPackagePrivate() {
        Set<Modifier> modifiers = element.getModifiers();
        return !(modifiers.contains(PUBLIC)
                || modifiers.contains(PROTECTED)
                || modifiers.contains(PRIVATE));
    }

    @Override
    public String getName() {
        return element.getSimpleName().toString();
    }

    @Override
    public Set<ElementModifier> getModifiers() {
        return element
                .getModifiers().stream()
                .map(m -> ElementModifier.valueOf(m.name()))
                .collect(Collectors.toSet());
    }

    @Override
    public Optional<String> getDocumentation() {
        String doc = visitorContext.getElements().getDocComment(element);
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
    public Object getNativeType() {
        return element;
    }

    @Override
    public String toString() {
        return element.toString();
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
        return newClassElement(type, declaredElementTypeArguments, new HashSet<>(), true);
    }


    @NonNull
    private ClassElement newClassElement(TypeMirror type,
                                         Map<String, ClassElement> declaredTypeArguments,
                                         Set<TypeMirror> visitedTypes,
                                         boolean includeTypeAnnotations) {
        return newClassElement(type, declaredTypeArguments, visitedTypes, includeTypeAnnotations, false, null);
    }

    @NonNull
    private ClassElement newClassElement(TypeMirror type,
                                         Map<String, ClassElement> declaredTypeArguments,
                                         Set<TypeMirror> visitedTypes,
                                         boolean includeTypeAnnotations,
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
            Element e = dt.asElement();
            // Declared types can wrap other types, like primitives
            if (!(e.asType() instanceof DeclaredType)) {
                return newClassElement(e.asType(), declaredTypeArguments, visitedTypes, includeTypeAnnotations);
            }
            if (e instanceof TypeElement typeElement) {
                List<? extends TypeMirror> typeMirrorArguments = dt.getTypeArguments();
                Map<String, ClassElement> resolvedTypeArguments;
                if (visitedTypes.contains(dt) || typeElement.equals(element)) {
                    ClassElement objectElement = visitorContext.getClassElement("java.lang.Object").get();
                    List<? extends TypeParameterElement> typeParameters = typeElement.getTypeParameters();
                    Map<String, ClassElement> resolved = CollectionUtils.newHashMap(typeMirrorArguments.size());
                    for (TypeParameterElement typeParameter : typeParameters) {
                        String variableName = typeParameter.getSimpleName().toString();
                        resolved.put(variableName, objectElement);
                    }
                    resolvedTypeArguments = resolved;
                } else {
                    visitedTypes.add(dt);
                    resolvedTypeArguments = resolveTypeArguments(typeElement, typeMirrorArguments, declaredTypeArguments, visitedTypes);
                }
                if (visitorContext.getModelUtils().resolveKind(typeElement, ElementKind.ENUM).isPresent()) {
                    return new JavaEnumElement(
                            typeElement,
                            elementAnnotationMetadataFactory,
                            visitorContext
                    ).withAnnotationMetadata(createAnnotationMetadata(typeElement, dt, includeTypeAnnotations));
                }
                return new JavaClassElement(
                        typeElement,
                        elementAnnotationMetadataFactory,
                        visitorContext,
                        typeMirrorArguments,
                        resolvedTypeArguments,
                        0
                ).withAnnotationMetadata(createAnnotationMetadata(typeElement, dt, includeTypeAnnotations));
            }
            return PrimitiveElement.VOID;
        }
        if (type instanceof TypeVariable tv) {
            return resolveTypeVariable(declaredTypeArguments, visitedTypes, includeTypeAnnotations, tv, isRawTypeParameter);
        }
        if (type instanceof ArrayType at) {
            TypeMirror componentType = at.getComponentType();
            ClassElement arrayType;
            if (componentType instanceof TypeVariable tv && componentType.getKind() == TypeKind.TYPEVAR) {
                arrayType = resolveTypeVariable(declaredTypeArguments, visitedTypes, includeTypeAnnotations, tv, isRawTypeParameter);
            } else {
                arrayType = newClassElement(componentType, declaredTypeArguments, visitedTypes, includeTypeAnnotations);
            }
            return arrayType.toArray();
        }
        if (type instanceof PrimitiveType pt) {
            return PrimitiveElement.valueOf(pt.getKind().name());
        }
        if (type instanceof WildcardType wt) {
            return resolveWildcard(visitorContext, declaredTypeArguments, visitedTypes, includeTypeAnnotations, representedTypeParameter, wt);
        }
        return PrimitiveElement.VOID;
    }

    private ClassElement resolveWildcard(JavaVisitorContext visitorContext,
                                         Map<String, ClassElement> declaredTypeArguments,
                                         Set<TypeMirror> visitedTypes,
                                         boolean includeTypeAnnotations,
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
            upperBounds = Stream.of(visitorContext.getElements().getTypeElement("java.lang.Object").asType());
        } else {
            upperBounds = Stream.of(extendsBound);
        }
        List<JavaClassElement> upperBoundsAsElements = upperBounds
                .map(tm -> (JavaClassElement) newClassElement(tm, declaredTypeArguments, visitedTypes, includeTypeAnnotations))
                .toList();
        List<JavaClassElement> lowerBoundsAsElements = lowerBounds
                .map(tm -> (JavaClassElement) newClassElement(tm, declaredTypeArguments, visitedTypes, includeTypeAnnotations))
                .toList();
        JavaClassElement upperType = WildcardElement.findUpperType(upperBoundsAsElements, lowerBoundsAsElements);
        if (upperType.getType().getName().equals("java.lang.Object")) {
            // Not bounded wildcard: <?>
            if (representedTypeParameter != null) {
                JavaClassElement definedTypeBound = (JavaClassElement) newClassElement(representedTypeParameter.asType(), declaredTypeArguments, visitedTypes, includeTypeAnnotations);
                // Use originating parameter to extract the bound defined
                if (definedTypeBound instanceof JavaGenericPlaceholderElement javaGenericPlaceholderElement) {
                    upperType = WildcardElement.findUpperType(javaGenericPlaceholderElement.getBounds(), Collections.emptyList());
                }
            }
        }
        return new JavaWildcardElement(
                elementAnnotationMetadataFactory,
                wt,
                upperType,
                upperBoundsAsElements,
                lowerBoundsAsElements
        );
    }

    protected final Map<String, ClassElement> resolveTypeArguments(TypeElement typeElement,
                                                                   @Nullable
                                                                   List<? extends TypeMirror> typeMirrorArguments,
                                                                   Map<String, ClassElement> declaredElementTypeArguments,
                                                                   Set<TypeMirror> visitedTypes) {
        List<? extends TypeParameterElement> typeParameters = typeElement.getTypeParameters();
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
                        newClassElement(typeParameterMirror, declaredElementTypeArguments, visitedTypes, true, false, typeParameter)
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
                        newClassElement(typeParameter.asType(), declaredElementTypeArguments, visitedTypes, true, isRaw, null)
                );
            }
        }
        return resolved;
    }

    private ClassElement resolveTypeVariable(Map<String, ClassElement> genericsInfo,
                                             Set<TypeMirror> visitedTypes,
                                             boolean includeTypeAnnotations,
                                             TypeVariable tv,
                                             boolean isRawType) {
        String variableName = tv.toString();
        ClassElement b = genericsInfo.get(variableName);
        if (b != null) {
            if (b instanceof WildcardElement wildcardElement) {
                if (wildcardElement.isBounded()) {
                    return wildcardElement;
                }
            } else {
                return b;
            }
        }
        List<JavaClassElement> bounds = new ArrayList<>();
        TypeMirror upperBound = tv.getUpperBound();
        // type variable is still free.
        List<? extends TypeMirror> boundsUnresolved = upperBound instanceof IntersectionType ?
                ((IntersectionType) upperBound).getBounds() :
                Collections.singletonList(upperBound);
        boundsUnresolved.stream()
                .map(tm -> (JavaClassElement) newClassElement(tm, genericsInfo, visitedTypes, includeTypeAnnotations))
                .forEach(bounds::add);
        return new JavaGenericPlaceholderElement(tv, bounds, elementAnnotationMetadataFactory, 0, isRawType);
    }

    private AnnotationMetadata createAnnotationMetadata(TypeElement typeElement, DeclaredType dt, boolean includeTypeAnnotations) {
        AnnotationUtils annotationUtils = visitorContext
                .getAnnotationUtils();
        AnnotationMetadata newAnnotationMetadata;
        List<? extends AnnotationMirror> annotationMirrors = dt.getAnnotationMirrors();
        if (!annotationMirrors.isEmpty()) {
            newAnnotationMetadata = annotationUtils.newAnnotationBuilder().buildDeclared(typeElement, annotationMirrors, includeTypeAnnotations);
        } else {
            newAnnotationMetadata = includeTypeAnnotations ? annotationUtils.newAnnotationBuilder().lookupOrBuildForType(typeElement).copyAnnotationMetadata() : AnnotationMetadata.EMPTY_METADATA;
        }
        return newAnnotationMetadata;
    }

    private boolean hasModifier(Modifier modifier) {
        return element.getModifiers().contains(modifier);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        // Do not check if classes match, sometimes it's an anonymous one
        if (o == null) {
            return false;
        }
        io.micronaut.inject.ast.Element that = (io.micronaut.inject.ast.Element) o;
        if (that instanceof TypedElement && ((TypedElement) that).isPrimitive()) {
            return false;
        }
        return element.equals(that.getNativeType());
    }

    @Override
    public int hashCode() {
        return element.hashCode();
    }
}
