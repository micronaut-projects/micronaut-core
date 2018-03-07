package io.micronaut.ast.groovy.utils

import groovy.transform.CompileStatic
import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.GenericsType
import org.codehaus.groovy.ast.Parameter
import io.micronaut.core.annotation.Internal

import javax.inject.Inject

import static org.codehaus.groovy.ast.tools.GenericsUtils.correctToGenericsSpecRecurse

/**
 * General utility methods for AST transforms
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@CompileStatic
class AstUtils {
    public static final Parameter[] ZERO_PARAMETERS = new Parameter[0]
    public static final ClassNode[] EMPTY_CLASS_ARRAY = new ClassNode[0]
    public static final ClassNode INTERNAL_ANNOTATION = ClassHelper.make(Internal)
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

        if(type.isGenericsPlaceHolder() && genericsPlaceholders != null) {
            final ClassNode placeHolderType
            if(genericsPlaceholders.containsKey(type.getUnresolvedName())) {
                placeHolderType = genericsPlaceholders.get(type.getUnresolvedName())
            } else {
                placeHolderType = defaultPlaceholder
            }
            if(placeHolderType != null) {
                return placeHolderType.getPlainNodeReference()
            } else {
                return ClassHelper.make(Object.class).getPlainNodeReference()
            }
        }

        final ClassNode nonGen = type.getPlainNodeReference()

        if("java.lang.Object".equals(type.getName())) {
            nonGen.setGenericsPlaceHolder(false)
            nonGen.setGenericsTypes(null)
            nonGen.setUsingGenerics(false)
        } else {
            if(type.isUsingGenerics()) {
                GenericsType[] parameterized = type.getGenericsTypes()
                if (parameterized != null && parameterized.length > 0) {
                    GenericsType[] copiedGenericsTypes = new GenericsType[parameterized.length]
                    for (int i = 0; i < parameterized.length; i++) {
                        GenericsType parameterizedType = parameterized[i]
                        GenericsType copiedGenericsType = null
                        if (parameterizedType.isPlaceholder() && genericsPlaceholders != null) {
                            ClassNode placeHolderType = genericsPlaceholders.get(parameterizedType.getName())
                            if(placeHolderType != null) {
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
