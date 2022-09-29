package io.micronaut.inject.ast;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.AnnotationValueBuilder;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.inject.annotation.AbstractAnnotationMetadataBuilder;
import io.micronaut.inject.annotation.AnnotationMetadataHierarchy;

import java.lang.annotation.Annotation;
import java.util.function.Consumer;
import java.util.function.Predicate;

public abstract class AbstractElementAnnotationMetadataFactory<K, A> implements ElementAnnotationMetadataFactory {

    protected final boolean isReadOnly;
    protected final AbstractAnnotationMetadataBuilder<K, A> metadataBuilder;

    public AbstractElementAnnotationMetadataFactory(boolean isReadOnly, AbstractAnnotationMetadataBuilder<K, A> metadataBuilder) {
        this.isReadOnly = isReadOnly;
        this.metadataBuilder = metadataBuilder;
    }

    @Override
    public ElementAnnotationMetadata build(Element element) {
        return build(element, null);
    }

    @Override
    public ElementAnnotationMetadata build(Element element, AnnotationMetadata defaultAnnotationMetadata) {
        if (element instanceof ClassElement) {
            ClassElement classElement = (ClassElement) element;
            return buildForClass(defaultAnnotationMetadata, classElement);
        }
        if (element instanceof ConstructorElement) {
            ConstructorElement constructorElement = (ConstructorElement) element;
            return buildForConstructor(defaultAnnotationMetadata, constructorElement);
        }
        if (element instanceof MethodElement) {
            MethodElement methodElement = (MethodElement) element;
            return buildForMethod(defaultAnnotationMetadata, methodElement);
        }
        if (element instanceof FieldElement) {
            FieldElement fieldElement = (FieldElement) element;
            return buildFroField(defaultAnnotationMetadata, fieldElement);
        }
        if (element instanceof ParameterElement) {
            ParameterElement parameterElement = (ParameterElement) element;
            return buildForParameter(defaultAnnotationMetadata, parameterElement);
        }
        if (element instanceof PackageElement) {
            PackageElement packageElement = (PackageElement) element;
            return buildForPackage(defaultAnnotationMetadata, packageElement);
        }
        if (element instanceof PropertyElement) {
            PropertyElement propertyElement = (PropertyElement) element;
            return buildForProperty(defaultAnnotationMetadata, propertyElement);
        }
        if (element instanceof EnumConstantElement) {
            EnumConstantElement enumConstantElement = (EnumConstantElement) element;
            return buildForEnumConstantElement(defaultAnnotationMetadata, enumConstantElement);
        }
        return buildForUnknown(defaultAnnotationMetadata, element);
    }

    protected abstract boolean isSupported(MethodElement methodElement);

    @NonNull
    protected ElementAnnotationMetadata buildForUnknown(@Nullable AnnotationMetadata annotationMetadata, @NonNull Element element) {
        throw new IllegalStateException("Unknown element: " + element.getClass() + " with native type: " + element.getNativeType());
    }

