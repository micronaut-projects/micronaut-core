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
package io.micronaut.ast.groovy

import groovy.transform.CompilationUnitAware
import groovy.transform.CompileStatic
import groovy.transform.PackageScope
import io.micronaut.aop.Introduction
import io.micronaut.ast.groovy.utils.AstAnnotationUtils
import io.micronaut.ast.groovy.utils.AstMessageUtils
import io.micronaut.ast.groovy.utils.PublicAbstractMethodVisitor
import io.micronaut.ast.groovy.visitor.GroovyVisitorContext
import io.micronaut.ast.groovy.visitor.LoadedVisitor
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.core.annotation.Generated
import io.micronaut.core.annotation.Introspected
import io.micronaut.core.io.service.ServiceDefinition
import io.micronaut.core.io.service.SoftServiceLoader
import io.micronaut.core.order.OrderUtil
import io.micronaut.inject.ast.Element
import io.micronaut.inject.visitor.TypeElementVisitor
import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.AnnotatedNode
import org.codehaus.groovy.ast.ClassCodeVisitorSupport
import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.FieldNode
import org.codehaus.groovy.ast.InnerClassNode
import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.ast.ModuleNode
import org.codehaus.groovy.ast.PropertyNode
import org.codehaus.groovy.control.CompilationUnit
import org.codehaus.groovy.control.CompilePhase
import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.transform.ASTTransformation
import org.codehaus.groovy.transform.GroovyASTTransformation

import java.lang.reflect.Modifier

import static org.codehaus.groovy.ast.ClassHelper.makeCached

/**
 * Executes type element visitors.
 *
 * @author James Kleeh
 * @author Graeme Rocher
 * @since 1.0
 */
@CompileStatic
// IMPORTANT NOTE: This transform runs in phase SEMANTIC_ANALYSIS so it runs before InjectTransform
@GroovyASTTransformation(phase = CompilePhase.SEMANTIC_ANALYSIS)
class TypeElementVisitorTransform implements ASTTransformation, CompilationUnitAware {

    private static ClassNode generatedNode = new ClassNode(Generated)
    protected static Map<String, LoadedVisitor> loadedVisitors = null
    private CompilationUnit compilationUnit

    @Override
    void visit(ASTNode[] nodes, SourceUnit source) {
        ModuleNode moduleNode = source.getAST()
        List<ClassNode> classes = moduleNode.getClasses()

        if (loadedVisitors == null) return

        GroovyVisitorContext visitorContext = new GroovyVisitorContext(source, compilationUnit)
        for (ClassNode classNode in classes) {
            if (!(classNode instanceof InnerClassNode && !Modifier.isStatic(classNode.getModifiers())) && classNode.getAnnotations(generatedNode).empty) {
                Collection<LoadedVisitor> matchedVisitors = loadedVisitors.values().findAll { v -> v.matches(classNode) }

                List<LoadedVisitor> values = new ArrayList<>(matchedVisitors)
                OrderUtil.reverseSort(values)
                def annotationMetadata = AstAnnotationUtils.getAnnotationMetadata(source, compilationUnit, classNode)
                def isIntroduction = annotationMetadata.hasStereotype(Introduction.class)
                def visitor = new ElementVisitor(source, compilationUnit, classNode, values, visitorContext, !isIntroduction)
                if (isIntroduction || (annotationMetadata.hasStereotype(Introspected.class) && classNode.isAbstract())) {
                    visitor.visitClass(classNode)
                    new PublicAbstractMethodVisitor(source, compilationUnit) {
                        @Override
                        void accept(ClassNode cn, MethodNode methodNode) {
                            visitor.doVisitMethod(methodNode)
                        }
                    }.accept(classNode)
                } else {
                    visitor.visitClass(classNode)
                }
            }
        }
    }

    @Override
    void setCompilationUnit(CompilationUnit unit) {
        this.compilationUnit = unit
    }

    private static class ElementVisitor extends ClassCodeVisitorSupport {

        final SourceUnit sourceUnit
        final CompilationUnit compilationUnit
        final AnnotationMetadata annotationMetadata
        final GroovyVisitorContext visitorContext
        final boolean visitMethods
        private final ClassNode concreteClass
        private final Collection<LoadedVisitor> typeElementVisitors

