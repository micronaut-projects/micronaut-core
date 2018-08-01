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
package io.micronaut.ast.groovy.annotation

import groovy.transform.CompileStatic
import io.micronaut.ast.groovy.utils.ExtendedParameter
import io.micronaut.core.convert.ConversionService
import io.micronaut.core.reflect.ClassUtils
import io.micronaut.core.util.StringUtils
import io.micronaut.core.value.OptionalValues
import io.micronaut.inject.annotation.AbstractAnnotationMetadataBuilder
import org.codehaus.groovy.ast.AnnotatedNode
import org.codehaus.groovy.ast.AnnotationNode
import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.ast.Parameter
import org.codehaus.groovy.ast.expr.AnnotationConstantExpression
import org.codehaus.groovy.ast.expr.ClassExpression
import org.codehaus.groovy.ast.expr.ConstantExpression
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.expr.ListExpression
import org.codehaus.groovy.ast.expr.PropertyExpression
import org.codehaus.groovy.ast.stmt.ReturnStatement
import org.codehaus.groovy.ast.stmt.Statement

import java.lang.annotation.Repeatable
import java.lang.reflect.Array

/**
 * Groovy implementation of {@link AbstractAnnotationMetadataBuilder}
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@CompileStatic
class GroovyAnnotationMetadataBuilder extends AbstractAnnotationMetadataBuilder<AnnotatedNode, AnnotationNode> {
    public static Map<String, Map<? extends AnnotatedNode, Expression>> ANNOTATION_DEFAULTS = new LinkedHashMap<>()
    public static final ClassNode ANN_OVERRIDE = ClassHelper.make(Override.class)

    @Override
    protected AnnotatedNode getTypeForAnnotation(AnnotationNode annotationMirror) {
        return annotationMirror.classNode
    }

    @Override
    protected String getRepeatableName(AnnotationNode annotationMirror) {
        List<AnnotationNode> annotationNodes = annotationMirror.classNode.getAnnotations(ClassHelper.makeCached(Repeatable))
        if (annotationNodes) {
            Expression expression = annotationNodes.get(0).getMember("value")
            if (expression instanceof ClassExpression) {
                return ((ClassExpression)expression).type.name
            }
        }
        return null
    }

    @Override
    protected Optional<AnnotatedNode> getAnnotationMirror(String annotationName) {
        ClassNode cn = ClassUtils.forName(annotationName, GroovyAnnotationMetadataBuilder.classLoader).map({ Class cls -> ClassHelper.make(cls)}).orElseGet({->ClassHelper.make(annotationName)})
        return Optional.of(cn)
    }

    @Override
    protected String getAnnotationTypeName(AnnotationNode annotationMirror) {
        return annotationMirror.classNode.name
    }

    @Override
    protected List<? extends AnnotationNode> getAnnotationsForType(AnnotatedNode element) {
        return element.getAnnotations()
    }

    @Override
    protected List<AnnotatedNode> buildHierarchy(AnnotatedNode element, boolean inheritTypeAnnotations) {
        if (element instanceof ClassNode) {
            List<AnnotatedNode> hierarchy = new ArrayList<>()
            ClassNode cn = (ClassNode) element
            hierarchy.add(cn)
            populateTypeHierarchy(cn, hierarchy)
            return hierarchy.reverse()
        } else if (element instanceof MethodNode) {
            MethodNode mn = (MethodNode) element
            List<AnnotatedNode> hierarchy
            if (inheritTypeAnnotations) {
                hierarchy = buildHierarchy(mn.getDeclaringClass(), false)
            } else {
                hierarchy = []
            }
            if (!mn.getAnnotations(ANN_OVERRIDE).isEmpty()) {
                hierarchy.addAll(findOverriddenMethods(mn))
            }
            hierarchy.add(mn)
            return hierarchy
        } else if (element instanceof ExtendedParameter) {
            ExtendedParameter p = (ExtendedParameter) element
            List<AnnotatedNode> hierarchy = []
            MethodNode methodNode = p.methodNode
            if (!methodNode.getAnnotations(ANN_OVERRIDE).isEmpty()) {
                int variableIdx = Arrays.asList(methodNode.parameters).indexOf(p.parameter)
                for (MethodNode overridden : findOverriddenMethods(methodNode)) {
                    hierarchy.add(new ExtendedParameter(overridden, overridden.parameters[variableIdx]))
                }
            }
            hierarchy.add(p)
            return hierarchy
        } else {
            return Collections.singletonList(element)
        }
    }

    @Override
    protected void readAnnotationRawValues(String memberName, Object annotationValue, Map<CharSequence, Object> annotationValues) {
        if (!annotationValues.containsKey(memberName)) {
            def v = readAnnotationValue(memberName, annotationValue)
            if (v != null) {
                annotationValues.put(memberName, v)
            }
        }
    }

    @Override
    protected Map<? extends AnnotatedNode, ?> readAnnotationDefaultValues(AnnotationNode annotationMirror) {
        Map<String, Map<? extends AnnotatedNode, Expression>> defaults = ANNOTATION_DEFAULTS
        ClassNode classNode = annotationMirror.classNode
        String annotationName = classNode.name

        if (!defaults.containsKey(annotationName)) {

            List<MethodNode> methods = new ArrayList<>(classNode.getMethods())
            Map<? extends AnnotatedNode, Expression> defaultValues = new HashMap<>()

            if (classNode.isResolved()) {
                Class resolved = classNode.getTypeClass()
                for (MethodNode method: methods) {
                    def defaultValue = resolved.getDeclaredMethod(method.getName()).defaultValue
                    if (defaultValue != null) {
                        if (defaultValue instanceof Class) {
                            defaultValues.put(method, new ClassExpression(ClassHelper.makeCached((Class)defaultValue)))
                        } else {
                            if (defaultValue instanceof String) {
                                if (StringUtils.isNotEmpty((String)defaultValue)) {
                                    defaultValues.put(method, new ConstantExpression(defaultValue))
                                }
                            } else {
                                defaultValues.put(method, new ConstantExpression(defaultValue))
                            }
                        }
                    }
                }
            } else {
                for (MethodNode method: methods) {
                    Statement stmt = method.code
                    if (stmt instanceof ReturnStatement) {
                        def expression = ((ReturnStatement) stmt).expression
                        if (expression instanceof ConstantExpression) {
                            ConstantExpression ce = (ConstantExpression) expression
                            def v = ce.value
                            if (v != null) {
                                if (v instanceof String) {
                                    if (StringUtils.isNotEmpty((String)v)) {
                                        defaultValues.put(method, new ConstantExpression(v))
                                    }
                                } else {
                                    defaultValues.put(method, (Expression)expression)
                                }
                            }
                        }
                    }
                }
            }

            if (!defaultValues.isEmpty()) {
                defaults.put(annotationName, defaultValues)
            }
        }

        return defaults.get(annotationName)
    }

    @Override
    protected Object readAnnotationValue(String memberName, Object annotationValue) {
        if (annotationValue instanceof ConstantExpression) {
            if (annotationValue instanceof AnnotationConstantExpression) {
                AnnotationConstantExpression ann = (AnnotationConstantExpression) annotationValue
                AnnotationNode value = (AnnotationNode) ann.getValue()
                return readNestedAnnotationValue(value)
            } else {
                return ((ConstantExpression) annotationValue).value
            }

        } else if (annotationValue instanceof PropertyExpression) {
            PropertyExpression pe = (PropertyExpression) annotationValue
            if (pe.objectExpression instanceof ClassExpression) {
                ClassExpression ce = (ClassExpression) pe.objectExpression
                if (ce.type.isResolved()) {
                    Class typeClass = ce.type.typeClass
                    try {
                        def value = typeClass[pe.propertyAsString]
                        if (value != null) {
                            return value
                        }
                    } catch (e) {
                        // ignore
                    }
                }
            }
        } else if (annotationValue instanceof ClassExpression) {
            return ((ClassExpression) annotationValue).type.name
        } else if (annotationValue instanceof ListExpression) {
            ListExpression le = (ListExpression) annotationValue
            List converted = []
            Class arrayType = Object.class
            for (exp in le.expressions) {
                if (exp instanceof AnnotationConstantExpression) {
                    arrayType = io.micronaut.core.annotation.AnnotationValue
                    AnnotationConstantExpression ann = (AnnotationConstantExpression) exp
                    AnnotationNode value = (AnnotationNode) ann.getValue()
                    converted.add(readNestedAnnotationValue(value))
                } else if (exp instanceof ConstantExpression) {
                    Object value = ((ConstantExpression) exp).value
                    if(value != null) {
                        if(value instanceof CharSequence) {
                            value = value.toString()
                        }
                        arrayType = value.getClass()
                        converted.add(value)
                    }
                } else if (exp instanceof ClassExpression) {
                    arrayType = String
                    converted.add(((ClassExpression) exp).type.name)
                }
            }
            // for some reason this is necessary to produce correct array type in Groovy
            return ConversionService.SHARED.convert(converted, Array.newInstance(arrayType, 0).getClass()).orElse(null)
        }
        return null
    }

    @Override
    protected Map<? extends AnnotatedNode, ?> readAnnotationRawValues(AnnotationNode annotationMirror) {
        Map<String, Expression> members = annotationMirror.getMembers()
        Map<? extends AnnotatedNode, Object> values = [:]
        ClassNode annotationClassNode = annotationMirror.classNode
        for (m in members) {
            values.put(annotationClassNode.getMethods(m.key)[0], m.value)
        }
        return values
    }

    @Override
    protected OptionalValues<?> getAnnotationValues(AnnotatedNode member, Class<?> annotationType) {
        if (member != null) {
            def anns = member.getAnnotations(ClassHelper.make(annotationType))
            if (!anns.isEmpty()) {
                AnnotationNode ann = anns[0]
                Map<CharSequence, Object> converted = new LinkedHashMap<>();
                for (annMember in ann.members) {
                    readAnnotationRawValues(annMember.key, annMember.value, converted)
                }
                return OptionalValues.of(Object.class, converted)
            }
        }
        return OptionalValues.empty()
    }

    @Override
    protected String getAnnotationMemberName(AnnotatedNode member) {
        return ((MethodNode) member).getName()
    }

    private void populateTypeHierarchy(ClassNode classNode, List<AnnotatedNode> hierarchy) {
        while (classNode != null) {
            ClassNode[] interfaces = classNode.getInterfaces()
            for (ClassNode anInterface : interfaces) {
                if (!hierarchy.contains(anInterface) && anInterface.name != GroovyObject.name) {
                    hierarchy.add(anInterface)
                    populateTypeHierarchy(anInterface, hierarchy)
                }
            }
            classNode = classNode.getSuperClass()
            if (classNode != null) {
                if (classNode == ClassHelper.OBJECT_TYPE || classNode.name == Script.name || classNode.name == GroovyObjectSupport.name) {
                    break
                } else {
                    hierarchy.add(classNode)
                }
            } else {
                break
            }
        }
    }

    private List<MethodNode> findOverriddenMethods(MethodNode methodNode) {
        List<MethodNode> overriddenMethods = []
        ClassNode classNode = methodNode.getDeclaringClass()

        String methodName = methodNode.name
        Parameter[] methodParameters = methodNode.parameters

        while (classNode != null && classNode.name != Object.name) {

            for (i in classNode.getAllInterfaces()) {
                MethodNode parent = i.getDeclaredMethod(methodName, methodParameters)
                if (parent != null) {
                    overriddenMethods.add(parent)
                }
            }
            classNode = classNode.superClass
            if (classNode != null && classNode.name != Object.name) {
                MethodNode parent = classNode.getDeclaredMethod(methodName, methodParameters)
                if (parent != null) {
                    if (!parent.isPrivate()) {
                        overriddenMethods.add(parent)
                    }
                    if (parent.getAnnotations(ANN_OVERRIDE).isEmpty()) {
                        break
                    }
                }
            }
        }
        return overriddenMethods
    }
}
