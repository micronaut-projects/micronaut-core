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

import groovy.transform.CompileStatic
import io.micronaut.core.annotation.Internal
import io.micronaut.core.naming.NameUtils
import org.codehaus.groovy.ast.ClassCodeVisitorSupport
import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.ast.Parameter
import org.codehaus.groovy.ast.PropertyNode
import org.codehaus.groovy.control.SourceUnit

/**
 * Visits public methods in a class node.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@CompileStatic
@Internal
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
        while (classNode != null && classNode.name != Object.class.getName()) {
            this.current = classNode
            classNode.visitContents(this)
            for (i in classNode.getAllInterfaces()) {
                if (classNode == i) {
                    continue
                }
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

    @Override
    void visitProperty(PropertyNode node) {
        //Convert the property to a setter method
        Parameter param = new Parameter(node.type, node.name)
        MethodNode setter = new MethodNode(NameUtils.setterNameFor(node.name),
                node.modifiers,
                ClassHelper.makeCached(Void.TYPE),
                [param] as Parameter[],
                null,
                null)
        setter.addAnnotations(node.field.getAnnotations())
        setter.setDeclaringClass(node.getDeclaringClass())

        //Convert the property to a getter method
        MethodNode getter = new MethodNode(NameUtils.getterNameFor(node.name),
                node.modifiers,
                node.type,
                [] as Parameter[],
                null,
                null)
        getter.addAnnotations(node.field.getAnnotations())
        getter.setDeclaringClass(node.getDeclaringClass())

        ClassNode classNode = current ?: node.declaringClass
        //Can't use node.getText() because for properties because it returns <not implemented yet...>
        String key = classNode.getName() + '#' + node.getName()
        if (!processed.contains(key)) {
            processed.add(key)
            if (isAcceptable(setter)) {
                accept(classNode, setter)
            }
            if (isAcceptable(getter)) {
                accept(classNode, getter)
            }
        }

    }

    protected boolean isAcceptable(MethodNode node) {
        node.isPublic() && !node.isStatic() && !node.isSynthetic() && !node.isFinal()
    }

    abstract void accept(ClassNode classNode, MethodNode methodNode)
}
