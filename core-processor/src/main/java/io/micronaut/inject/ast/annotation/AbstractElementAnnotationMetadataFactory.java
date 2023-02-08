/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.inject.ast.annotation;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.AnnotationValueBuilder;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.inject.annotation.AbstractAnnotationMetadataBuilder;
import io.micronaut.inject.annotation.AnnotationMetadataHierarchy;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ConstructorElement;
import io.micronaut.inject.ast.Element;
import io.micronaut.inject.ast.EnumConstantElement;
import io.micronaut.inject.ast.FieldElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.PackageElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.ast.PropertyElement;

import java.lang.annotation.Annotation;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Abstract element annotation metadata factory.
 *
 * @param <K> The element type
 * @param <A> The annotation type
 * @author Denis Stepanov
 * @since 4.0.0
 */
public abstract class AbstractElementAnnotationMetadataFactory<K, A> implements ElementAnnotationMetadataFactory {

    protected final boolean isReadOnly;
    protected final AbstractAnnotationMetadataBuilder<K, A> metadataBuilder;

    protected AbstractElementAnnotationMetadataFactory(boolean isReadOnly, AbstractAnnotationMetadataBuilder<K, A> metadataBuilder) {
        this.isReadOnly = isReadOnly;
        this.metadataBuilder = metadataBuilder;
    }

    @Override
    public ElementAnnotationMetadata build(Element element) {
        return build(element, null);
    }

    @Override
    public ElementAnnotationMetadata build(Element element, AnnotationMetadata defaultAnnotationMetadata) {
        if (element instanceof ClassElement classElement) {
            return buildForClass(defaultAnnotationMetadata, classElement);
        }
        if (element instanceof ConstructorElement constructorElement) {
            return buildForConstructor(defaultAnnotationMetadata, constructorElement);
        }
        if (element instanceof MethodElement methodElement) {
            return buildForMethod(defaultAnnotationMetadata, methodElement);
        }
        if (element instanceof FieldElement fieldElement) {
            return buildForField(defaultAnnotationMetadata, fieldElement);
        }
        if (element instanceof ParameterElement parameterElement) {
            return buildForParameter(defaultAnnotationMetadata, parameterElement);
        }
        if (element instanceof PackageElement packageElement) {
            return buildForPackage(defaultAnnotationMetadata, packageElement);
        }
        if (element instanceof PropertyElement propertyElement) {
            return buildForProperty(defaultAnnotationMetadata, propertyElement);
        }
        if (element instanceof EnumConstantElement enumConstantElement) {
            return buildForEnumConstantElement(defaultAnnotationMetadata, enumConstantElement);
        }
        throw new IllegalStateException("Unknown element: " + element.getClass() + " with native type: " + element.getNativeType());
    }

    /**
     * Lookup annotation metadata for the package.
     * @param packageElement The element
     * @return The annotation metadata
     */
    protected AbstractAnnotationMetadataBuilder.CachedAnnotationMetadata lookupForPackage(PackageElement packageElement) {
        return metadataBuilder.lookupOrBuildForType((K) packageElement.getNativeType());
    }

    /**
     * Lookup annotation metadata for the parameter.
     * @param parameterElement The element
     * @return The annotation metadata
     */
    protected AbstractAnnotationMetadataBuilder.CachedAnnotationMetadata lookupForParameter(ParameterElement parameterElement) {
        return metadataBuilder.lookupOrBuildForParameter(
                (K) parameterElement.getMethodElement().getOwningType().getNativeType(),
                (K) parameterElement.getMethodElement().getNativeType(),
                (K) parameterElement.getNativeType()
        );
    }

    /**
     * Lookup annotation metadata for the field.
     * @param fieldElement The element
     * @return The annotation metadata
     */
    protected AbstractAnnotationMetadataBuilder.CachedAnnotationMetadata lookupForField(FieldElement fieldElement) {
        return metadataBuilder.lookupOrBuildForField(
                (K) fieldElement.getOwningType().getNativeType(),
                (K) fieldElement.getNativeType()
        );
    }

    /**
     * Lookup annotation metadata for the method.
     * @param methodElement The element
     * @return The annotation metadata
     */
    protected AbstractAnnotationMetadataBuilder.CachedAnnotationMetadata lookupForMethod(MethodElement methodElement) {
        return metadataBuilder.lookupOrBuildForMethod((K) methodElement.getOwningType().getNativeType(), (K) methodElement.getNativeType());
    }

    /**
     * Lookup annotation metadata for the class.
     * @param classElement The element
     * @return The annotation metadata
     */
    protected AbstractAnnotationMetadataBuilder.CachedAnnotationMetadata lookupForClass(ClassElement classElement) {
        return metadataBuilder.lookupOrBuildForType((K) classElement.getNativeType());
    }

