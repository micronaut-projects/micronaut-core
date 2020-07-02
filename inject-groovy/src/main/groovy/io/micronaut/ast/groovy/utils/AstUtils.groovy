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

import static org.codehaus.groovy.ast.tools.GenericsUtils.correctToGenericsSpecRecurse

import groovy.transform.CompileStatic
import io.micronaut.core.annotation.Internal
import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.GenericsType
import org.codehaus.groovy.ast.Parameter

import javax.inject.Inject

/**
 * General utility methods for AST transforms
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@CompileStatic
class AstUtils {

    /**
     * Zero parameter
     */
    public static final Parameter[] ZERO_PARAMETERS = new Parameter[0]

    /**
     * Empty class node array
     */
    public static final ClassNode[] EMPTY_CLASS_ARRAY = new ClassNode[0]

    /**
     * Internal annotation
     */
    public static final ClassNode INTERNAL_ANNOTATION = ClassHelper.make(Internal)

    /**
     * Inject annotation
     */
    public static final ClassNode INJECT_ANNOTATION = ClassHelper.make(Inject)

    static Parameter[] copyParameters(Parameter[] parameterTypes) {
        return copyParameters(parameterTypes, null)
    }

    static Parameter[] copyParameters(Parameter[] parameterTypes, Map<String, ClassNode> genericsPlaceholders) {
        Parameter[] newParameterTypes = new Parameter[parameterTypes.length]
        for (int i = 0; i < parameterTypes.length; i++) {
            Parameter parameterType = parameterTypes[i]
            Parameter newParameter = new Parameter(replaceGenericsPlaceholders(parameterType.getType(), genericsPlaceholders), parameterType.getName(), parameterType.getInitialExpression())
            AstAnnotationUtils.copyAnnotations(parameterType, newParameter)
            newParameterTypes[i] = newParameter
        }
        return newParameterTypes
    }

    static Parameter[] copyParameters(Map<String, ClassNode> genericsSpec, Parameter[] parameterTypes, List<String> currentMethodGenPlaceholders) {
        Parameter[] newParameterTypes = new Parameter[parameterTypes.length]
        for (int i = 0; i < parameterTypes.length; i++) {
            Parameter parameterType = parameterTypes[i]
            ClassNode newParamType = correctToGenericsSpecRecurse(genericsSpec, parameterType.getType(), currentMethodGenPlaceholders)
            Parameter newParameter = new Parameter(newParamType, parameterType.getName(), parameterType.getInitialExpression())
            newParameter.addAnnotations(parameterType.getAnnotations())
            newParameterTypes[i] = newParameter
        }
        return newParameterTypes
    }

    static ClassNode replaceGenericsPlaceholders(ClassNode type, Map<String, ClassNode> genericsPlaceholders) {
        return replaceGenericsPlaceholders(type, genericsPlaceholders, null)
    }

    static ClassNode replaceGenericsPlaceholders(ClassNode type, Map<String, ClassNode> genericsPlaceholders, ClassNode defaultPlaceholder) {
        if (type.isArray()) {
            return replaceGenericsPlaceholders(type.getComponentType(), genericsPlaceholders).makeArray()
        }

        if (!type.isUsingGenerics() && !type.isRedirectNode()) {
            return type.getPlainNodeReference()
        }

        if (type.isGenericsPlaceHolder() && genericsPlaceholders != null) {
            final ClassNode placeHolderType
            if (genericsPlaceholders.containsKey(type.getUnresolvedName())) {
                placeHolderType = genericsPlaceholders.get(type.getUnresolvedName())
            } else {
                placeHolderType = defaultPlaceholder
            }
            if (placeHolderType != null) {
                return placeHolderType.getPlainNodeReference()
            } else {
                return ClassHelper.make(Object.class).getPlainNodeReference()
            }
        }

        final ClassNode nonGen = type.getPlainNodeReference()

        if ("java.lang.Object".equals(type.getName())) {
            nonGen.setGenericsPlaceHolder(false)
            nonGen.setGenericsTypes(null)
            nonGen.setUsingGenerics(false)
        } else {
            if (type.isUsingGenerics()) {
                GenericsType[] parameterized = type.getGenericsTypes()
                if (parameterized != null && parameterized.length > 0) {
                    GenericsType[] copiedGenericsTypes = new GenericsType[parameterized.length]
                    for (int i = 0; i < parameterized.length; i++) {
                        GenericsType parameterizedType = parameterized[i]
                        GenericsType copiedGenericsType = null
                        if (parameterizedType.isPlaceholder() && genericsPlaceholders != null) {
                            ClassNode placeHolderType = genericsPlaceholders.get(parameterizedType.getName())
                            if (placeHolderType != null) {
                                copiedGenericsType = new GenericsType(placeHolderType.getPlainNodeReference())
                            } else {
                                copiedGenericsType = new GenericsType(ClassHelper.make(Object.class).getPlainNodeReference())
                            }
                        } else {
                            copiedGenericsType = new GenericsType(replaceGenericsPlaceholders(parameterizedType.getType(), genericsPlaceholders))
                        }
                        copiedGenericsTypes[i] = copiedGenericsType
                    }
                    nonGen.setGenericsTypes(copiedGenericsTypes)
                }
            }
        }

        return nonGen
    }
}
