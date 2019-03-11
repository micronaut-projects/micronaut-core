/*
 * Copyright 2017-2019 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
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
import io.micronaut.inject.annotation.DefaultAnnotationMetadata;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.processing.JavaModelUtils;

import javax.annotation.Nonnull;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.*;
import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Locale;
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

    @Nonnull
    @Override
    public <T extends Annotation> io.micronaut.inject.ast.Element annotate(@Nonnull String annotationType, @Nonnull Consumer<AnnotationValueBuilder<T>> consumer) {
        ArgumentUtils.requireNonNull("annotationType", annotationType);
        ArgumentUtils.requireNonNull("consumer", consumer);
        final AnnotationValueBuilder<T> builder = AnnotationValue.builder(annotationType);
        consumer.accept(builder);
        final AnnotationValue<T> av = builder.build();
        this.annotationMetadata = DefaultAnnotationMetadata.mutateMember(
                annotationMetadata,
                annotationType,
                av.getValues()
        );
        AbstractAnnotationMetadataBuilder.addMutatedMetadata(element, annotationMetadata);
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
     * Obtain the ClassElement for the given mirror.
     *
     * @param returnType The return type
     * @param visitorContext The visitor context
     * @return The class element
     */
    protected ClassElement mirrorToClassElement(TypeMirror returnType, JavaVisitorContext visitorContext) {
        if (returnType instanceof NoType) {
            return new JavaVoidElement();
        } else if (returnType instanceof DeclaredType) {
            DeclaredType dt = (DeclaredType) returnType;
            Element e = dt.asElement();
            List<? extends TypeMirror> typeArguments = dt.getTypeArguments();
            if (e instanceof TypeElement) {
                TypeElement typeElement = (TypeElement) e;
                if (JavaModelUtils.resolveKind(typeElement, ElementKind.ENUM).isPresent()) {
                    return new JavaEnumElement(
                            typeElement,
                            visitorContext.getAnnotationUtils().getAnnotationMetadata(typeElement),
                            visitorContext,
                            typeArguments
                    );
                } else {
                    return new JavaClassElement(
                            typeElement,
                            visitorContext.getAnnotationUtils().getAnnotationMetadata(typeElement),
                            visitorContext,
                            typeArguments
                    );
                }
            }
        } else if (returnType instanceof PrimitiveType) {
            PrimitiveType pt = (PrimitiveType) returnType;
            return JavaPrimitiveElement.valueOf(pt.toString().toUpperCase(Locale.ENGLISH));
        } else if (returnType instanceof TypeVariable) {
            TypeVariable tv = (TypeVariable) returnType;
            TypeMirror upperBound = tv.getUpperBound();
            ClassElement classElement = mirrorToClassElement(upperBound, visitorContext);
            if (classElement != null) {
                return classElement;
            } else {
                return mirrorToClassElement(tv.getLowerBound(), visitorContext);
            }
        } else if (returnType instanceof ArrayType) {
            ArrayType at = (ArrayType) returnType;
            TypeMirror componentType = at.getComponentType();
            ClassElement arrayType = mirrorToClassElement(componentType, visitorContext);
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
        }
        return null;
    }

    private boolean hasModifier(Modifier modifier) {
        return element.getModifiers().contains(modifier);
    }
}
