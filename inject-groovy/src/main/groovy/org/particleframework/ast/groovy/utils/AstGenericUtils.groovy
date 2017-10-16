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
    static Object resolveTypeReference(ClassNode classNode) {
        if (classNode == null) {
            return null
        } else {
            return classNode.isResolved() || ClassHelper.isPrimitiveType(classNode) ? classNode.typeClass : classNode.name
        }
    }

    /**
     * Build the generics information for the given type
     * @param parameterType The parameter type
     * @return The generics information
     */
    static Map<String, Object> buildGenericTypeInfo(ClassNode parameterType) {
        Map<String, Object> resolvedGenericTypes = [:]
        Map<String, GenericsType> placeholders = GenericsUtils.extractPlaceholders(parameterType)
        for (entry in placeholders) {
            GenericsType gt = entry.value
            if (!gt.isPlaceholder()) {
                resolvedGenericTypes.put(entry.key, resolveTypeReference(gt.type))
            } else if (gt.isWildcard()) {
                ClassNode[] upperBounds = gt.upperBounds
                if (upperBounds != null && upperBounds.length == 1) {
                    resolvedGenericTypes.put(entry.key, resolveTypeReference(upperBounds[0]))
                    continue
                }

                ClassNode lowerBounds = gt.lowerBound
                if (lowerBounds != null) {
                    resolvedGenericTypes.put(entry.key, resolveTypeReference(lowerBounds))
                    continue
                }

                resolvedGenericTypes.put(entry.key, Object.class)
            } else {
                resolvedGenericTypes.put(entry.key, Object.class)
            }

        }
        resolvedGenericTypes
    }


    static Map<String, Object> extractPlaceholders(ClassNode cn) {
        Map<String, Object> ret = new HashMap<String, Object>()
        extractPlaceholders(cn, ret)
        return ret
    }

    /**
     * For a given classnode, fills in the supplied map with the parameterized
     * types it defines.
     * @param node
     * @param map
     */
    static void extractPlaceholders(ClassNode node, Map<String, Object> map) {
        if (node == null) return

        if (node.isArray()) {
            extractPlaceholders(node.getComponentType(), map)
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
                            extractPlaceholders(lowerBound, newMap)
                        }
                        ClassNode[] upperBounds = value.getUpperBounds()
                        if (upperBounds!=null) {
                            for (ClassNode upperBound : upperBounds) {
                                def newMap = new LinkedHashMap()
                                map.put(name, Collections.singletonMap(cn.name,newMap))

                                extractPlaceholders(upperBound, newMap)
                            }
                        }
                    } else if (!value.isPlaceholder()) {
                        if(!cn.isUsingGenerics()) {
                            map.put(name, typeRef)
                        }
                        else {
                            def newMap = new LinkedHashMap()
                            map.put(name, Collections.singletonMap(cn.name,newMap))
                            extractPlaceholders(cn, newMap)
                        }
                    }

                }
            }
        }
    }
}
