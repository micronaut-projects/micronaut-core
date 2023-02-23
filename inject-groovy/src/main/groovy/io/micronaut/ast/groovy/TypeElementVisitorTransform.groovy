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
import io.micronaut.ast.groovy.visitor.GroovyClassElement
import io.micronaut.ast.groovy.visitor.GroovyVisitorContext
import io.micronaut.ast.groovy.visitor.LoadedVisitor
import io.micronaut.core.annotation.Generated
import io.micronaut.core.order.OrderUtil
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.ElementQuery
import io.micronaut.inject.ast.EnumConstantElement
import io.micronaut.inject.ast.FieldElement
import io.micronaut.inject.ast.MemberElement
import io.micronaut.inject.ast.MethodElement
import io.micronaut.inject.ast.PropertyElement
import io.micronaut.inject.processing.ProcessingException
import io.micronaut.inject.writer.AbstractBeanDefinitionBuilder
import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.ConstructorNode
import org.codehaus.groovy.ast.InnerClassNode
import org.codehaus.groovy.ast.ModuleNode
import org.codehaus.groovy.control.CompilationUnit
import org.codehaus.groovy.control.CompilePhase
import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.transform.ASTTransformation
import org.codehaus.groovy.transform.GroovyASTTransformation

import java.lang.reflect.Modifier
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
    protected static ThreadLocal<Map<String, LoadedVisitor>> loadedVisitors = new ThreadLocal<>()
    protected static ThreadLocal<List<AbstractBeanDefinitionBuilder>> beanDefinitionBuilders = ThreadLocal.withInitial({ -> [] })
    private CompilationUnit compilationUnit

    @Override
    void visit(ASTNode[] nodes, SourceUnit source) {
        ModuleNode moduleNode = source.getAST()
        List<ClassNode> classes = moduleNode.getClasses()
        Map<String, LoadedVisitor> visitors = loadedVisitors.get()
        if (visitors == null) return

        GroovyVisitorContext visitorContext = new GroovyVisitorContext(source, compilationUnit)

        List<LoadedVisitor> sortedVisitors = new ArrayList<>(visitors.values())
        OrderUtil.reverseSort(sortedVisitors)

        // The visitor X with a higher priority should process elements of A before
        // the visitor Y which is processing elements of B but also using elements A

        // Micronaut Data use-case: EntityMapper with a higher priority needs to process entities first
        // before RepositoryMapper is going to process repositories and read entities

        for (LoadedVisitor loadedVisitor : sortedVisitors) {
            for (ClassNode classNode in classes) {
                if (!(classNode instanceof InnerClassNode && !Modifier.isStatic(classNode.getModifiers())) && classNode.getAnnotations(generatedNode).empty) {
                    ClassElement targetClassElement = visitorContext.getElementFactory().newSourceClassElement(classNode, visitorContext.getElementAnnotationMetadataFactory())
                    if (!loadedVisitor.matchesClass(targetClassElement)) {
                        continue
                    }
                    try {
                        def visitor = new ElementVisitor(source, compilationUnit, classNode, [loadedVisitor], visitorContext, targetClassElement)
                        visitor.visitClass(classNode)
                    } catch (ProcessingException ex) {
                        visitorContext.fail(ex.getMessage(), ex.getOriginatingElement() as ASTNode)
                    }
                }
            }
        }

        loadedVisitors.set(visitors)
        beanDefinitionBuilders.get().addAll(visitorContext.getBeanElementBuilders())
    }

    @Override
    void setCompilationUnit(CompilationUnit unit) {
        this.compilationUnit = unit
    }

    @CompileStatic
    private static class ElementVisitor {

        final SourceUnit sourceUnit
        final CompilationUnit compilationUnit
        final GroovyVisitorContext visitorContext
        private final ClassNode concreteClass
        private final Collection<LoadedVisitor> typeElementVisitors

        private ClassElement targetClassElement

        ElementVisitor(
                SourceUnit sourceUnit,
                CompilationUnit compilationUnit,
                ClassNode targetClassNode,
                Collection<LoadedVisitor> typeElementVisitors,
                GroovyVisitorContext visitorContext,
                ClassElement targetClassElement) {
            this.targetClassElement = targetClassElement
            this.compilationUnit = compilationUnit
            this.typeElementVisitors = typeElementVisitors
            this.concreteClass = targetClassNode
            this.sourceUnit = sourceUnit
            this.visitorContext = visitorContext
        }

        void visitClass(ClassNode node) {
            if ((targetClassElement as GroovyClassElement).getNativeType().annotatedNode() != node) {
                targetClassElement = visitorContext.getElementFactory().newSourceClassElement(node, visitorContext.getElementAnnotationMetadataFactory())
            }
            for (LoadedVisitor it : typeElementVisitors) {
                if (it.matchesClass(targetClassElement)) {
                    it.getVisitor().visitClass(targetClassElement, visitorContext)
                }
            }
            GroovyClassElement classElement = targetClassElement as GroovyClassElement
            def properties = classElement.getSyntheticBeanProperties()
            for (PropertyElement pn : (properties)) {
                visitNativeProperty(pn)
            }
            for (ConstructorNode cn : node.getDeclaredConstructors()) {
                visitConstructor(cn)
            }
            for (MemberElement memberElement : classElement.getSourceEnclosedElements(ElementQuery.ALL_FIELD_AND_METHODS)) {
                if (memberElement instanceof FieldElement) {
                    visitField(memberElement)
                } else if (memberElement instanceof MethodElement) {
                    visitMethod(memberElement)
                } else {
                    throw new IllegalStateException("Unknown element: " + memberElement)
                }
            }
        }

        void visitConstructor(ConstructorNode node) {
            def e = visitorContext.getElementFactory()
                    .newConstructorElement(targetClassElement, node, visitorContext.getElementAnnotationMetadataFactory())
            for (LoadedVisitor it : typeElementVisitors) {
                if (it.matchesElement(e)) {
                    it.getVisitor().visitConstructor(e, visitorContext)
                }
            }
        }

        void visitMethod(MethodElement e) {
            for (LoadedVisitor it : typeElementVisitors) {
                if (it.matchesElement(e)) {
                    it.getVisitor().visitMethod(e, visitorContext)
                }
            }
        }

        void visitField(FieldElement fieldElement) {
            if (fieldElement instanceof EnumConstantElement) {
                EnumConstantElement enumConstantElement = fieldElement
                for (LoadedVisitor it : typeElementVisitors) {
                    if (it.matchesElement(enumConstantElement)) {
                        it.getVisitor().visitEnumConstant(enumConstantElement, visitorContext)
                    }
                }
            } else {
                for (LoadedVisitor it : typeElementVisitors) {
                    if (it.matchesElement(fieldElement)) {
                        it.getVisitor().visitField(fieldElement, visitorContext)
                    }
                }
            }
        }

        void visitNativeProperty(PropertyElement propertyNode) {
            for (LoadedVisitor it : typeElementVisitors) {
                if (it.matchesElement(propertyNode)) {
                    propertyNode.field.ifPresent(f -> it.getVisitor().visitField(f, visitorContext))
                    // visit synthetic getter/setter methods
                    propertyNode.writeMethod.ifPresent(m -> it.getVisitor().visitMethod(m, visitorContext))
                    propertyNode.readMethod.ifPresent(m -> it.getVisitor().visitMethod(m, visitorContext))
                }
            }
        }
    }
}