    @NonNull
    private AbstractElementAnnotationMetadata buildForProperty(@Nullable AnnotationMetadata defaultAnnotationMetadata, @NonNull PropertyElement propertyElement) {
        return new AbstractElementAnnotationMetadata(defaultAnnotationMetadata) {

            @Override
            protected AbstractAnnotationMetadataBuilder.CachedAnnotationMetadata lookup() {
                throw new IllegalStateException("Properties should combine annotations for it's elements!");
            }

            @Override
            public String toString() {
                return propertyElement.toString();
            }
        };
    }

    @NonNull
    private AbstractElementAnnotationMetadata buildForEnumConstantElement(@Nullable AnnotationMetadata defaultAnnotationMetadata,
                                                                          @NonNull EnumConstantElement enumConstantElement) {
        return new AbstractElementAnnotationMetadata(defaultAnnotationMetadata) {

            @Override
            protected AbstractAnnotationMetadataBuilder.CachedAnnotationMetadata lookup() {
                return lookupForField(enumConstantElement);
            }

            @Override
            public String toString() {
                return enumConstantElement.toString();
            }
        };
    }

    @NonNull
    private AbstractElementAnnotationMetadata buildForPackage(@Nullable AnnotationMetadata defaultAnnotationMetadata, @NonNull PackageElement packageElement) {
        return new AbstractElementAnnotationMetadata(defaultAnnotationMetadata) {

            @Override
            protected AbstractAnnotationMetadataBuilder.CachedAnnotationMetadata lookup() {
                return lookupForPackage(packageElement);
            }

            @Override
            public String toString() {
                return packageElement.toString();
            }
        };
    }

    @NonNull
    private AbstractElementAnnotationMetadata buildForParameter(@Nullable AnnotationMetadata defaultAnnotationMetadata, @NonNull ParameterElement parameterElement) {
        return new AbstractElementAnnotationMetadata(defaultAnnotationMetadata) {

            @Override
            protected AbstractAnnotationMetadataBuilder.CachedAnnotationMetadata lookup() {
                return lookupForParameter(parameterElement);
            }

            @Override
            public String toString() {
                return parameterElement.toString();
            }

        };
    }

    @NonNull
    private AbstractElementAnnotationMetadata buildForField(@Nullable AnnotationMetadata defaultAnnotationMetadata, @NonNull FieldElement fieldElement) {
        return new AbstractElementAnnotationMetadata(defaultAnnotationMetadata) {

            @Override
            protected AbstractAnnotationMetadataBuilder.CachedAnnotationMetadata lookup() {
                return lookupForField(fieldElement);
            }

            @Override
            public String toString() {
                return fieldElement.toString();
            }
        };
    }

    @NonNull
    private AbstractElementAnnotationMetadata buildForMethod(@Nullable AnnotationMetadata defaultAnnotationMetadata,
                                                             @NonNull MethodElement methodElement) {
        return new AbstractElementAnnotationMetadata(isReadOnly, methodElement.getOwningType(), defaultAnnotationMetadata) {

            @Override
            protected AbstractAnnotationMetadataBuilder.CachedAnnotationMetadata lookup() {
                return lookupForMethod(methodElement);
            }

            @Override
            public String toString() {
                return methodElement.toString();
            }
        };
    }

    @NonNull
    private AbstractElementAnnotationMetadata buildForConstructor(@Nullable AnnotationMetadata defaultAnnotationMetadata,
                                                                  @NonNull ConstructorElement constructorElement) {
        return new AbstractElementAnnotationMetadata(defaultAnnotationMetadata) {

            @Override
            protected AbstractAnnotationMetadataBuilder.CachedAnnotationMetadata lookup() {
                return lookupForMethod(constructorElement);
            }

            @Override
            public String toString() {
                return constructorElement.toString();
            }
        };
    }

    @NonNull
    private AbstractElementAnnotationMetadata buildForClass(@Nullable AnnotationMetadata defaultAnnotationMetadata, @NonNull ClassElement classElement) {
        return new AbstractElementAnnotationMetadata(defaultAnnotationMetadata) {

            @Override
            protected AbstractAnnotationMetadataBuilder.CachedAnnotationMetadata lookup() {
                return lookupForClass(classElement);
            }

            @Override
            public String toString() {
                return classElement.toString();
            }
        };
    }


    /**
     * Abstract implementation of {@link ElementAnnotationMetadata}.
     *
     * @author Denis Stepanov
     * @since 4.0.0
     */
    protected abstract class AbstractElementAnnotationMetadata implements ElementAnnotationMetadata {

        protected AnnotationMetadata preloadedAnnotationMetadata;
        private final boolean readOnly;
        private AbstractAnnotationMetadataBuilder.CachedAnnotationMetadata cachedAnnotationMetadata;
        private final ClassElement classElement;

        protected AbstractElementAnnotationMetadata(@Nullable AnnotationMetadata annotationMetadata) {
            this(AbstractElementAnnotationMetadataFactory.this.isReadOnly, annotationMetadata);
        }

        protected AbstractElementAnnotationMetadata(boolean readOnly, @Nullable AnnotationMetadata annotationMetadata) {
            this(readOnly, null, annotationMetadata);
        }

