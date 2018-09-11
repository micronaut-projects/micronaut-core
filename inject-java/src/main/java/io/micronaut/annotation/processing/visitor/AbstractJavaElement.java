/*
 * Copyright 2017-2018 original authors
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

import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;

/**
 * An abstract class for other elements to extend from.
 *
 * @author James Kleeh
 * @since 1.0
 */
public abstract class AbstractJavaElement implements io.micronaut.inject.visitor.Element, AnnotationMetadataDelegate {

    private final Element element;
    private final AnnotationMetadata annotationMetadata;

    /**
     * @param element            The {@link Element}
     * @param annotationMetadata The Annotation metadata
     */
    AbstractJavaElement(Element element, AnnotationMetadata annotationMetadata) {
        this.element = element;
        this.annotationMetadata = annotationMetadata;
    }

    private boolean hasModifier(Modifier modifier) {
        return element.getModifiers().contains(modifier);
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
}
