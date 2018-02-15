package org.particleframework.ast.groovy.utils

import groovy.transform.CompileStatic
import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.GenericsType
import org.codehaus.groovy.ast.tools.GenericsUtils

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

    /**
     * Resolve a type reference for the given node.
     * @param classNode The class node
     * @return A type reference. Either a java.lang.Class or a string representing the name of the class
     */
    static Object resolveTypeReference(ClassNode classNode, Map<String, ClassNode> boundTypes = Collections.emptyMap()) {
        if (classNode == null) {
            return null
        } else {
            if(classNode.isGenericsPlaceHolder() && classNode.genericsTypes) {
                String typeVar = classNode.genericsTypes[0].name
                if(boundTypes.containsKey(typeVar)) {
                    return boundTypes.get(typeVar).name
                }
            }
            else if(classNode.isArray() && classNode.componentType.isGenericsPlaceHolder()) {
                GenericsType[] componentGenericTypes = classNode.componentType.genericsTypes
                if(componentGenericTypes) {
                    String typeVar = componentGenericTypes[0].name
                    if(boundTypes.containsKey(typeVar)) {
                        return boundTypes.get(typeVar).makeArray().name
                    }
                }
            }
            return classNode.isResolved() || ClassHelper.isPrimitiveType(classNode) ? classNode.typeClass : classNode.name
        }
    }

    /**
     * Build the generics information for the given type
     * @param classNode The parameter type
     * @return The generics information
     */
    static Map<String, Object> buildGenericTypeInfo(ClassNode classNode, Map<String, ClassNode> boundTypes) {

        if (!classNode.isUsingGenerics() || !classNode.isRedirectNode()) Collections.emptyMap()

        Map<String, Object> resolvedGenericTypes = [:]
        extractPlaceholders(classNode, resolvedGenericTypes, boundTypes)
        return resolvedGenericTypes
    }


    static Map<String, Object> extractPlaceholders(ClassNode cn) {
        Map<String, Object> ret = new HashMap<String, Object>()
        extractPlaceholders(cn, ret, Collections.emptyMap())
        return ret
    }

    /**
     * For a given classnode, fills in the supplied map with the parameterized
     * types it defines.
     * @param node
     * @param map
     */
    static void extractPlaceholders(ClassNode node, Map<String, Object> map, Map<String, ClassNode> boundTypes) {
        if (node == null) return

        if (node.isArray()) {
            extractPlaceholders(node.getComponentType(), map, boundTypes)
            return
        }

        if (!node.isUsingGenerics() || !node.isRedirectNode()) return
        GenericsType[] parameterized = node.getGenericsTypes()
        if (parameterized == null || parameterized.length == 0) return
        GenericsType[] redirectGenericsTypes = node.redirect().getGenericsTypes()
        if (redirectGenericsTypes==null) redirectGenericsTypes = parameterized
        for (int i = 0; i < redirectGenericsTypes.length; i++) {
            GenericsType redirectType = redirectGenericsTypes[i]
            if (redirectType.isPlaceholder()) {
                String name = redirectType.getName()
                if (!map.containsKey(name)) {
                    GenericsType value = parameterized[i]
                    ClassNode cn = value.type
                    Object typeRef = resolveTypeReference(cn)

                    if (value.isWildcard()) {
                        ClassNode lowerBound = value.getLowerBound()
                        if (lowerBound!=null) {
                            def newMap = new LinkedHashMap()
                            map.put(name, Collections.singletonMap(cn.name,newMap))
                            extractPlaceholders(lowerBound, newMap,boundTypes)
                        }
                        ClassNode[] upperBounds = value.getUpperBounds()
                        if (upperBounds!=null) {
                            for (ClassNode upperBound : upperBounds) {
                                def newMap = new LinkedHashMap()
                                map.put(name, Collections.singletonMap(cn.name,newMap))

                                extractPlaceholders(upperBound, newMap,boundTypes)
                            }
                        }
                    } else if (!value.isPlaceholder()) {
                        if(!cn.isUsingGenerics()) {
                            map.put(name, typeRef)
                        }
                        else {
                            def newMap = new LinkedHashMap()
                            map.put(name, Collections.singletonMap(cn.name,newMap))
                            extractPlaceholders(cn, newMap, boundTypes)
                        }
                    } else {
                        if(boundTypes.containsKey(value.name)) {
                            map.put(name, resolveTypeReference(boundTypes.get(value.name)))
                        }
                        else {
                            map.put(name, resolveTypeReference(value.type))
                        }
                    }

                }
            }
        }
    }
}
