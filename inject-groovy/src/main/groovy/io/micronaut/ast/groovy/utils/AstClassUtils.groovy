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
import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.ClassNode

import static org.codehaus.groovy.ast.ClassHelper.make
/**
 * Utility methods for working with classes
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@CompileStatic
class AstClassUtils {

    /**
     * Return whether the given child class is a subclass or or implements the given class
     *
     * @param childClass The child class
     * @param superClass The super class or interface
     * @return
     */
    static boolean isSubclassOfOrImplementsInterface(ClassNode childClass, ClassNode superClass) {
        String superClassName = superClass.getName()
        return isSubclassOfOrImplementsInterface(childClass, superClassName)
    }

    static boolean isSubclassOfOrImplementsInterface(ClassNode childClass, String superClassName) {
        return isSubclassOf(childClass, superClassName) || implementsInterface(childClass, superClassName)
    }

    /**
     * Returns true if the given class name is a parent class of the given class
     *
     * @param classNode The class node
     * @param parentClassName the parent class name
     * @return True if it is a subclass
     */
    static boolean isSubclassOf(ClassNode classNode, String parentClassName) {
        if (classNode.name == parentClassName) return true
        ClassNode currentSuper = classNode.getSuperClass()
        while (currentSuper != null) {
            if (currentSuper.getName() == parentClassName) {
                return true
            }

            if (currentSuper.getName() == ClassHelper.OBJECT_TYPE.getName()) {
                break
            } else {
                currentSuper = currentSuper.getSuperClass()
            }

        }
        return false
    }

    /**
     * Whether the given class node implements the given interface name
     *
     * @param classNode The class node
     * @param interfaceName The interface name
     * @return True if it does
     */
    static boolean implementsInterface(ClassNode classNode, String interfaceName) {
        ClassNode interfaceNode = make(interfaceName)
        return implementsInterface(classNode, interfaceNode)
    }

    /**
     * Whether the given class node implements the given interface node
     *
     * @param classNode The class node
     * @param interfaceName The interface node
     * @return True if it does
     */
    static boolean implementsInterface(ClassNode classNode, ClassNode interfaceNode) {
        if (classNode.getAllInterfaces().contains(interfaceNode)) {
            return true
        }
        ClassNode superClass = classNode.getSuperClass()
        while (superClass != null) {
            if (superClass.getAllInterfaces().contains(interfaceNode)) {
                return true
            }
            superClass = superClass.getSuperClass()
        }
        return false
    }

}
