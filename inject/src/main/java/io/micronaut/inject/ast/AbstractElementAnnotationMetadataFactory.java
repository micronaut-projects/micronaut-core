package io.micronaut.inject.ast;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.AnnotationValueBuilder;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.inject.annotation.AbstractAnnotationMetadataBuilder;
import io.micronaut.inject.annotation.AnnotationMetadataHierarchy;
import org.jetbrains.annotations.NotNull;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;
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
            protected AnnotationMetadata createOnMissing(K nativeOwnerType, K nativeType) {
                AnnotationMetadata existing = metadataBuilder.lookupExisting(nativeOwnerType, getElementAsString());
                if (existing != null) {
                    return existing;
                }
                List<K> parents = new ArrayList<>(3);
                propertyElement.getField().ifPresent(e -> parents.add((K) e.getNativeType()));
                propertyElement.getWriteMethod().filter(m -> isSupported(m)).ifPresent(e -> parents.add((K) e.getNativeType()));
                propertyElement.getReadMethod().filter(m -> isSupported(m)).ifPresent(e -> parents.add((K) e.getNativeType()));
                K element = parents.remove(parents.size() - 1);
                if (!parents.isEmpty()) {
                    return metadataBuilder.buildCombinedNoCache(parents, element);
                }
                return metadataBuilder.buildForMethod(nativeOwnerType, nativeType);
            }

            @Override
            protected void addMutatedData() {
                metadataBuilder.addMutatedMetadata(getNativeOwnerType(), getElementAsString(), annotationMetadata);
            }

            @NotNull
            private String getElementAsString() {
                return "property$ " + propertyElement.getName();
            }

            @Override
            protected K getNativeOwnerType() {
                return (K) propertyElement.getOwningType().getNativeType();
            }

            @Override
            protected K getNativeType() {
                return (K) propertyElement.getNativeType();
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
            protected AnnotationMetadata createOnMissing(K nativeOwnerType, K nativeType) {
                return metadataBuilder.build(nativeOwnerType, nativeType);
            }

            @Override
            protected K getNativeOwnerType() {
                return (K) enumConstantElement.getOwningType().getNativeType();
            }

            @Override
            protected K getNativeType() {
                return (K) enumConstantElement.getNativeType();
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
            protected AnnotationMetadata createOnMissing(K nativeOwnerType, K nativeType) {
                return metadataBuilder.buildForType((K) packageElement.getNativeType());
            }

            @Override
            protected K getNativeOwnerType() {
                return (K) packageElement.getNativeType();
            }

            @Override
            protected K getNativeType() {
                return (K) packageElement.getNativeType();
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
            protected AnnotationMetadata createOnMissing(K nativeOwnerType, K nativeType) {
                return metadataBuilder.buildForParameter(nativeOwnerType, nativeType);
            }

            @Override
            protected K getNativeOwnerType() {
                return (K) parameterElement.getMethodElement().getOwningType().getNativeType();
            }

            @Override
            protected K getNativeType() {
                return (K) parameterElement.getNativeType();
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
            protected AnnotationMetadata createOnMissing(K nativeOwnerType, K nativeType) {
                return metadataBuilder.build(nativeOwnerType, nativeType);
            }

            @Override
            protected K getNativeOwnerType() {
                return (K) fieldElement.getOwningType().getNativeType();
            }

            @Override
            protected K getNativeType() {
                return (K) fieldElement.getNativeType();
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
            protected AnnotationMetadata createOnMissing(K nativeOwnerType, K nativeType) {
                AnnotationMetadata methodAnnotations = metadataBuilder.buildForMethod(nativeOwnerType, nativeType);
                if (methodAnnotations instanceof AnnotationMetadataHierarchy) {
                    return methodAnnotations;
                }
                AnnotationMetadata typeAnnotations = metadataBuilder.buildForType(nativeOwnerType);
                return new AnnotationMetadataHierarchy(typeAnnotations, methodAnnotations);
            }

            @Override
            protected K getNativeOwnerType() {
                return (K) methodElement.getOwningType().getNativeType();
            }

            @Override
            protected K getNativeType() {
                return (K) methodElement.getNativeType();
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
            protected AnnotationMetadata createOnMissing(K nativeOwnerType, K nativeType) {
                return metadataBuilder.build(nativeOwnerType, nativeType);
            }

            @Override
            protected K getNativeOwnerType() {
                return (K) constructorElement.getOwningType().getNativeType();
            }

            @Override
            protected K getNativeType() {
                return (K) constructorElement.getNativeType();
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
            protected AnnotationMetadata createOnMissing(K nativeOwnerType, K nativeType) {
                return metadataBuilder.buildForType((K) classElement.getNativeType());
            }

            @Override
            protected K getNativeOwnerType() {
                return (K) classElement.getNativeType();
            }

            @Override
            protected K getNativeType() {
                return (K) classElement.getNativeType();
            }

            @Override
            public String toString() {
                return classElement.toString();
            }
        };
    }

    protected abstract class AbstractElementAnnotationMetadata implements ElementAnnotationMetadata {

        private final boolean readOnly;
        protected AnnotationMetadata annotationMetadata;

        protected AbstractElementAnnotationMetadata(@Nullable AnnotationMetadata annotationMetadata) {
            this(AbstractElementAnnotationMetadataFactory.this.isReadOnly, annotationMetadata);
        }

        protected AbstractElementAnnotationMetadata(boolean readOnly, @Nullable AnnotationMetadata annotationMetadata) {
            this.readOnly = readOnly;
            this.annotationMetadata = annotationMetadata;
        }

        protected abstract AnnotationMetadata createOnMissing(K nativeOwnerType, K nativeType);

        protected abstract K getNativeOwnerType();

        protected abstract K getNativeType();

        private void updateAnnotationCaches() {
            if (!readOnly) {
                addMutatedData();
            }
        }

        protected void addMutatedData() {
            metadataBuilder.addMutatedMetadata(getNativeOwnerType(), getNativeType(), annotationMetadata);
        }

        @Override
        public AnnotationMetadata get() {
            if (annotationMetadata == null) {
                K nativeOwnerType = getNativeOwnerType();
                K nativeType = getNativeType();
                annotationMetadata = createOnMissing(nativeOwnerType, nativeType);
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
