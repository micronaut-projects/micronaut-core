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
import java.util.Collections;
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
     * Returns a class element with aligned generic information.
     *
     * @param typeMirror          The type mirror
     * @param visitorContext      The visitor context
     * @param declaredGenericInfo The declared generic info
     * @return The class element
     */
    protected @NonNull ClassElement parameterizedClassElement(
        TypeMirror typeMirror,
        JavaVisitorContext visitorContext,
        Map<String, Map<String, Supplier<ClassElement>>> declaredGenericInfo) {
        return mirrorToClassElement(
            typeMirror,
            visitorContext,
            declaredGenericInfo,
            true);
    }

    /**
     * Obtain the ClassElement for the given mirror.
     *
     * @param returnType     The return type
     * @param visitorContext The visitor context
     * @return The class element
     */
    protected @NonNull ClassElement mirrorToClassElement(TypeMirror returnType, JavaVisitorContext visitorContext) {
        return mirrorToClassElement(returnType, visitorContext, Collections.emptyMap(), true);
    }

    /**
     * Obtain the ClassElement for the given mirror.
     *
     * @param returnType     The return type
     * @param visitorContext The visitor context
     * @param genericsInfo   The generic information.
     * @return The class element
     */
    protected @NonNull ClassElement mirrorToClassElement(TypeMirror returnType,
                                                         JavaVisitorContext visitorContext,
                                                         Map<String, Map<String, Supplier<ClassElement>>> genericsInfo) {
        return mirrorToClassElement(returnType, visitorContext, genericsInfo, true);
    }

    /**
     * Obtain the ClassElement for the given mirror.
     *
     * @param returnType             The return type
     * @param visitorContext         The visitor context
     * @param genericsInfo           The generic information.
     * @param includeTypeAnnotations Whether to include type level annotations in the metadata for the element
     * @return The class element
     */
    protected @NonNull ClassElement mirrorToClassElement(TypeMirror returnType,
                                                         JavaVisitorContext visitorContext,
                                                         Map<String, Map<String, Supplier<ClassElement>>> genericsInfo,
                                                         boolean includeTypeAnnotations) {
        return mirrorToClassElement(returnType, visitorContext, genericsInfo, includeTypeAnnotations, returnType instanceof TypeVariable);
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
        if (genericsInfo == null) {
            genericsInfo = Collections.emptyMap();
        }
        if (returnType instanceof NoType) {
            return PrimitiveElement.VOID;
        }
        if (returnType instanceof DeclaredType dt) {
            Element e = dt.asElement();
            // Declared types can wrap other types, like primitives
            if (!(e.asType() instanceof DeclaredType)) {
                return mirrorToClassElement(e.asType(), visitorContext, genericsInfo, includeTypeAnnotations);
            }
            if (e instanceof TypeElement typeElement) {
                List<? extends TypeMirror> typeArguments = dt.getTypeArguments();
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
                        alignNewGenericsInfo(typeElement, typeArguments, genericsInfo),
                        0,
                        isTypeVariable
                ).withAnnotationMetadata(createAnnotationMetadata(typeElement, dt, includeTypeAnnotations));
            }
            return PrimitiveElement.VOID;
        }
        if (returnType instanceof TypeVariable tv) {
            return resolveTypeVariable(visitorContext, genericsInfo, includeTypeAnnotations, tv, tv, isTypeVariable);
        }
        if (returnType instanceof ArrayType at) {
            TypeMirror componentType = at.getComponentType();
            ClassElement arrayType;
            if (componentType instanceof TypeVariable tv && componentType.getKind() == TypeKind.TYPEVAR) {
                arrayType = resolveTypeVariable(visitorContext, genericsInfo, includeTypeAnnotations, tv, at, isTypeVariable);
            } else {
                arrayType = mirrorToClassElement(componentType, visitorContext, genericsInfo, includeTypeAnnotations, isTypeVariable);
            }
            return arrayType.toArray();
        }
        if (returnType instanceof PrimitiveType pt) {
            return PrimitiveElement.valueOf(pt.getKind().name());
        }
        if (returnType instanceof WildcardType wt) {
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
            Map<String, Map<String, Supplier<ClassElement>>> finalGenericsInfo = genericsInfo;
            return new JavaWildcardElement(
                elementAnnotationMetadataFactory,
                wt,
                upperBounds
                    .map(tm -> (JavaClassElement) mirrorToClassElement(tm, visitorContext, finalGenericsInfo, includeTypeAnnotations))
                    .toList(),
                lowerBounds
                    .map(tm -> (JavaClassElement) mirrorToClassElement(tm, visitorContext, finalGenericsInfo, includeTypeAnnotations))
                    .toList()
            );
        }
        return PrimitiveElement.VOID;
    }

    private Map<String, Map<String, Supplier<ClassElement>>> alignNewGenericsInfo(TypeElement typeElement,
                                                                                  List<? extends TypeMirror> typeArguments,
                                                                                  Map<String, Map<String, Supplier<ClassElement>>> genericsInfo) {
        String typeName = typeElement.getQualifiedName().toString();
        List<? extends TypeParameterElement> typeParameters = typeElement.getTypeParameters();

        if (typeArguments.size() == typeParameters.size()) {
            Map<String, Supplier<ClassElement>> resolved = CollectionUtils.newHashMap(typeArguments.size());
            Iterator<? extends TypeMirror> i = typeArguments.iterator();
            for (TypeParameterElement typeParameter : typeParameters) {
                TypeMirror typeParameterMirror = i.next();
                String variableName = typeParameter.getSimpleName().toString();
                resolved.put(variableName, SupplierUtil.memoizedNonEmpty(() -> mirrorToClassElement(typeParameterMirror, visitorContext, genericsInfo)));
            }
            return Collections.singletonMap(typeName, resolved);
        }
        return Collections.emptyMap();
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

    private ClassElement resolveTypeVariable(JavaVisitorContext visitorContext,
                                             Map<String, Map<String, Supplier<ClassElement>>> genericsInfo,
                                             boolean includeTypeAnnotations,
                                             TypeVariable tv,
                                             TypeMirror declaration,
                                             boolean isTypeVariable) {
        TypeMirror upperBound = tv.getUpperBound();
        Map<String, Supplier<ClassElement>> boundGenerics = resolveBoundGenerics(visitorContext, genericsInfo);

        Supplier<ClassElement> bound = boundGenerics.get(tv.toString());
        if (bound != null) {
            ClassElement classElement = bound.get();
            if (classElement instanceof WildcardElement wildcardElement) {
                if (!wildcardElement.getType().getName().equals("java.lang.Object")) {
                    return wildcardElement;
                }
            } else {
                return classElement;
            }
        }
        // type variable is still free.
        List<? extends TypeMirror> boundsUnresolved = upperBound instanceof IntersectionType ?
            ((IntersectionType) upperBound).getBounds() :
            Collections.singletonList(upperBound);
        List<JavaClassElement> bounds = boundsUnresolved.stream()
            .map(tm -> (JavaClassElement) mirrorToClassElement(tm,
                visitorContext,
                genericsInfo,
                includeTypeAnnotations,
                isTypeVariable))
            .toList();
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
