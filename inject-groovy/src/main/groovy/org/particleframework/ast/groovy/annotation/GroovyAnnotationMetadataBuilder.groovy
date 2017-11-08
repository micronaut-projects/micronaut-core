/*
 * Copyright 2017 original authors
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
package org.particleframework.ast.groovy.annotation

import groovy.transform.CompileStatic
import org.codehaus.groovy.ast.*
import org.codehaus.groovy.ast.expr.ClassExpression
import org.codehaus.groovy.ast.expr.ConstantExpression
import org.codehaus.groovy.ast.expr.ListExpression
import org.particleframework.core.value.OptionalValues
import org.particleframework.inject.annotation.AbstractAnnotationMetadataBuilder

/**
 * Groovy implementation of {@link AbstractAnnotationMetadataBuilder}
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@CompileStatic
class GroovyAnnotationMetadataBuilder extends AbstractAnnotationMetadataBuilder<AnnotatedNode, AnnotationNode>{

    public static final ClassNode ANN_OVERRIDE = ClassHelper.make(Override.class)

    @Override
    protected AnnotatedNode getTypeForAnnotation(AnnotationNode annotationMirror) {
        return annotationMirror.classNode
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
        if(element instanceof ClassNode) {
            List<AnnotatedNode> hierarchy = new ArrayList<>()
            ClassNode cn = (ClassNode) element
            hierarchy.add(cn)
            populateTypeHierarchy(cn, hierarchy)
            return hierarchy.reverse()
        }
        else if(element instanceof MethodNode) {
            MethodNode mn = (MethodNode)element
            List<AnnotatedNode> hierarchy
            if(inheritTypeAnnotations) {
                hierarchy = buildHierarchy(mn.getDeclaringClass(), false)
            }
            else {
                hierarchy = []
            }
            if (!mn.getAnnotations(ANN_OVERRIDE).isEmpty()) {
                hierarchy.addAll(findOverriddenMethods(mn))
            }
            hierarchy.add(mn)
            return hierarchy

        } else {
            return Collections.singletonList(element)
        }
    }

    @Override
    protected void readAnnotationValues(String memberName, Object annotationValue, Map<CharSequence, Object> annotationValues) {
        if(annotationValue instanceof ConstantExpression) {
            annotationValues.put(memberName, ((ConstantExpression)annotationValue).value)
        }
        else if(annotationValue instanceof ClassExpression) {
            annotationValues.put(memberName, ((ClassExpression)annotationValue).type.name)
        }
        else if(annotationValue instanceof ListExpression) {
            ListExpression le = (ListExpression)annotationValue
            List converted = []
            for(exp in le.expressions) {
                if(exp instanceof ConstantExpression) {
                    converted.add(((ConstantExpression)exp).value)
                }
                else if(exp instanceof ClassExpression) {
                    converted.add(((ClassExpression)exp).type.name)
                }
            }
            annotationValues.put(memberName, converted)
        }
    }

    @Override
    protected Map<? extends AnnotatedNode, ?> readAnnotationValues(AnnotationNode annotationMirror) {
        def members = annotationMirror.getMembers()
        Map<? extends AnnotatedNode, Object> values = [:]
        for(m in members) {
            values.put(annotationMirror.classNode.getMethods(m.key)[0], m.value)
        }
        return values
    }

    @Override
    protected OptionalValues<?> getAnnotationValues(AnnotatedNode member, Class<?> annotationType) {
        def anns = member.getAnnotations(ClassHelper.make(annotationType))
        if(!anns.isEmpty()) {
            AnnotationNode ann = anns[0]
            Map<CharSequence, Object> converted = new LinkedHashMap<>();
            for(annMember in ann.members) {
                readAnnotationValues(annMember.key, annMember.value, converted)
            }
            return OptionalValues.of(Object.class, converted)
        }
        return OptionalValues.empty()
    }

    @Override
    protected String getAnnotationMemberName(AnnotatedNode member) {
        return ((MethodNode)member).getName()
    }

    private void populateTypeHierarchy(ClassNode classNode, List<AnnotatedNode> hierarchy) {
        while(classNode != null) {

            ClassNode[] interfaces = classNode.getInterfaces()
            for (ClassNode anInterface : interfaces) {
                if(!hierarchy.contains(anInterface)) {
                    hierarchy.add(anInterface)
                    populateTypeHierarchy(anInterface, hierarchy)
                }
            }
            classNode = classNode.getSuperClass()
            if(classNode  != null) {
                if(classNode .toString().equals(Object.class.getName())) {
                    break
                }
                else {
                    hierarchy.add(classNode)
                }
            }
            else {
                break
            }
        }
    }

    private List<MethodNode> findOverriddenMethods(MethodNode methodNode) {
        List<MethodNode> overriddenMethods = []
        ClassNode classNode = methodNode.getDeclaringClass()

        String methodName = methodNode.name
        Parameter[] methodParameters = methodNode.parameters

        while(classNode != null && classNode.name != Object.name) {

            for(i in classNode.getAllInterfaces()) {
                MethodNode parent = i.getDeclaredMethod(methodName, methodParameters)
                if(parent != null) {
                    overriddenMethods.add(parent)
                }
            }
            classNode = classNode.superClass
            if(classNode != null && classNode.name != Object.name) {
                MethodNode parent = classNode.getDeclaredMethod(methodName, methodParameters)
                if(parent != null) {
                    if(!parent.isPrivate()) {
                        overriddenMethods.add(parent)
                    }
                    if(parent.getAnnotations(ANN_OVERRIDE).isEmpty()) {
                        break
                    }
                }
            }
        }
        return overriddenMethods
    }

}
