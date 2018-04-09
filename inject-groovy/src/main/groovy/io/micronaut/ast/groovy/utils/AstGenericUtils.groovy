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
import io.micronaut.core.util.ArrayUtils
import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.GenericsType
import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.ast.tools.GenericsUtils

/**
 * @author Graeme Rocher
 * @since 1.0
 */
@CompileStatic
class AstGenericUtils {

    /**
     * Variation that takes into account non primary nodes
     *
     * @param classNode The class node
     * @return The generics spec
     */
    static Map<String, ClassNode> createGenericsSpec(ClassNode classNode) {
        GenericsType[] existingGenericTypes = classNode.genericsTypes
        boolean hasGenericTypes = ArrayUtils.isNotEmpty(existingGenericTypes)
        if (!classNode.isPrimaryClassNode()) {
            if (hasGenericTypes) {
                GenericsType[] redirectTypes = classNode.redirect().getGenericsTypes()
                Map<String, ClassNode> ret = new HashMap<String, ClassNode>()
                populateGenericsSpec(redirectTypes, existingGenericTypes, ret)
                return ret
            } else {

                classNode = classNode.redirect()
                Map<String, ClassNode> ret = new HashMap<String, ClassNode>()
                GenericsType[] redirectTypes = classNode.getGenericsTypes()
                if (redirectTypes != null) {
                    for (GenericsType gt in redirectTypes) {
                        if (gt.isPlaceholder()) {
                            if (gt.upperBounds) {
                                ret.put(gt.name, gt.upperBounds[0])
                            } else if (gt.lowerBound) {
                                ret.put(gt.name, gt.lowerBound)
                            } else {
                                ret.put(gt.name, ClassHelper.OBJECT_TYPE)
                            }
                        } else {
                            ret.put(gt.name, gt.type)
                        }
                    }
                }
                return ret
            }
        } else {
            if (!hasGenericTypes) {
                return Collections.emptyMap()
            } else {
                return GenericsUtils.createGenericsSpec(classNode)
            }
        }
    }

    /**
     * Populates generics for a method
     *
     * @param methodNode The method node node
     * @param genericsSpec The spec to populate
     * @return The generics spec
     */
    static Map<String, ClassNode> createGenericsSpec(MethodNode methodNode, Map<String, ClassNode> genericsSpec) {
        GenericsType[] redirectTypes = methodNode.genericsTypes
        if (redirectTypes != null) {
            for (GenericsType gt in redirectTypes) {
                if (gt.isPlaceholder()) {
                    if (gt.upperBounds) {
                        genericsSpec.put(gt.name, gt.upperBounds[0])
                    } else if (gt.lowerBound) {
                        genericsSpec.put(gt.name, gt.lowerBound)
                    } else {
                        genericsSpec.put(gt.name, ClassHelper.OBJECT_TYPE)
                    }
                } else {
                    genericsSpec.put(gt.name, gt.type)
                }
            }
        }
        return genericsSpec
    }

    private static void populateGenericsSpec(GenericsType[] redirectTypes, GenericsType[] genericTypes, HashMap<String, ClassNode> boundTypes) {
        if (redirectTypes && redirectTypes.length == genericTypes.length) {
            int i = 0
            for (GenericsType redirect in redirectTypes) {
                GenericsType specifiedType = genericTypes[i]
                ClassNode specifiedClassNode = specifiedType.type
                if (redirect.isPlaceholder() && specifiedClassNode) {
                    if (specifiedType.isPlaceholder()) {
                        if (specifiedType.upperBounds) {
                            boundTypes.put(redirect.name, specifiedType.upperBounds[0])
                        } else if (specifiedType.lowerBound) {
                            boundTypes.put(redirect.name, specifiedType.lowerBound)
                        } else {
                            boundTypes.put(redirect.name, specifiedClassNode.redirect().plainNodeReference)
                        }
                    } else {
                        boundTypes.put(redirect.name, specifiedClassNode.redirect())
                    }
                }

                i++
            }
        }
    }

    /**
     * Finds the generic type for the given interface for the given class node
     *
     * @param classNode The class node
     * @param itfe The interface
     * @return The generic type or null
     */
    static ClassNode resolveInterfaceGenericType(ClassNode classNode, Class itfe) {
        ClassNode foundInterface = classNode.allInterfaces.find() { it.name == itfe.name }
        if (foundInterface != null) {
            if (foundInterface.genericsTypes) {
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
            if (classNode.isGenericsPlaceHolder()) {
                if (classNode.genericsTypes) {

                    String typeVar = classNode.genericsTypes[0].name
                    if (boundTypes.containsKey(typeVar)) {
                        return boundTypes.get(typeVar).name
                    }
                } else {
                    if (classNode.isResolved() || ClassHelper.isPrimitiveType(classNode)) {
                        try {
                            return classNode.typeClass
                        } catch (ClassNotFoundException ignored) {
                            return classNode.name
                        }
                    } else {
                        String redirectName = classNode.redirect().name
                        if (redirectName != classNode.unresolvedName) {
                            return redirectName
                        } else {
                            return Object.name
                        }
                    }
                }
            } else if (classNode.isArray() && classNode.componentType.isGenericsPlaceHolder()) {
                GenericsType[] componentGenericTypes = classNode.componentType.genericsTypes
                if (componentGenericTypes) {
                    String typeVar = componentGenericTypes[0].name
                    if (boundTypes.containsKey(typeVar)) {
                        ClassNode arrayNode = boundTypes.get(typeVar).makeArray()
                        return arrayNode.isPrimaryClassNode() ? arrayNode.name : arrayNode.toString()
                    }
                }
            }

            try {
                return classNode.isResolved() || ClassHelper.isPrimitiveType(classNode) ? classNode.typeClass : classNode.name
            } catch (ClassNotFoundException ignored) {
                return classNode.name
            }
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
        if (redirectGenericsTypes == null) redirectGenericsTypes = parameterized
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
                        if (lowerBound != null) {
                            def newMap = new LinkedHashMap()
                            map.put(name, Collections.singletonMap(cn.name, newMap))
                            extractPlaceholders(lowerBound, newMap, boundTypes)
                        }
                        ClassNode[] upperBounds = value.getUpperBounds()
                        if (upperBounds != null) {
                            for (ClassNode upperBound : upperBounds) {
                                def newMap = new LinkedHashMap()
                                map.put(name, Collections.singletonMap(cn.name, newMap))

                                extractPlaceholders(upperBound, newMap, boundTypes)
                            }
                        }
                    } else if (!value.isPlaceholder()) {
                        if (!cn.isUsingGenerics()) {
                            map.put(name, typeRef)
                        } else {
                            def newMap = new LinkedHashMap()
                            map.put(name, Collections.singletonMap(cn.name, newMap))
                            extractPlaceholders(cn, newMap, boundTypes)
                        }
                    } else {
                        if (boundTypes.containsKey(value.name)) {
                            map.put(name, resolveTypeReference(boundTypes.get(value.name)))
                        } else {
                            map.put(name, resolveTypeReference(value.type))
                        }
                    }
                }
            }
        }
    }
}
