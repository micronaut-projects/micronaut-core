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
import io.micronaut.core.util.SupplierUtil;
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
import java.util.function.Supplier;
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
     * @param returnType             The return type
     * @param visitorContext         The visitor context
     * @param genericsInfo           The generic information.
     * @param includeTypeAnnotations Whether to include type level annotations in the metadata for the element
     * @param isTypeVariable         is the type a type variable
     * @return The class element
     */
    protected @NonNull ClassElement mirrorToClassElement(TypeMirror returnType,
                                                         JavaVisitorContext visitorContext,
                                                         Map<String, Map<String, Supplier<ClassElement>>> genericsInfo,
                                                         boolean includeTypeAnnotations,
                                                         boolean isTypeVariable) {
        return mirrorToClassElement(returnType, visitorContext, genericsInfo, includeTypeAnnotations, isTypeVariable, false);
    }

    /**
     * Obtain the ClassElement for the given mirror.
     *
     * @param type                        The return type
     * @param visitorContext              The visitor context
     * @param genericsInfo                The generic information.
     * @param includeTypeAnnotations      Whether to include type level annotations in the metadata for the element
     * @param isTypeVariable              is the type a type variable
     * @param resolveGenericTypeVariables Should resolve the generics
     * @return The class element
     */
    protected @NonNull ClassElement mirrorToClassElement(TypeMirror type,
                                                         JavaVisitorContext visitorContext,
                                                         Map<String, Map<String, Supplier<ClassElement>>> genericsInfo,
                                                         boolean includeTypeAnnotations,
                                                         boolean isTypeVariable,
                                                         boolean resolveGenericTypeVariables) {
        Map<String, Supplier<ClassElement>> generics = genericsInfo == null ? Collections.emptyMap() : resolveBoundGenerics(visitorContext, genericsInfo);
        return mirrorToClassElement0(type, visitorContext, generics, new HashSet<>(), includeTypeAnnotations, isTypeVariable, resolveGenericTypeVariables);
    }

    protected final @NonNull ClassElement mirrorToClassElement0(TypeMirror type,
                                                                JavaVisitorContext visitorContext,
                                                                Map<String, Supplier<ClassElement>> genericsInfo,
                                                                Set<TypeMirror> visitedTypes,
                                                                boolean includeTypeAnnotations,
                                                                boolean isTypeVariable,
                                                                boolean resolveGenericTypeVariables) {
        if (genericsInfo == null) {
            genericsInfo = Collections.emptyMap();
        }
        if (type instanceof NoType) {
            return PrimitiveElement.VOID;
        }
        if (type instanceof DeclaredType dt) {
            Element e = dt.asElement();
            // Declared types can wrap other types, like primitives
            if (!(e.asType() instanceof DeclaredType)) {
                return mirrorToClassElement0(e.asType(), visitorContext, genericsInfo, visitedTypes, includeTypeAnnotations, isTypeVariable, resolveGenericTypeVariables);
            }
            if (e instanceof TypeElement typeElement) {
                List<? extends TypeMirror> typeArguments = dt.getTypeArguments();
                Map<String, Map<String, Supplier<ClassElement>>> typeGenerics;
                if (visitedTypes.contains(dt) || typeElement.equals(element)) {
                    Supplier<ClassElement> objectElement = SupplierUtil.memoizedNonEmpty(() -> visitorContext.getClassElement("java.lang.Object").get());
                    String typeName = typeElement.getQualifiedName().toString();
                    List<? extends TypeParameterElement> typeParameters = typeElement.getTypeParameters();
                    Map<String, Supplier<ClassElement>> resolved = CollectionUtils.newHashMap(typeArguments.size());
                    for (TypeParameterElement typeParameter : typeParameters) {
                        String variableName = typeParameter.getSimpleName().toString();
                        resolved.put(variableName, objectElement);
                    }
                    typeGenerics = Collections.singletonMap(typeName, resolved);
                } else {
                    visitedTypes.add(dt);
                    typeGenerics = alignNewGenericsInfo(typeElement, typeArguments, genericsInfo, visitedTypes, resolveGenericTypeVariables);
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
                        typeArguments,
                        typeGenerics,
                        0,
                        isTypeVariable
                ).withAnnotationMetadata(createAnnotationMetadata(typeElement, dt, includeTypeAnnotations));
            }
            return PrimitiveElement.VOID;
        }
        if (type instanceof TypeVariable tv) {
            return resolveTypeVariable(visitorContext, genericsInfo, visitedTypes, includeTypeAnnotations, tv, isTypeVariable, resolveGenericTypeVariables);
        }
        if (type instanceof ArrayType at) {
            TypeMirror componentType = at.getComponentType();
            ClassElement arrayType;
            if (componentType instanceof TypeVariable tv && componentType.getKind() == TypeKind.TYPEVAR) {
                arrayType = resolveTypeVariable(visitorContext, genericsInfo, visitedTypes, includeTypeAnnotations, tv, isTypeVariable, resolveGenericTypeVariables);
            } else {
                arrayType = mirrorToClassElement0(componentType, visitorContext, genericsInfo, visitedTypes, includeTypeAnnotations, isTypeVariable, resolveGenericTypeVariables);
            }
            return arrayType.toArray();
        }
        if (type instanceof PrimitiveType pt) {
            return PrimitiveElement.valueOf(pt.getKind().name());
        }
        if (type instanceof WildcardType wt) {
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
            Map<String, Supplier<ClassElement>> finalGenericsInfo = genericsInfo;
            return new JavaWildcardElement(
                    elementAnnotationMetadataFactory,
                    wt,
                    upperBounds
                            .map(tm -> (JavaClassElement) mirrorToClassElement0(tm, visitorContext, finalGenericsInfo, visitedTypes, includeTypeAnnotations, isTypeVariable, resolveGenericTypeVariables))
                            .toList(),
                    lowerBounds
                            .map(tm -> (JavaClassElement) mirrorToClassElement0(tm, visitorContext, finalGenericsInfo, visitedTypes, includeTypeAnnotations, isTypeVariable, resolveGenericTypeVariables))
                            .toList()
            );
        }
        return PrimitiveElement.VOID;
    }

    private Map<String, Map<String, Supplier<ClassElement>>> alignNewGenericsInfo(TypeElement typeElement,
                                                                                  List<? extends TypeMirror> typeArguments,
                                                                                  Map<String, Supplier<ClassElement>> genericsInfo,
                                                                                  Set<TypeMirror> visitedTypes,
                                                                                  boolean resolveGenericTypeVariables) {
        String typeName = typeElement.getQualifiedName().toString();
        List<? extends TypeParameterElement> typeParameters = typeElement.getTypeParameters();
        if (typeArguments.size() == typeParameters.size()) {
            Map<String, Supplier<ClassElement>> resolved = CollectionUtils.newHashMap(typeArguments.size());
            Iterator<? extends TypeMirror> i = typeArguments.iterator();
            for (TypeParameterElement typeParameter : typeParameters) {
                TypeMirror typeParameterMirror = i.next();
                String variableName = typeParameter.getSimpleName().toString();
                resolved.put(variableName, SupplierUtil.memoizedNonEmpty(() ->
                        mirrorToClassElement0(typeParameterMirror, visitorContext,
                                genericsInfo, visitedTypes, true,
                                true, resolveGenericTypeVariables)));
            }
            return Collections.singletonMap(typeName, resolved);
        }
        return Collections.emptyMap();
    }

    private ClassElement resolveTypeVariable(JavaVisitorContext visitorContext,
                                             Map<String, Supplier<ClassElement>> genericsInfo,
                                             Set<TypeMirror> visitedTypes,
                                             boolean includeTypeAnnotations,
                                             TypeVariable tv,
                                             boolean isTypeVariable,
                                             boolean resolveGenericTypeVariables) {
        String variableName = tv.toString();
        Supplier<ClassElement> bound = genericsInfo.get(variableName);
        ClassElement b = null;
        if (bound != null) {
            b = bound.get();
//            if (b instanceof JavaGenericPlaceholderElement boundElement && boundElement.getRealTypeVariable().equals(tv)) {
//                genericsInfo = Collections.emptyMap();
//            }
            ClassElement classElement = bound.get();
            if (classElement instanceof WildcardElement wildcardElement) {
                if (!wildcardElement.getType().getName().equals("java.lang.Object")) {
                    return wildcardElement;
                }
            } else {
                return classElement;
            }
        }
        List<JavaClassElement> bounds = new ArrayList<>();
        TypeMirror upperBound = tv.getUpperBound();
        // type variable is still free.
        List<? extends TypeMirror> boundsUnresolved = upperBound instanceof IntersectionType ?
                ((IntersectionType) upperBound).getBounds() :
                Collections.singletonList(upperBound);
        Map<String, Supplier<ClassElement>> finalGenericsInfo = genericsInfo;
        boundsUnresolved.stream()
                .map(tm -> (JavaClassElement) mirrorToClassElement0(tm, visitorContext, finalGenericsInfo, visitedTypes, includeTypeAnnotations, isTypeVariable, resolveGenericTypeVariables))
                .forEach(bounds::add);
        return new JavaGenericPlaceholderElement(tv, bounds, elementAnnotationMetadataFactory, 0);
    }

    private Map<String, Supplier<ClassElement>> resolveBoundGenerics(JavaVisitorContext visitorContext, Map<String, Map<String, Supplier<ClassElement>>> genericsInfo) {
        String declaringTypeName = null;
        TypeElement typeElement = visitorContext.getModelUtils().classElementFor(element);
        if (typeElement != null) {
            declaringTypeName = typeElement.getQualifiedName().toString();
        }
        Map<String, Supplier<ClassElement>> boundGenerics = genericsInfo.get(declaringTypeName);
        if (boundGenerics == null) {
            boundGenerics = Collections.emptyMap();
        }
        return boundGenerics;
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
