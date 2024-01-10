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
package io.micronaut.ast.groovy;

import groovy.lang.GroovyObject;
import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.utils.NativeElementsHelper;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.MethodNode;
import org.codehaus.groovy.ast.Parameter;

import java.util.Collection;
import java.util.List;

/**
 * The Groovy native element helper.
 *
 * @author Denis Stepanov
 * @since 4.3.0
 */
@Internal
public final class GroovyNativeElementHelper extends NativeElementsHelper<ClassNode, MethodNode> {

    @Override
    protected boolean overrides(MethodNode subMethod, MethodNode superMethod, ClassNode owner) {
        Parameter[] superParameters = superMethod.getParameters();
        Parameter[] subParameters = subMethod.getParameters();
        if (superParameters.length != subParameters.length || !subMethod.getName().equals(superMethod.getName())) {
            return false;
        }
        for (int i = 0, n = superParameters.length; i < n; i += 1) {
            ClassNode superType = superParameters[i].getType();
            ClassNode subType = subParameters[i].getType();

            if (!isAssignable(subType, superType)) {
                return false;
            }
        }
        ClassNode subDeclaringClass = subMethod.getDeclaringClass();
        ClassNode superDeclaringClass = superMethod.getDeclaringClass();
        return isAssignable(subDeclaringClass, superDeclaringClass);
    }

    private boolean isAssignable(ClassNode c1, ClassNode c2) {
        if (c1.equals(c2)) {
            return true;
        }
        if (c2.isInterface()) {
            return c1.implementsInterface(c2);
        }
        return c1.isDerivedFrom(c2);
    }

    @Override
    protected String getMethodName(MethodNode element) {
        return element.getName();
    }

    @Override
    protected ClassNode getSuperClass(ClassNode classNode) {
        return classNode.getSuperClass();
    }

    @Override
    protected Collection<ClassNode> getInterfaces(ClassNode classNode) {
        return List.of(classNode.getInterfaces());
    }

    @Override
    protected List<MethodNode> getMethods(ClassNode classNode) {
        return classNode.getMethods();
    }

    @Override
    protected boolean excludeClass(ClassNode classNode) {
        return classNode.getName().equals(Object.class.getName()) || classNode.getName().equals(GroovyObject.class.getName());
    }

    @Override
    protected boolean isInterface(ClassNode classNode) {
        return classNode.isInterface();
    }
}
