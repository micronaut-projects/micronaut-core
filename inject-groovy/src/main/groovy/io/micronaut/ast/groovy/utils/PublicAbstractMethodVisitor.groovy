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
package io.micronaut.ast.groovy.utils

import groovy.transform.CompilationUnitAware
import groovy.transform.CompileStatic
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.control.CompilationUnit
import org.codehaus.groovy.control.SourceUnit

/**
 * A visitor that visits only abstract methods
 *
 * @author graemerocher
 * @since 1.0
 */
@CompileStatic
abstract class PublicAbstractMethodVisitor extends PublicMethodVisitor {

    ClassNode current
    private final CompilationUnit compilationUnit

    PublicAbstractMethodVisitor(SourceUnit sourceUnit, CompilationUnit compilationUnit) {
        super(sourceUnit)
        this.compilationUnit = compilationUnit
    }

    CompilationUnit getCompilationUnit() {
        compilationUnit
    }

    @Override
    void accept(ClassNode classNode) {
        this.current = classNode
        super.accept(classNode)
    }

    @Override
    protected boolean isAcceptable(MethodNode node) {
        if (!isAcceptableMethod(node)) {
            return false
        }
        if (current != null) {
            // ignore overridden methods
            def existing = current.getMethod(node.name, node.parameters)
            if (existing != null && existing != node) {
                return false
            }
        }
        return super.isAcceptable(node)
    }

    /**
     * Return whether the given executable element is acceptable. By default just checks if the method is abstract.
     * @param executableElement The method
     * @return True if it is
     */
    protected boolean isAcceptableMethod(MethodNode node) {
        return node.isAbstract()
    }
}
