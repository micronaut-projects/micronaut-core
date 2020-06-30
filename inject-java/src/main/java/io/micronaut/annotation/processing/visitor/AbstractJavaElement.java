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
import io.micronaut.core.annotation.AnnotationMetadataDelegate;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.AnnotationValueBuilder;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.inject.annotation.AbstractAnnotationMetadataBuilder;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.MemberElement;

import edu.umd.cs.findbugs.annotations.NonNull;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.*;
import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * An abstract class for other elements to extend from.
 *
 * @author James Kleeh
 * @author graemerocher
 * @since 1.0
 */
public abstract class AbstractJavaElement implements io.micronaut.inject.ast.Element, AnnotationMetadataDelegate {

    private final Element element;
    private final JavaVisitorContext visitorContext;
    private AnnotationMetadata annotationMetadata;

    /**
     * @param element            The {@link Element}
     * @param annotationMetadata The Annotation metadata
     * @param visitorContext     The Java visitor context
     */
    AbstractJavaElement(Element element, AnnotationMetadata annotationMetadata, JavaVisitorContext visitorContext) {
        this.element = element;
        this.annotationMetadata = annotationMetadata;
        this.visitorContext = visitorContext;
    }

    @NonNull
    @Override
    public <T extends Annotation> io.micronaut.inject.ast.Element annotate(@NonNull String annotationType, @NonNull Consumer<AnnotationValueBuilder<T>> consumer) {
        ArgumentUtils.requireNonNull("annotationType", annotationType);
        ArgumentUtils.requireNonNull("consumer", consumer);

        final AnnotationValueBuilder<T> builder = AnnotationValue.builder(annotationType);
        consumer.accept(builder);
        final AnnotationValue<T> av = builder.build();
        this.annotationMetadata = visitorContext
                .getAnnotationUtils()
                .newAnnotationBuilder()
                .annotate(annotationMetadata, av);

        String declaringTypeName = this instanceof MemberElement ? ((MemberElement) this).getOwningType().getName() : getName();
        AbstractAnnotationMetadataBuilder.addMutatedMetadata(declaringTypeName, element, annotationMetadata);
        visitorContext.getAnnotationUtils().invalidateMetadata(element);
        return this;
    }

    @Override
    public String getName() {
        return element.getSimpleName().toString();
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
        return annotationMetadata;
    }

    @Override
    public String toString() {
        return element.toString();
    }

    /**
     * Returns a class element with aligned generic information.
     * @param typeMirror The type mirror
     * @param visitorContext The visitor context
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
                declaredGenericInfo
        );
    }

    /**
     * Obtain the ClassElement for the given mirror.
     *
     * @param returnType The return type
     * @param visitorContext The visitor context
     * @return The class element
     */
    protected @NonNull ClassElement mirrorToClassElement(TypeMirror returnType, JavaVisitorContext visitorContext) {
        return mirrorToClassElement(returnType, visitorContext, Collections.emptyMap());
    }

    /**
     * Obtain the ClassElement for the given mirror.
     *
     * @param returnType The return type
     * @param visitorContext The visitor context
     * @param genericsInfo The generic informatino
     * @return The class element
     */
    protected @NonNull ClassElement mirrorToClassElement(TypeMirror returnType, JavaVisitorContext visitorContext, Map<String, Map<String, TypeMirror>> genericsInfo) {
        if (genericsInfo == null) {
            genericsInfo = Collections.emptyMap();
        }
        if (returnType instanceof NoType) {
            return new JavaVoidElement();
        } else if (returnType instanceof DeclaredType) {
            DeclaredType dt = (DeclaredType) returnType;
            Element e = dt.asElement();
            List<? extends TypeMirror> typeArguments = dt.getTypeArguments();
            if (e instanceof TypeElement) {
                TypeElement typeElement = (TypeElement) e;
                Map<String, TypeMirror> boundGenerics = resolveBoundGenerics(visitorContext, genericsInfo);
                if (visitorContext.getModelUtils().resolveKind(typeElement, ElementKind.ENUM).isPresent()) {
                    return new JavaEnumElement(
                            typeElement,
                            visitorContext.getAnnotationUtils().getAnnotationMetadata(typeElement),
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
                            visitorContext.getAnnotationUtils().getAnnotationMetadata(typeElement),
                            visitorContext,
                            genericsInfo
                    );
                }
            }
        } else if (returnType instanceof TypeVariable) {
            TypeVariable tv = (TypeVariable) returnType;
            TypeMirror upperBound = tv.getUpperBound();
            Map<String, TypeMirror> boundGenerics = resolveBoundGenerics(visitorContext, genericsInfo);

            TypeMirror bound = boundGenerics.get(tv.toString());
            if (bound != null) {
                return mirrorToClassElement(bound, visitorContext, genericsInfo);
            } else {

                ClassElement classElement = mirrorToClassElement(upperBound, visitorContext, genericsInfo);
                if (classElement != null) {
                    return classElement;
                } else {
                    return mirrorToClassElement(tv.getLowerBound(), visitorContext, genericsInfo);
                }
            }

        } else if (returnType instanceof ArrayType) {
            ArrayType at = (ArrayType) returnType;
            TypeMirror componentType = at.getComponentType();
            ClassElement arrayType = mirrorToClassElement(componentType, visitorContext, genericsInfo);
            if (arrayType != null) {
                if (arrayType instanceof JavaPrimitiveElement) {
                    JavaPrimitiveElement jpe = (JavaPrimitiveElement) arrayType;
                    return jpe.toArray();
                } else {
                    return new JavaClassElement((TypeElement) arrayType.getNativeType(), arrayType, visitorContext) {
                        @Override
                        public boolean isArray() {
                            return true;
                        }
                    };
                }
            }
        } else if (returnType instanceof PrimitiveType) {
            PrimitiveType pt = (PrimitiveType) returnType;
            return JavaPrimitiveElement.valueOf(pt.getKind().name());
        }
        return new JavaVoidElement();
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
}
