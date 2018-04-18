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
    private final GroovyVisitorContext visitorContext

    LoadedVisitor(TypeElementVisitor visitor, GroovyVisitorContext visitorContext) {
        this.visitorContext = visitorContext
        this.visitor = visitor
        ClassNode classNode = ClassHelper.make(visitor.getClass())
        ClassNode definition = classNode.getAllInterfaces().find {
            it.name == TypeElementVisitor.class.name
        }
        GenericsType[] generics = definition.getGenericsTypes()
        classAnnotation = generics[0].type.name
        elementAnnotation = generics[1].type.name
    }

    boolean matches(ClassNode classNode) {
        if (classAnnotation == ClassHelper.OBJECT) {
            return true
        }
        AnnotationMetadata annotationMetadata = AstAnnotationUtils.getAnnotationMetadata(classNode)
        return annotationMetadata.hasAnnotation(classAnnotation)
    }

    boolean matches(AnnotationMetadata annotationMetadata) {
        if (elementAnnotation == ClassHelper.OBJECT) {
            return true
        }
        return annotationMetadata.hasAnnotation(elementAnnotation)
    }

    void visit(AnnotatedNode annotatedNode, AnnotationMetadata annotationMetadata) {
        switch (annotatedNode.getClass()) {
            case FieldNode:
            case PropertyNode:
                visitor.visitField(new GroovyFieldElement((Variable) annotatedNode), annotationMetadata, visitorContext)
                break
            case MethodNode:
                visitor.visitMethod(new GroovyMethodElement((MethodNode) annotatedNode), annotationMetadata, visitorContext)
                break
            case ClassNode:
                visitor.visitClass(new GroovyClassElement((ClassNode) annotatedNode), annotationMetadata, visitorContext)
                break
        }
    }
}
