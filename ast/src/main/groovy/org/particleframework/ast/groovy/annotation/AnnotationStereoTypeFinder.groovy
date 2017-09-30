package org.particleframework.ast.groovy.annotation

import groovy.transform.CompileStatic
import groovy.transform.Memoized
import org.codehaus.groovy.ast.*
import org.codehaus.groovy.ast.expr.ConstantExpression
import org.particleframework.ast.groovy.utils.AstAnnotationUtils

import java.lang.annotation.Annotation
import java.lang.annotation.Documented
import java.lang.annotation.Retention
import java.lang.annotation.Target

/**
 * Deals with annotation stereotypes
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@CompileStatic
class AnnotationStereoTypeFinder {


    boolean hasStereoType(AnnotatedNode annotatedNode, Class<? extends Annotation> stereotype) {
        return hasStereoType(annotatedNode, stereotype.name)
    }

    boolean hasStereoType(AnnotatedNode annotatedNode, String... stereotypes) {
        return hasStereoType(annotatedNode, Arrays.asList(stereotypes))
    }

    boolean hasStereoType(AnnotatedNode annotatedNode, List<String> stereotypes) {
        for(stereotype in stereotypes) {
            if(findAnnotationWithStereoType(annotatedNode, stereotype) ) {
                return true
            }
        }
        return false
    }

    AnnotationNode findAnnotationWithStereoType(AnnotatedNode annotatedNode, Class<? extends Annotation> stereotype) {
        return findAnnotationWithStereoType(annotatedNode, stereotype.name)
    }

    @Memoized
    AnnotationNode findAnnotationWithStereoType(AnnotatedNode annotatedNode, String stereotype) {
        List<AnnotationNode> annotations = annotatedNode.getAnnotations()
        for(AnnotationNode ann in annotations) {
            ClassNode annotationClassNode = ann.classNode
            if(annotationClassNode.name == stereotype) {
                return ann
            }
            else if(!(annotationClassNode.name in [Retention.name, Documented.name, Target.name])) {
                if(findAnnotationWithStereoType(ann.classNode, stereotype) != null) {
                    return ann
                }
            }
        }
        if (annotatedNode instanceof MethodNode) {
            MethodNode method = (MethodNode) annotatedNode
            if (AstAnnotationUtils.findAnnotation(method, Override.class) != null) {
                MethodNode overridden = findOverriddenMethod(method)
                if (overridden != null) {
                    AnnotationNode ann = findAnnotationWithStereoType(overridden, stereotype)
                    if(ann != null) {
                        return ann
                    }
                    else {
                        return findAnnotationWithStereoType(overridden.declaringClass, stereotype)
                    }
                }
            }
        }
        return null
    }

    /**
     * Find all the annotations for the given stereotype
     *
     * @param annotatedNode The annotated node
     * @param stereotype The stereotype
     * @return A list of annotations
     */
    List<AnnotationNode> findAnnotationsWithStereoType(AnnotatedNode annotatedNode, Class<? extends Annotation> stereotype) {
        List<AnnotationNode> foundAnnotations = []
        findAnnotationsInternal(annotatedNode, stereotype, foundAnnotations)
        return foundAnnotations.unique()
    }

    private void findAnnotationsInternal(AnnotatedNode annotatedNode, Class<? extends Annotation> stereotype, List<AnnotationNode>
            foundAnnotations) {
        AnnotationNode foundAnn = findAnnotationWithStereoType(annotatedNode, stereotype)
        if(foundAnn != null) {
            foundAnnotations.add(foundAnn)
        }
        List<AnnotationNode> annotations = annotatedNode.getAnnotations()
        for (AnnotationNode ann in annotations) {
            ClassNode annotationClassNode = ann.classNode
            if (findAnnotationWithStereoType(ann.classNode, stereotype) != null) {
                foundAnnotations.add(ann)
            } else if (!(annotationClassNode.name in [Retention.name, Documented.name, Target.name])) {
                findAnnotationsInternal(ann.classNode, stereotype, foundAnnotations)
            }
        }
    }

    private MethodNode findOverriddenMethod(MethodNode methodNode) {
        ClassNode classNode = methodNode.getDeclaringClass()

        String methodName = methodNode.name
        Parameter[] methodParameters = methodNode.parameters

        while(classNode != null && classNode.name != Object.name) {

            for(i in classNode.getAllInterfaces()) {
                MethodNode parent = i.getDeclaredMethod(methodName, methodParameters)
                if(parent != null) {
                    return parent
                }
            }
            classNode = classNode.superClass
            if(classNode != null && classNode.name != Object.name) {
                MethodNode parent = classNode.getDeclaredMethod(methodName, methodParameters)
                if(parent != null) {
                    return parent
                }
            }
        }

        return null
    }

    boolean isAttributeTrue(AnnotatedNode node, String annotation, String attribute) {
        AnnotationNode ann = AstAnnotationUtils.findAnnotation(node, annotation)
        if(ann != null) {
            def attr = ann.getMember(attribute)
            if(attr instanceof ConstantExpression) {
                ConstantExpression ce = (ConstantExpression)attr
                if(ce.value instanceof Boolean) {
                    return ce.value
                }
            }
        }
        return false
    }
}
