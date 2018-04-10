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
package io.micronaut.ast.groovy.utils

import groovy.transform.CompileStatic
import org.codehaus.groovy.ast.ClassCodeVisitorSupport
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.control.SourceUnit

/**
 * @author Graeme Rocher
 * @since 1.0
 */
@CompileStatic
abstract class PublicMethodVisitor extends ClassCodeVisitorSupport {
    final SourceUnit sourceUnit
    private final Set<String> processed = new HashSet<>()
    private ClassNode current

    PublicMethodVisitor(SourceUnit sourceUnit) {
        this.sourceUnit = sourceUnit
    }

    @Override
    protected SourceUnit getSourceUnit() {
        return sourceUnit
    }

    void accept(ClassNode classNode) {
        while (classNode.name != Object.class.getName()) {
            this.current = classNode
            classNode.visitContents(this)
            for (i in classNode.getAllInterfaces()) {
                if (i.name != GroovyObject.class.name) {
                    this.current = i
                    i.visitContents(this)
                }
            }

            classNode = classNode.getSuperClass()
        }
    }

    @Override
    void visitMethod(MethodNode node) {
        if (isAcceptable(node)) {
            def key = node.getText()
            if (!processed.contains(key)) {
                processed.add(key)
                accept(current ?: node.declaringClass, node)
            }
        }
    }

    protected boolean isAcceptable(MethodNode node) {
        node.isPublic() && !node.isStatic() && !node.isSynthetic() && !node.isFinal()
    }

    abstract void accept(ClassNode classNode, MethodNode methodNode)
}
