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
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.inject.annotation.AnnotationMetadataHierarchy;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.FieldElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.ast.PropertyElement;
import io.micronaut.inject.ast.annotation.ElementAnnotationMetadata;
import io.micronaut.inject.ast.annotation.ElementAnnotationMetadataFactory;
import io.micronaut.inject.ast.annotation.MutableAnnotationMetadataDelegate;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Models a {@link PropertyElement} for Java.
 *
 * @author graemerocher
 * @since 1.0
 */
@Internal
final class JavaPropertyElement extends AbstractJavaElement implements PropertyElement {

    private final ClassElement type;
    private final String name;
    private final AccessKind readAccessKind;
    private final AccessKind writeAccessKind;
    private final ClassElement owningElement;
    @Nullable
    private final MethodElement getter;
    @Nullable
    private final MethodElement setter;
    @Nullable
    private final FieldElement field;
    private final boolean excluded;
    private final ElementAnnotationMetadata annotationMetadata;

    JavaPropertyElement(ClassElement owningElement,
                        ClassElement type,
                        MethodElement getter,
                        MethodElement setter,
                        FieldElement field,
                        ElementAnnotationMetadataFactory annotationMetadataFactory,
                        String name,
                        AccessKind readAccessKind,
                        AccessKind writeAccessKind,
                        boolean excluded,
                        JavaVisitorContext visitorContext) {
        super(selectNativeType(getter, setter, field), annotationMetadataFactory, visitorContext);
        this.type = type;
        this.getter = getter;
        this.setter = setter;
        this.field = field;
        this.name = name;
        this.readAccessKind = readAccessKind;
        this.writeAccessKind = writeAccessKind;
        this.owningElement = owningElement;
        this.excluded = excluded;
        List<MutableAnnotationMetadataDelegate<?>> elements = new ArrayList<>(3);
        if (setter != null) {
            elements.add(setter);
            ParameterElement[] parameters = setter.getParameters();
            if (parameters.length > 0) {
                ParameterElement parameter = parameters[0];
                MutableAnnotationMetadataDelegate<?> typeAnnotationMetadata = parameter.getType().getTypeAnnotationMetadata();
                if (!typeAnnotationMetadata.isEmpty()) {
                    elements.add(typeAnnotationMetadata);
                }
            }
        }
        if (field != null) {
            elements.add(field);
            MutableAnnotationMetadataDelegate<?> typeAnnotationMetadata = field.getType().getTypeAnnotationMetadata();
            if (!typeAnnotationMetadata.isEmpty()) {
                elements.add(typeAnnotationMetadata);
            }
        }
        if (getter != null) {
            elements.add(getter);
            MutableAnnotationMetadataDelegate<?> typeAnnotationMetadata = getter.getReturnType().getTypeAnnotationMetadata();
            if (!typeAnnotationMetadata.isEmpty()) {
                elements.add(typeAnnotationMetadata);
            }
        }
        // The instance AnnotationMetadata of each element can change after a modification
        // Set annotation metadata as actual elements so the changes are reflected
        AnnotationMetadata propertyAnnotationMetadata;
        if (elements.size() == 1) {
            propertyAnnotationMetadata = elements.iterator().next();
        } else {
            propertyAnnotationMetadata = new AnnotationMetadataHierarchy(
                true,
                elements.stream().map(e -> {
                    if (e instanceof MethodElement methodElement) {
                        return new AnnotationMetadataDelegate() {
                            @Override
                            public AnnotationMetadata getAnnotationMetadata() {
                                // Exclude type metadata
                                return methodElement.getAnnotationMetadata().getDeclaredMetadata();
                            }
                        };
                    }
                    return e;
                }).toArray(AnnotationMetadata[]::new)
            );
        }
        annotationMetadata = new ElementAnnotationMetadata() {

            @Override
            public <T extends Annotation> io.micronaut.inject.ast.Element annotate(AnnotationValue<T> annotationValue) {
                for (MutableAnnotationMetadataDelegate<?> am : elements) {
                    am.annotate(annotationValue);
                }
                return JavaPropertyElement.this;
            }

            @Override
            public <T extends Annotation> io.micronaut.inject.ast.Element annotate(String annotationType, Consumer<AnnotationValueBuilder<T>> consumer) {
                for (MutableAnnotationMetadataDelegate<?> am : elements) {
                    am.annotate(annotationType, consumer);
                }
                return JavaPropertyElement.this;
            }

            @Override
            public <T extends Annotation> io.micronaut.inject.ast.Element annotate(Class<T> annotationType) {
                for (MutableAnnotationMetadataDelegate<?> am : elements) {
                    am.annotate(annotationType);
                }
                return JavaPropertyElement.this;
            }

            @Override
            public io.micronaut.inject.ast.Element annotate(String annotationType) {
                for (MutableAnnotationMetadataDelegate<?> am : elements) {
                    am.annotate(annotationType);
                }
                return JavaPropertyElement.this;
            }

            @Override
            public <T extends Annotation> io.micronaut.inject.ast.Element annotate(Class<T> annotationType, Consumer<AnnotationValueBuilder<T>> consumer) {
                for (MutableAnnotationMetadataDelegate<?> am : elements) {
                    am.annotate(annotationType, consumer);
                }
                return JavaPropertyElement.this;
            }

            @Override
            public io.micronaut.inject.ast.Element removeAnnotation(String annotationType) {
                for (MutableAnnotationMetadataDelegate<?> am : elements) {
                    am.removeAnnotation(annotationType);
                }
                return JavaPropertyElement.this;
            }

            @Override
            public <T extends Annotation> io.micronaut.inject.ast.Element removeAnnotationIf(Predicate<AnnotationValue<T>> predicate) {
                for (MutableAnnotationMetadataDelegate<?> am : elements) {
                    am.removeAnnotationIf(predicate);
                }
                return JavaPropertyElement.this;
            }

            @Override
            public AnnotationMetadata getAnnotationMetadata() {
                return propertyAnnotationMetadata;
            }
        };
    }

