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
package io.micronaut.ast.groovy.visitor;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.inject.ast.ConstructorElement;
import org.codehaus.groovy.ast.ConstructorNode;
import org.codehaus.groovy.control.CompilationUnit;
import org.codehaus.groovy.control.SourceUnit;

/**
 * A {@link ConstructorElement} for Groovy.
 *
 * @author graemerocher
 * @since 1.0
 */
public class GroovyConstructorElement extends GroovyMethodElement implements ConstructorElement {
    /**
     * @param declaringClass     The declaring class
     * @param sourceUnit         The source unit
     * @param compilationUnit    The compilation unit
     * @param methodNode         The {@link ConstructorNode}
     * @param annotationMetadata The annotation metadata
     */
    GroovyConstructorElement(GroovyClassElement declaringClass, SourceUnit sourceUnit, CompilationUnit compilationUnit, ConstructorNode methodNode, AnnotationMetadata annotationMetadata) {
        super(declaringClass, sourceUnit, compilationUnit, methodNode, annotationMetadata);
    }
}
