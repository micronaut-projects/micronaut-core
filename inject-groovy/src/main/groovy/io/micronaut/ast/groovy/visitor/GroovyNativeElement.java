/*
 * Copyright 2017-2024 original authors
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

import io.micronaut.core.annotation.Internal;
import org.codehaus.groovy.ast.AnnotatedNode;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.FieldNode;
import org.codehaus.groovy.ast.MethodNode;
import org.codehaus.groovy.ast.PackageNode;

/**
 * Groovy's native element.
 *
 * @author Denis Stepanov
 * @since 4.0.0
 */
@Internal
public sealed interface GroovyNativeElement {

    /**
     * @return The annotated node representing the type.
     */
    AnnotatedNode annotatedNode();

    /**
     * The class element.
     *
     * @param annotatedNode The class node
     */
    record Class(ClassNode annotatedNode) implements GroovyNativeElement {
    }

    /**
     * The class element with an owner (Generic type etc.).
     *
     * @param annotatedNode The class node
     * @param owner The owner
     */
    record ClassWithOwner(ClassNode annotatedNode,
                          GroovyNativeElement owner) implements GroovyNativeElement {
    }

    /**
     * The method element.
     *
     * @param annotatedNode The method node
     */
    record Method(MethodNode annotatedNode) implements GroovyNativeElement {
    }

    /**
     * The parameter element.
     *
     * @param annotatedNode The parameter element.
     * @param methodNode The method element.
     */
    record Parameter(org.codehaus.groovy.ast.Parameter annotatedNode,
                     MethodNode methodNode) implements GroovyNativeElement {
    }

    /**
     * The package element.
     *
     * @param annotatedNode The package node
     */
    record Package(PackageNode annotatedNode) implements GroovyNativeElement {
    }

    /**
     * The field element.
     *
     * @param annotatedNode The field node
     * @param owner The owner node
     */
    record Field(FieldNode annotatedNode,
                 GroovyNativeElement owner) implements GroovyNativeElement {
    }

    /**
     * The placeholder element.
     *
     * @param annotatedNode The placeholder node
     * @param owner The owner node
     * @param variableName The variable name
     */
    record Placeholder(ClassNode annotatedNode,
                       GroovyNativeElement owner,
                       String variableName) implements GroovyNativeElement {
    }

}