        ElementVisitor(
                SourceUnit sourceUnit,
                CompilationUnit compilationUnit,
                ClassNode targetClassNode,
                Collection<LoadedVisitor> typeElementVisitors,
                GroovyVisitorContext visitorContext,
                boolean visitMethods = true) {
            this.compilationUnit = compilationUnit
            this.typeElementVisitors = typeElementVisitors
            this.concreteClass = targetClassNode
            this.sourceUnit = sourceUnit
            this.annotationMetadata = AstAnnotationUtils.getAnnotationMetadata(sourceUnit, compilationUnit, targetClassNode)
            this.visitorContext = visitorContext
            this.visitMethods = visitMethods
        }

        protected boolean isPackagePrivate(AnnotatedNode annotatedNode, int modifiers) {
            return ((!Modifier.isProtected(modifiers) && !Modifier.isPublic(modifiers) && !Modifier.isPrivate(modifiers)) || !annotatedNode.getAnnotations(makeCached(PackageScope)).isEmpty())
        }

        @Override
        void visitClass(ClassNode node) {
            AnnotationMetadata annotationMetadata = AstAnnotationUtils.getAnnotationMetadata(sourceUnit, compilationUnit, node)
            typeElementVisitors.each {
                def element = it.visit(node, annotationMetadata, visitorContext)
                if (element != null) {
                    annotationMetadata = element.annotationMetadata
                }
            }

            ClassNode superClass = node.getSuperClass()
            List<ClassNode> superClasses = []
            while (superClass != null) {
                superClasses.add(superClass)
                superClass = superClass.getSuperClass()
            }
            superClasses = superClasses.reverse()
            for (classNode in superClasses) {
                if (classNode.name != ClassHelper.OBJECT_TYPE.name && classNode.name != GroovyObjectSupport.name && classNode.name != Script.name) {
                    classNode.visitContents(this)
                }
            }
            super.visitClass(node)
        }

        @Override
        protected void visitConstructorOrMethod(MethodNode methodNode, boolean isConstructor) {
            if (visitMethods) {
                doVisitMethod(methodNode)
            }
        }

        void doVisitMethod(MethodNode methodNode) {
            AnnotationMetadata methodAnnotationMetadata = AstAnnotationUtils.getAnnotationMetadata(sourceUnit, compilationUnit, methodNode)
            typeElementVisitors.findAll { it.matches(methodAnnotationMetadata) }.each {
                def element = it.visit(methodNode, methodAnnotationMetadata, visitorContext)
                if (element != null) {
                    methodAnnotationMetadata = element.annotationMetadata
                }
            }
        }

        @Override
        void visitField(FieldNode fieldNode) {
            if (fieldNode.name == 'metaClass') return
            int modifiers = fieldNode.modifiers
            if (Modifier.isFinal(modifiers) || Modifier.isStatic(modifiers)) {
                return
            }
            if (fieldNode.isSynthetic() && !isPackagePrivate(fieldNode, fieldNode.modifiers)) {
                return
            }
            AnnotationMetadata fieldAnnotationMetadata = AstAnnotationUtils.getAnnotationMetadata(sourceUnit, compilationUnit, fieldNode)
            typeElementVisitors.findAll { it.matches(fieldAnnotationMetadata) }.each {
                def element = it.visit(fieldNode, fieldAnnotationMetadata, visitorContext)
                if (element != null) {
                    fieldAnnotationMetadata = element.annotationMetadata
                }
            }
        }

        @Override
        void visitProperty(PropertyNode propertyNode) {
            FieldNode fieldNode = propertyNode.field
            if (fieldNode.name == 'metaClass') return
            def modifiers = propertyNode.getModifiers()
            if (Modifier.isFinal(modifiers) || Modifier.isStatic(modifiers)) {
                return
            }
            AnnotationMetadata fieldAnnotationMetadata = AstAnnotationUtils.getAnnotationMetadata(sourceUnit, compilationUnit, fieldNode)
            typeElementVisitors.findAll { it.matches(fieldAnnotationMetadata) }.each {
                def element = it.visit(fieldNode, fieldAnnotationMetadata, visitorContext)
                if (element != null) {
                    fieldAnnotationMetadata = element.annotationMetadata
                }
            }
        }
    }
}
