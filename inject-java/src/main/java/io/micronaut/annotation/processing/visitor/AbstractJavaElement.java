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
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ElementAnnotationMetadata;
import io.micronaut.inject.ast.ElementAnnotationMetadataFactory;
import io.micronaut.inject.ast.ElementModifier;
import io.micronaut.inject.ast.PrimitiveElement;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
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
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
public abstract class AbstractJavaElement implements io.micronaut.inject.ast.Element {

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

    private ElementAnnotationMetadata getElementAnnotationMetadata() {
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

    @NonNull
    @Override
    public <T extends Annotation> io.micronaut.inject.ast.Element annotate(@NonNull String annotationType, @NonNull Consumer<AnnotationValueBuilder<T>> consumer) {
        getElementAnnotationMetadata().annotate(annotationType, consumer);
        return this;
    }

    @Override
    public <T extends Annotation> io.micronaut.inject.ast.Element annotate(AnnotationValue<T> annotationValue) {
        getElementAnnotationMetadata().annotate(annotationValue);
        return this;
    }

    @Override
    public io.micronaut.inject.ast.Element removeAnnotation(@NonNull String annotationType) {
        getElementAnnotationMetadata().removeAnnotation(annotationType);
        return this;
    }

    @Override
    public <T extends Annotation> io.micronaut.inject.ast.Element removeAnnotationIf(@NonNull Predicate<AnnotationValue<T>> predicate) {
        getElementAnnotationMetadata().removeAnnotationIf(predicate);
        return this;
    }

    @Override
    public io.micronaut.inject.ast.Element removeStereotype(@NonNull String annotationType) {
        getElementAnnotationMetadata().removeStereotype(annotationType);
        return this;
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
    public AnnotationMetadata getAnnotationMetadata() {
        return getElementAnnotationMetadata().get();
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
        Map<String, Map<String, TypeMirror>> declaredGenericInfo) {
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
    protected @NonNull ClassElement mirrorToClassElement(TypeMirror returnType, JavaVisitorContext visitorContext, Map<String, Map<String, TypeMirror>> genericsInfo) {
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
    protected @NonNull ClassElement mirrorToClassElement(TypeMirror returnType, JavaVisitorContext visitorContext, Map<String, Map<String, TypeMirror>> genericsInfo, boolean includeTypeAnnotations) {
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
    protected @NonNull ClassElement mirrorToClassElement(
        TypeMirror returnType,
        JavaVisitorContext visitorContext,
        Map<String, Map<String, TypeMirror>> genericsInfo,
        boolean includeTypeAnnotations,
        boolean isTypeVariable) {
        if (genericsInfo == null) {
            genericsInfo = Collections.emptyMap();
        }
        if (returnType instanceof NoType) {
            return PrimitiveElement.VOID;
        } else if (returnType instanceof DeclaredType) {
            DeclaredType dt = (DeclaredType) returnType;
            Element e = dt.asElement();
            //Declared types can wrap other types, like primitives
            if (e.asType() instanceof DeclaredType) {
                List<? extends TypeMirror> typeArguments = dt.getTypeArguments();
                if (e instanceof TypeElement) {
                    TypeElement typeElement = (TypeElement) e;
                    Map<String, TypeMirror> boundGenerics = resolveBoundGenerics(visitorContext, genericsInfo);
                    if (visitorContext.getModelUtils().resolveKind(typeElement, ElementKind.ENUM).isPresent()) {
                        return new JavaEnumElement(
                            typeElement,
                            resolveElementAnnotationMetadataFactory(typeElement, dt, includeTypeAnnotations),
                            visitorContext
                        );
                    } else {
                        genericsInfo = visitorContext.getGenericUtils().alignNewGenericsInfo(
                            typeElement,
                            typeArguments,
                            boundGenerics
                        );
                        return new JavaClassElement(
                            typeElement,
                            resolveElementAnnotationMetadataFactory(typeElement, dt, includeTypeAnnotations),
                            visitorContext,
                            typeArguments,
                            genericsInfo,
                            isTypeVariable
                        );
                    }
                }
            } else {
                return mirrorToClassElement(e.asType(), visitorContext, genericsInfo, includeTypeAnnotations);
            }
        } else if (returnType instanceof TypeVariable) {
            TypeVariable tv = (TypeVariable) returnType;
            return resolveTypeVariable(
                visitorContext,
                genericsInfo,
                includeTypeAnnotations,
                tv,
                tv
            );

        } else if (returnType instanceof ArrayType) {
            ArrayType at = (ArrayType) returnType;
            TypeMirror componentType = at.getComponentType();
            ClassElement arrayType;
            if (componentType instanceof TypeVariable && componentType.getKind() == TypeKind.TYPEVAR) {
                TypeVariable tv = (TypeVariable) componentType;
                arrayType = resolveTypeVariable(visitorContext, genericsInfo, includeTypeAnnotations, tv, at);
            } else {
                arrayType = mirrorToClassElement(componentType, visitorContext, genericsInfo, includeTypeAnnotations);
            }
            return arrayType.toArray();
        } else if (returnType instanceof PrimitiveType) {
            PrimitiveType pt = (PrimitiveType) returnType;
            return PrimitiveElement.valueOf(pt.getKind().name());
        } else if (returnType instanceof WildcardType) {
            WildcardType wt = (WildcardType) returnType;
            Map<String, Map<String, TypeMirror>> finalGenericsInfo = genericsInfo;
            TypeMirror superBound = wt.getSuperBound();
            Stream<? extends TypeMirror> lowerBounds;
            if (superBound instanceof UnionType) {
                lowerBounds = ((UnionType) superBound).getAlternatives().stream();
            } else if (superBound == null) {
                lowerBounds = Stream.empty();
            } else {
                lowerBounds = Stream.of(superBound);
            }
            TypeMirror extendsBound = wt.getExtendsBound();
            Stream<? extends TypeMirror> upperBounds;
            if (extendsBound instanceof IntersectionType) {
                upperBounds = ((IntersectionType) extendsBound).getBounds().stream();
            } else if (extendsBound == null) {
                upperBounds = Stream.of(visitorContext.getElements().getTypeElement("java.lang.Object").asType());
            } else {
                upperBounds = Stream.of(extendsBound);
            }
            return new JavaWildcardElement(
                elementAnnotationMetadataFactory,
                wt,
                upperBounds
                    .map(tm -> (JavaClassElement) mirrorToClassElement(tm, visitorContext, finalGenericsInfo, includeTypeAnnotations))
                    .collect(Collectors.toList()),
                lowerBounds
                    .map(tm -> (JavaClassElement) mirrorToClassElement(tm, visitorContext, finalGenericsInfo, includeTypeAnnotations))
                    .collect(Collectors.toList())
            );
        }
        return PrimitiveElement.VOID;
    }

    @NonNull
    private ElementAnnotationMetadataFactory resolveElementAnnotationMetadataFactory(TypeElement typeElement, DeclaredType dt, boolean includeTypeAnnotations) {
        return elementAnnotationMetadataFactory.overrideForNativeType(typeElement, element -> {
            AnnotationUtils annotationUtils = visitorContext
                .getAnnotationUtils();
            AnnotationMetadata newAnnotationMetadata;
            List<? extends AnnotationMirror> annotationMirrors = dt.getAnnotationMirrors();
            if (!annotationMirrors.isEmpty()) {
                newAnnotationMetadata = annotationUtils.newAnnotationBuilder().buildDeclared(typeElement, annotationMirrors, includeTypeAnnotations);
            } else {
                newAnnotationMetadata = includeTypeAnnotations ? annotationUtils.newAnnotationBuilder().lookupOrBuildForType(typeElement).copyAnnotationMetadata() : AnnotationMetadata.EMPTY_METADATA;
            }
            return elementAnnotationMetadataFactory.build(element, newAnnotationMetadata);
        });
    }

    private ClassElement resolveTypeVariable(JavaVisitorContext visitorContext,
                                             Map<String, Map<String, TypeMirror>> genericsInfo,
                                             boolean includeTypeAnnotations,
                                             TypeVariable tv,
                                             TypeMirror declaration) {
        TypeMirror upperBound = tv.getUpperBound();
        Map<String, TypeMirror> boundGenerics = resolveBoundGenerics(visitorContext, genericsInfo);

        TypeMirror bound = boundGenerics.get(tv.toString());
        if (bound != null && bound != declaration) {
            return mirrorToClassElement(bound, visitorContext, genericsInfo, includeTypeAnnotations, true);
        } else {
            // type variable is still free.
            List<? extends TypeMirror> boundsUnresolved = upperBound instanceof IntersectionType ?
                ((IntersectionType) upperBound).getBounds() :
                Collections.singletonList(upperBound);
            List<JavaClassElement> bounds = boundsUnresolved.stream()
                .map(tm -> (JavaClassElement) mirrorToClassElement(tm,
                    visitorContext,
                    genericsInfo,
                    includeTypeAnnotations))
                .collect(Collectors.toList());
            return new JavaGenericPlaceholderElement(tv, bounds, elementAnnotationMetadataFactory, 0);
        }
    }

    private Map<String, TypeMirror> resolveBoundGenerics(JavaVisitorContext visitorContext, Map<String, Map<String, TypeMirror>> genericsInfo) {
        String declaringTypeName = null;
        TypeElement typeElement = visitorContext.getModelUtils().classElementFor(element);
        if (typeElement != null) {
            declaringTypeName = typeElement.getQualifiedName().toString();
        }
        Map<String, TypeMirror> boundGenerics = genericsInfo.get(declaringTypeName);
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
        return element.equals(that.getNativeType());
    }

    @Override
    public int hashCode() {
        return Objects.hash(element);
    }
}
