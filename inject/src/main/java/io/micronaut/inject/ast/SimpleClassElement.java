package io.micronaut.inject.ast;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import org.jetbrains.annotations.NotNull;

@Internal
final class SimpleClassElement implements ClassElement {
    private final String typeName;
    private final boolean isInterface;
    private final AnnotationMetadata annotationMetadata;

    SimpleClassElement(String typeName) {
        this(typeName, false, AnnotationMetadata.EMPTY_METADATA);
    }

    SimpleClassElement(String typeName, boolean isInterface, AnnotationMetadata annotationMetadata) {
        this.typeName = typeName;
        this.isInterface = isInterface;
        this.annotationMetadata = annotationMetadata != null ? annotationMetadata : AnnotationMetadata.EMPTY_METADATA;
    }

    @Override
    public AnnotationMetadata getAnnotationMetadata() {
        return this.annotationMetadata;
    }

    @Override
    public boolean isInterface() {
        return isInterface;
    }

    @Override
    public boolean isAssignable(String type) {
        return false;
    }

    @Override
    public boolean isAssignable(ClassElement type) {
        return false;
    }

    @Override
    public ClassElement toArray() {
        throw new UnsupportedOperationException("Cannot convert class elements produced by name to an array");
    }

    @Override
    public ClassElement fromArray() {
        throw new UnsupportedOperationException("Cannot convert class elements produced by from an array");
    }

    @NotNull
    @Override
    public String getName() {
        return typeName;
    }

    @Override
    public boolean isPackagePrivate() {
        return false;
    }

    @Override
    public boolean isProtected() {
        return false;
    }

    @Override
    public boolean isPublic() {
        return false;
    }

    @NotNull
    @Override
    public Object getNativeType() {
        return typeName;
    }
}
