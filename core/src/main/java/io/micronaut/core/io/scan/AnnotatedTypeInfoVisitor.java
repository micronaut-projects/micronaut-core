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
package io.micronaut.core.io.scan;

import io.micronaut.core.annotation.AnnotatedTypeInfo;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.reflect.ClassUtils;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.util.HashSet;
import java.util.Set;

/**
 * Discovers the annotation names of a class.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
class AnnotatedTypeInfoVisitor extends ClassVisitor implements AnnotatedTypeInfo {
    private Set<String> annotations = new HashSet<>();
    private String className;
    private boolean isAbstract;

    /**
     * Default constructor.
     */
    public AnnotatedTypeInfoVisitor() {
        super(Opcodes.ASM5);
    }

    @Override
    public void visit(int version, int access, String name, String signature, String supername, String[] interfaces) {
        this.className = ClassUtils.pathToClassName(name);
        boolean isInterface = ((access & Opcodes.ACC_INTERFACE) != 0);
        boolean isAnnotation = ((access & Opcodes.ACC_ANNOTATION) != 0);
        this.isAbstract = isInterface || isAnnotation || ((access & Opcodes.ACC_ABSTRACT) != 0);
    }

    @Override
    public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
        String className = Type.getType(desc).getClassName();
        annotations.add(className);
        return super.visitAnnotation(desc, visible);
    }

    @Override
    public boolean isAbstract() {
        return isAbstract;
    }

    @Override
    public String getTypeName() {
        return className;
    }

    @Override
    public boolean hasAnnotation(String annotationName) {
        return annotations.contains(annotationName);
    }
}
