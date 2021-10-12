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
package io.micronaut.ast.groovy.visitor;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.inject.ast.ConstructorElement;
import org.codehaus.groovy.ast.ConstructorNode;

/**
 * A {@link ConstructorElement} for Groovy.
 *
 * @author graemerocher
 * @since 1.0
 */
public class GroovyConstructorElement extends GroovyMethodElement implements ConstructorElement {
    /**
     * @param declaringClass     The declaring class
     * @param visitorContext     The visitor context
     * @param methodNode         The {@link ConstructorNode}
     * @param annotationMetadata The annotation metadata
     */
    GroovyConstructorElement(GroovyClassElement declaringClass, GroovyVisitorContext visitorContext, ConstructorNode methodNode, AnnotationMetadata annotationMetadata) {
        super(declaringClass, visitorContext, methodNode, annotationMetadata);
    }
}