    @Override
    protected AbstractJavaElement copyThis() {
        return new JavaPropertyElement(owningElement, type, getter, setter, field, elementAnnotationMetadataFactory, name, readAccessKind, writeAccessKind, excluded, visitorContext);
    }

    @Override
    public PropertyElement withAnnotationMetadata(AnnotationMetadata annotationMetadata) {
        return (PropertyElement) super.withAnnotationMetadata(annotationMetadata);
    }

    private static JavaNativeElement selectNativeType(MethodElement getter,
                                            MethodElement setter,
                                            FieldElement field) {
        if (getter != null) {
            return (JavaNativeElement) getter.getNativeType();
        }
        if (setter != null) {
            return (JavaNativeElement) setter.getNativeType();
        }
        if (field != null) {
            return (JavaNativeElement) field.getNativeType();
        }
        throw new IllegalStateException();
    }

    @Override
    public boolean isExcluded() {
        return excluded;
    }

    @Override
    public ElementAnnotationMetadata getElementAnnotationMetadata() {
        return annotationMetadata;
    }

    @Override
    public ClassElement getType() {
        return type;
    }

    @Override
    public ClassElement getGenericType() {
        return type; // Already generic
    }

    @Override
    public Optional<FieldElement> getField() {
        return Optional.ofNullable(field);
    }

    @Override
    public Optional<MethodElement> getWriteMethod() {
        return Optional.ofNullable(setter);
    }

    @Override
    public Optional<MethodElement> getReadMethod() {
        return Optional.ofNullable(getter);
    }

    @Override
    public boolean isPrimitive() {
        return getType().isPrimitive();
    }

    @Override
    public boolean isArray() {
        return getType().isArray();
    }

    @Override
    public int getArrayDimensions() {
        return getType().getArrayDimensions();
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return name;
    }

    @Override
    public AccessKind getReadAccessKind() {
        return readAccessKind;
    }

    @Override
    public AccessKind getWriteAccessKind() {
        return writeAccessKind;
    }

    @Override
    public boolean isReadOnly() {
        switch (writeAccessKind) {
            case METHOD:
                return setter == null;
            case FIELD:
                return field == null || field.isFinal();
            default:
                throw new IllegalStateException();
        }
    }

    @Override
    public boolean isWriteOnly() {
        switch (readAccessKind) {
            case METHOD:
                return getter == null;
            case FIELD:
                return field == null;
            default:
                throw new IllegalStateException();
        }
    }

    @Override
    public ClassElement getDeclaringType() {
        if (field != null) {
            return field.getDeclaringType();
        }
        if (getter != null) {
            return getter.getDeclaringType();
        }
        if (setter != null) {
            return setter.getDeclaringType();
        }
        throw new IllegalStateException();
    }

    @Override
    public ClassElement getOwningType() {
        return owningElement;
    }

}
