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
package io.micronaut.validation.routes;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationMetadataDelegate;
import io.micronaut.core.bind.annotation.Bindable;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ParameterElement;

import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Models a route parameter.
 *
 * @author James Kleeh
 * @since 1.0
 */
class RouteParameterElement implements ParameterElement, AnnotationMetadataDelegate {

    private final ParameterElement delegate;
    private final String name;

    /**
     * Default constructor.
     * @param delegate The parameter to delegate to
     */
    RouteParameterElement(ParameterElement delegate) {
        this.delegate = delegate;
        this.name = delegate.stringValue(Bindable.class).orElse(delegate.getName());
    }

    @Override
    public AnnotationMetadata getAnnotationMetadata() {
        return delegate;
    }

    @NonNull
    @Override
    public ClassElement getType() {
        return delegate.getType();
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public boolean isProtected() {
        return delegate.isProtected();
    }

    @Override
    public boolean isPublic() {
        return delegate.isPublic();
    }

    @Override
    public Object getNativeType() {
        return delegate.getNativeType();
    }

    @Override
    public boolean isAbstract() {
        return delegate.isAbstract();
    }

    @Override
    public boolean isStatic() {
        return delegate.isStatic();
    }

    @Override
    public boolean isPrivate() {
        return delegate.isPrivate();
    }

    @Override
    public boolean isFinal() {
        return delegate.isFinal();
    }

    @Override
    public String toString() {
        ClassElement type = delegate.getType();
        if (type != null) {
            return type.getName() + " " + delegate.getName();
        } else {
            return delegate.getName();
        }
    }
}