        protected AbstractElementAnnotationMetadata(boolean readOnly,
                                                    ClassElement classElement,
                                                    @Nullable AnnotationMetadata annotationMetadata) {
            this.readOnly = readOnly;
            this.classElement = classElement;
            this.preloadedAnnotationMetadata = annotationMetadata;
            if (preloadedAnnotationMetadata instanceof AbstractAnnotationMetadataBuilder.CachedAnnotationMetadata) {
                throw new IllegalStateException();
            }
            if (preloadedAnnotationMetadata instanceof ElementAnnotationMetadata) {
                throw new IllegalStateException();
            }
        }

        protected abstract AbstractAnnotationMetadataBuilder.CachedAnnotationMetadata lookup();

        private AbstractAnnotationMetadataBuilder.CachedAnnotationMetadata getCacheEntry() {
            if (cachedAnnotationMetadata == null) {
                cachedAnnotationMetadata = lookup();
            }
            return cachedAnnotationMetadata;
        }

        @Override
        public AnnotationMetadata getAnnotationMetadata() {
            if (preloadedAnnotationMetadata != null) {
                if (classElement != null) {
                    if (preloadedAnnotationMetadata instanceof AnnotationMetadataHierarchy) {
                        return preloadedAnnotationMetadata;
                    }
                    return new AnnotationMetadataHierarchy(classElement, preloadedAnnotationMetadata);
                }
                return preloadedAnnotationMetadata;
            }
            if (classElement != null) {
                return new AnnotationMetadataHierarchy(classElement, getCacheEntry());
            }
            return getCacheEntry();
        }

        private AnnotationMetadata getAnnotationMetadataToModify() {
            if (preloadedAnnotationMetadata != null) {
                if (preloadedAnnotationMetadata instanceof AnnotationMetadataHierarchy) {
                    return preloadedAnnotationMetadata.getDeclaredMetadata().copyAnnotationMetadata();
                }
                return preloadedAnnotationMetadata;
            }
            return getCacheEntry().copyAnnotationMetadata();
        }

        private AnnotationMetadata replaceAnnotationsInternal(AnnotationMetadata annotationMetadata) {
            if (annotationMetadata instanceof AbstractAnnotationMetadataBuilder.CachedAnnotationMetadata) {
                throw new IllegalStateException();
            }
            if (annotationMetadata instanceof MutableAnnotationMetadataDelegate) {
                throw new IllegalStateException();
            }
            if (annotationMetadata.isEmpty()) {
                annotationMetadata = EMPTY_METADATA;
            }
            if (!readOnly) {
                if (annotationMetadata instanceof AnnotationMetadataHierarchy) {
                    throw new IllegalStateException("Not supported to cache AnnotationMetadataHierarchy");
                }
                getCacheEntry().update(annotationMetadata);
                preloadedAnnotationMetadata = null;
            } else {
                preloadedAnnotationMetadata = annotationMetadata;
            }
            return getAnnotationMetadata();
        }

        @Override
        @SuppressWarnings("java:S1192")
        public <T extends Annotation> AnnotationMetadata annotate(@NonNull String annotationType, @NonNull Consumer<AnnotationValueBuilder<T>> consumer) {
            ArgumentUtils.requireNonNull("annotationType", annotationType);
            AnnotationValueBuilder<T> builder = AnnotationValue.builder(annotationType, metadataBuilder.getRetentionPolicy(annotationType));
            //noinspection ConstantConditions
            if (consumer != null) {
                consumer.accept(builder);
                AnnotationValue<T> av = builder.build();
                return replaceAnnotationsInternal(metadataBuilder.annotate(getAnnotationMetadataToModify(), av));
            }
            return getAnnotationMetadata();
        }

        @Override
        public <T extends Annotation> AnnotationMetadata annotate(AnnotationValue<T> annotationValue) {
            ArgumentUtils.requireNonNull("annotationValue", annotationValue);
            return replaceAnnotationsInternal(metadataBuilder.annotate(getAnnotationMetadataToModify(), annotationValue));
        }

        @Override
        public AnnotationMetadata removeAnnotation(@NonNull String annotationType) {
            ArgumentUtils.requireNonNull("annotationType", annotationType);
            return replaceAnnotationsInternal(metadataBuilder.removeAnnotation(getAnnotationMetadataToModify(), annotationType));
        }

        @Override
        public <T extends Annotation> AnnotationMetadata removeAnnotationIf(@NonNull Predicate<AnnotationValue<T>> predicate) {
            ArgumentUtils.requireNonNull("predicate", predicate);
            return replaceAnnotationsInternal(metadataBuilder.removeAnnotationIf(getAnnotationMetadataToModify(), predicate));
        }

        @Override
        public AnnotationMetadata removeStereotype(@NonNull String annotationType) {
            ArgumentUtils.requireNonNull("annotationType", annotationType);
            return replaceAnnotationsInternal(metadataBuilder.removeStereotype(getAnnotationMetadataToModify(), annotationType));
        }

    }

}
