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
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.PropertyElement;

import edu.umd.cs.findbugs.annotations.NonNull;
import javax.lang.model.element.ExecutableElement;

/**
 * Models a {@link PropertyElement} for Java.
 *
 * @author graemerocher
 * @since 1.0
 */
@Internal
class JavaPropertyElement extends AbstractJavaElement implements PropertyElement {

    private final String name;
    private final ClassElement type;
    private final boolean readOnly;
    private final ClassElement declaringElement;

    /**
     * Default constructor.
     *  @param declaringElement The declaring element
     * @param getter The element
     * @param annotationMetadata The annotation metadata
     * @param name The name
     * @param type The type
     * @param readOnly Whether it is read only
     * @param visitorContext The java visitor context
     */
    JavaPropertyElement(
            ClassElement declaringElement,
            ExecutableElement getter,
            AnnotationMetadata annotationMetadata,
            String name,
            ClassElement type,
            boolean readOnly,
            JavaVisitorContext visitorContext) {
        super(getter, annotationMetadata, visitorContext);
        this.name = name;
        this.type = type;
        this.readOnly = readOnly;
        this.declaringElement = declaringElement;
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public String toString() {
        return name;
    }

    @NonNull
    @Override
    public ClassElement getType() {
        return type;
    }

    @Override
    public boolean isReadOnly() {
        return readOnly;
    }

    @Override
    public ClassElement getDeclaringType() {
        return declaringElement;
    }
}
