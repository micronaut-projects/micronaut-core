/*
 * Copyright 2017-2020 original authors
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
import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.ast.PackageElement;

import javax.annotation.Nonnull;

/**
 * Represents a package in Java.
 *
 * @author graemerocher
 * @since 1.3.4
 */
@Internal
public class JavaPackageElement implements PackageElement {
    private final javax.lang.model.element.PackageElement packageElement;
    private final AnnotationMetadata annotationMetadata;

    /**
     * Default constructor.
     * @param packageElement The package element
     * @param annotationMetadata The annotation metadata
     */
    public JavaPackageElement(javax.lang.model.element.PackageElement packageElement, AnnotationMetadata annotationMetadata) {
        this.packageElement = packageElement;
        this.annotationMetadata = annotationMetadata;
    }

    @Override
    public AnnotationMetadata getAnnotationMetadata() {
        return annotationMetadata;
    }

    @Nonnull
    @Override
    public String getName() {
        return packageElement.getQualifiedName().toString();
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
        return packageElement;
    }
}