    @NonNull
    private AbstractElementAnnotationMetadata buildForProperty(@Nullable AnnotationMetadata defaultAnnotationMetadata, @NonNull PropertyElement propertyElement) {
        return new AbstractElementAnnotationMetadata(defaultAnnotationMetadata) {

            @Override
            protected AbstractAnnotationMetadataBuilder.CacheEntry lookup() {
                throw new IllegalStateException("Properties should combine annotations for it's elements!");
//                // Use a simple property as a key
//                AbstractAnnotationMetadataBuilder metadataBuilderObject = metadataBuilder;
//                AnnotationMetadata existing = metadataBuilderObject.lookupExisting(nativeOwnerType, propertyElement.getName());
//                if (existing != null) {
//                    return existing;
//                }
//                List<K> parents = new ArrayList<>(3);
//                propertyElement.getField().ifPresent(e -> parents.add((K) e.getNativeType()));
//                propertyElement.getWriteMethod().filter(m -> isSupported(m)).ifPresent(e -> parents.add((K) e.getNativeType()));
//                propertyElement.getReadMethod().filter(m -> isSupported(m)).ifPresent(e -> parents.add((K) e.getNativeType()));
//                K element = parents.remove(parents.size() - 1);
//                if (!parents.isEmpty()) {
//                    return metadataBuilder.buildCombinedNoCache(parents, element);
//                }
//                return metadataBuilder.buildForMethod(nativeOwnerType, nativeType);
            }

            @Override
            protected void addMutatedData() {
                throw new IllegalStateException("Properties should combine annotations for it's elements!");
//                AbstractAnnotationMetadataBuilder metadataBuilderObject = metadataBuilder;
//                metadataBuilderObject.addMutatedMetadata(getNativeOwnerType(), propertyElement.getName(), annotationMetadata);
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
            protected AbstractAnnotationMetadataBuilder.CacheEntry lookup() {
                return metadataBuilder.lookupOrBuildForField(
                    (K) enumConstantElement.getOwningType().getNativeType(),
                    (K) enumConstantElement.getNativeType()
                );
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
            protected AbstractAnnotationMetadataBuilder.CacheEntry lookup() {
                return metadataBuilder.lookupOrBuildForType((K) packageElement.getNativeType());
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
            protected AbstractAnnotationMetadataBuilder.CacheEntry lookup() {
                return metadataBuilder.lookupOrBuildForParameter(
                    (K) parameterElement.getMethodElement().getOwningType().getNativeType(),
                    (K) parameterElement.getMethodElement().getNativeType(),
                    (K) parameterElement.getNativeType()
                );
            }

            @Override
            public String toString() {
                return parameterElement.toString();
            }

        };
    }

    @NonNull
    private AbstractElementAnnotationMetadata buildFroField(@Nullable AnnotationMetadata defaultAnnotationMetadata, @NonNull FieldElement fieldElement) {
        return new AbstractElementAnnotationMetadata(defaultAnnotationMetadata) {

            @Override
            protected AbstractAnnotationMetadataBuilder.CacheEntry lookup() {
                return metadataBuilder.lookupOrBuildForField(
                    (K) fieldElement.getOwningType().getNativeType(),
                    (K) fieldElement.getNativeType()
                );
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
        return new AbstractElementAnnotationMetadata(defaultAnnotationMetadata) {

            @Override
            protected AbstractAnnotationMetadataBuilder.CacheEntry lookup() {
                K ownerType = (K) methodElement.getOwningType().getNativeType();
                AbstractAnnotationMetadataBuilder.CacheEntry methodCacheEntry = metadataBuilder.lookupOrBuildForMethod(
                    ownerType, (K) methodElement.getNativeType());
                AnnotationMetadata methodAnnotations = methodCacheEntry.get();
                if (methodAnnotations instanceof AnnotationMetadataHierarchy) {
                    return methodCacheEntry;
                }
                AbstractAnnotationMetadataBuilder.CacheEntry typeCacheEntry = metadataBuilder.lookupOrBuildForType(ownerType);
                AnnotationMetadata typeAnnotations = typeCacheEntry.get();

                return methodCacheEntry.withAnnotationMetadata(
                    new AnnotationMetadataHierarchy(typeAnnotations, methodAnnotations)
                );
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
            protected AbstractAnnotationMetadataBuilder.CacheEntry lookup() {
                return metadataBuilder.lookupOrBuildForMethod(
                    (K) constructorElement.getOwningType().getNativeType(),
                    (K) constructorElement.getNativeType()
                );
            }

            @Override
            public String toString() {
                return constructorElement.toString();
            }
        };
    }

    @NonNull
    protected AbstractElementAnnotationMetadata buildForClass(@Nullable AnnotationMetadata defaultAnnotationMetadata, @NonNull ClassElement classElement) {
        return new AbstractElementAnnotationMetadata(defaultAnnotationMetadata) {

            @Override
            protected AbstractAnnotationMetadataBuilder.CacheEntry lookup() {
                return metadataBuilder.lookupOrBuildForType((K) classElement.getNativeType());
            }

            @Override
            public String toString() {
                return classElement.toString();
            }
        };
    }

    protected abstract class AbstractElementAnnotationMetadata implements ElementAnnotationMetadata {

        private final boolean readOnly;
        private AnnotationMetadata annotationMetadata;
        private AbstractAnnotationMetadataBuilder.CacheEntry cacheEntry;

        protected AbstractElementAnnotationMetadata(@Nullable AnnotationMetadata annotationMetadata) {
            this(AbstractElementAnnotationMetadataFactory.this.isReadOnly, annotationMetadata);
        }

        protected AbstractElementAnnotationMetadata(boolean readOnly, @Nullable AnnotationMetadata annotationMetadata) {
            this.readOnly = readOnly;
            this.annotationMetadata = annotationMetadata;
        }

        protected abstract AbstractAnnotationMetadataBuilder.CacheEntry lookup();

        private void updateAnnotationCaches() {
            if (!readOnly) {
                addMutatedData();
            }
        }

        protected void addMutatedData() {
            getCacheEntry().update(annotationMetadata);

        }

        private AbstractAnnotationMetadataBuilder.CacheEntry getCacheEntry() {
            if (cacheEntry == null) {
                cacheEntry = lookup();
            }
            return cacheEntry;
        }

        @Override
        public AnnotationMetadata get() {
            if (annotationMetadata == null) {
                annotationMetadata = getCacheEntry().get();
            }
            return annotationMetadata;
        }

        @Override
        public <T extends Annotation> AnnotationMetadata annotate(@NonNull String annotationType, @NonNull Consumer<AnnotationValueBuilder<T>> consumer) {
            ArgumentUtils.requireNonNull("annotationType", annotationType);
            AnnotationValueBuilder<T> builder = AnnotationValue.builder(annotationType);
            //noinspection ConstantConditions
            if (consumer != null) {
                consumer.accept(builder);
                AnnotationValue<T> av = builder.build();
                this.annotationMetadata = metadataBuilder.annotate(get(), av);
                updateAnnotationCaches();
            }
            return this.annotationMetadata;
        }

        @Override
        public <T extends Annotation> AnnotationMetadata annotate(AnnotationValue<T> annotationValue) {
            ArgumentUtils.requireNonNull("annotationValue", annotationValue);
            this.annotationMetadata = metadataBuilder.annotate(get(), annotationValue);
            updateAnnotationCaches();
            return this.annotationMetadata;
        }

        @Override
        public AnnotationMetadata removeAnnotation(@NonNull String annotationType) {
            ArgumentUtils.requireNonNull("annotationType", annotationType);
            this.annotationMetadata = metadataBuilder.removeAnnotation(get(), annotationType);
            updateAnnotationCaches();
            return this.annotationMetadata;
        }

        @Override
        public <T extends Annotation> AnnotationMetadata removeAnnotationIf(@NonNull Predicate<AnnotationValue<T>> predicate) {
            ArgumentUtils.requireNonNull("predicate", predicate);
            this.annotationMetadata = metadataBuilder.removeAnnotationIf(get(), predicate);
            updateAnnotationCaches();
            return this.annotationMetadata;

        }

        @Override
        public AnnotationMetadata removeStereotype(@NonNull String annotationType) {
            ArgumentUtils.requireNonNull("annotationType", annotationType);
            this.annotationMetadata = metadataBuilder.removeStereotype(get(), annotationType);
            updateAnnotationCaches();
            return this.annotationMetadata;
        }

        @Override
        public AnnotationMetadata replaceAnnotations(AnnotationMetadata annotationMetadata) {
            ArgumentUtils.requireNonNull("annotationType", annotationMetadata);
            this.annotationMetadata = annotationMetadata;
            updateAnnotationCaches();
            return this.annotationMetadata;
        }
    }

}
