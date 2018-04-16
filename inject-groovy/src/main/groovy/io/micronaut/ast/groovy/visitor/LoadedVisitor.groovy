package io.micronaut.ast.groovy.visitor

import groovy.transform.CompileStatic
import io.micronaut.ast.groovy.utils.AstAnnotationUtils
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.inject.visitor.Element
import io.micronaut.inject.visitor.TypeElementVisitor
import org.codehaus.groovy.ast.AnnotatedNode
import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.FieldNode
import org.codehaus.groovy.ast.GenericsType
import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.ast.PropertyNode
import org.codehaus.groovy.ast.Variable

class LoadedVisitor {

    private final TypeElementVisitor visitor
    private final String classAnnotation
    private final String elementAnnotation

    LoadedVisitor(TypeElementVisitor visitor) {
        this.visitor = visitor
        ClassNode classNode = ClassHelper.make(visitor.getClass())
        ClassNode definition = classNode.getAllInterfaces().find {
            it.name == TypeElementVisitor.class.name
        }
        GenericsType[] generics = definition.getGenericsTypes()
        classAnnotation = generics[0].type.name
        elementAnnotation = generics[1].type.name
    }

    boolean matches(ClassNode classNode, AnnotatedNode astNode) {
        AnnotationMetadata elementMetadata = AstAnnotationUtils.getAnnotationMetadata(astNode)

        if (classAnnotation == ClassHelper.OBJECT) {
            if (elementAnnotation == ClassHelper.OBJECT) {
                return true
            } else {
                return elementMetadata.hasAnnotation(elementAnnotation)
            }
        } else {
            AnnotationMetadata classMetadata = AstAnnotationUtils.getAnnotationMetadata(classNode)
            if (classMetadata.hasAnnotation(classAnnotation)) {
                if (elementAnnotation == ClassHelper.OBJECT) {
                    return true
                } else {
                    return elementMetadata.hasAnnotation(elementAnnotation)
                }
            } else {
                return false
            }
        }
    }

    void visit(AnnotatedNode annotatedNode, GroovyVisitorContext context) {
        switch (annotatedNode.getClass()) {
            case FieldNode:
            case PropertyNode:
                visitor.visitField(new GroovyFieldElement((Variable) annotatedNode), context)
                break
            case MethodNode:
                visitor.visitMethod(new GroovyMethodElement((MethodNode) annotatedNode), context)
                break
            case ClassNode:
                visitor.visitClass(new GroovyClassElement((ClassNode) annotatedNode), context)
                break
        }
    }
}
