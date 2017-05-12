package org.particleframework.ast.groovy.utils

import groovy.transform.CompileStatic
import org.codehaus.groovy.ast.ClassNode

/**
 * @author Graeme Rocher
 * @since 1.0
 */
@CompileStatic
class AstGenericUtils {

    /**
     * Finds the generic type for the given interface for the given class node
     *
     * @param classNode The class node
     * @param itfe The interface
     * @return The generic type or null
     */
    static ClassNode resolveInterfaceGenericType(ClassNode classNode, Class itfe) {
        ClassNode foundInterface = classNode.allInterfaces.find() { it.name == itfe.name }
        if(foundInterface != null) {
            if(foundInterface.genericsTypes) {
                return foundInterface.genericsTypes[0]?.type
            }
        }
        return null
    }
}
