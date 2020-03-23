package io.micronaut.ast.groovy.visitor;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.ast.PackageElement;
import org.codehaus.groovy.ast.PackageNode;

import javax.annotation.Nonnull;

/**
 * A Groovy package element implementation.
 *
 * @author graemerocher
 * @since 1.3.4
 */
@Internal
public class GroovyPackageElement implements PackageElement {

    private final PackageNode packageNode;
    private final AnnotationMetadata annotationMetadata;

    /**
     * Default constructor.
     * @param packageNode The package node
     * @param annotationMetadata The annotation metadata
     */
    public GroovyPackageElement(PackageNode packageNode, AnnotationMetadata annotationMetadata) {
        this.packageNode = packageNode;
        this.annotationMetadata = annotationMetadata;
    }

    @Override
    public AnnotationMetadata getAnnotationMetadata() {
        return annotationMetadata;
    }

    @Nonnull
    @Override
    public String getName() {
        return packageNode.getName();
    }

    @Override
    public boolean isProtected() {
        return false;
    }

    @Override
    public boolean isPublic() {
        return true;
    }

    @Nonnull
    @Override
    public Object getNativeType() {
        return packageNode;
    }
}
