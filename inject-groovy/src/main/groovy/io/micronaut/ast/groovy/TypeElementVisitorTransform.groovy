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
import io.micronaut.ast.groovy.utils.AstMessageUtils
import io.micronaut.ast.groovy.visitor.GroovyClassElement
import io.micronaut.ast.groovy.visitor.GroovyGeneratedSourceFiles
import io.micronaut.ast.groovy.visitor.GroovyNativeElement
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
import io.micronaut.inject.visitor.TypeElementQuery
import io.micronaut.inject.writer.AbstractBeanDefinitionBuilder
import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.CompileUnit
import org.codehaus.groovy.ast.ConstructorNode
import org.codehaus.groovy.ast.InnerClassNode
import org.codehaus.groovy.ast.ModuleNode
import org.codehaus.groovy.control.CompilationUnit
import org.codehaus.groovy.control.CompilePhase
import org.codehaus.groovy.control.Phases
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
// IMPORTANT NOTE: This transform is registered for SEMANTIC_ANALYSIS, but the visiting itself happens at the end
// of CANONICALIZATION, after Groovy's own local transforms of that phase and before InjectTransform (see visit)
@GroovyASTTransformation(phase = CompilePhase.SEMANTIC_ANALYSIS)
class TypeElementVisitorTransform implements ASTTransformation, CompilationUnitAware {

    private static ClassNode generatedNode = new ClassNode(Generated)
    protected static ThreadLocal<Map<String, LoadedVisitor>> loadedVisitors = new ThreadLocal<>()
    protected static ThreadLocal<List<AbstractBeanDefinitionBuilder>> beanDefinitionBuilders = ThreadLocal.withInitial({ -> [] })
    /**
     * Whether classes were visited since the visitors last finished a round.
     */
    protected static ThreadLocal<Boolean> roundPending = ThreadLocal.withInitial({ -> Boolean.FALSE })
    private final Set<SourceUnit> finishedRoundSources = Collections.newSetFromMap(new IdentityHashMap<SourceUnit, Boolean>())
    private CompilationUnit compilationUnit

    /**
     * Groovy runs the global transforms of a phase before the local ones, so a transform invoked here would see a
     * record before {@code RecordTypeASTTransformation} has made it one, and a {@code @TupleConstructor},
     * {@code @Canonical} or {@code @Immutable} class before its constructors and methods exist. The visiting is
     * therefore deferred to a phase operation for CANONICALIZATION: registered from an earlier phase, the compiler
     * appends it after every operation of that phase, the local transforms included, and it still runs before the
     * operation writing the bean definitions. The operations are registered once per compilation unit and process
     * every source unit of the compilation, the ones queued later included.
     *
     * <p>The bean definitions and the queueing of the generated sources are registered here as well, in that order,
     * so that all three run in CANONICALIZATION ahead of the check of the source queue that ends the phase: the
     * generated classes then exist before the hand-written sources are type checked, in INSTRUCTION_SELECTION.</p>
     */
    @Override
    void visit(ASTNode[] nodes, SourceUnit source) {
        if (compilationUnit == null) {
            visitTypes(source)
            return
        }
        CompileUnit ast = compilationUnit.getAST()
        if (ast.getNodeMetaData(TypeElementVisitorTransform) == null) {
            ast.putNodeMetaData(TypeElementVisitorTransform, Boolean.TRUE)
            compilationUnit.addNewPhaseOperation({ SourceUnit sourceUnit -> visitTypes(sourceUnit) } as CompilationUnit.ISourceUnitOperation, Phases.CANONICALIZATION)
            compilationUnit.addNewPhaseOperation({ SourceUnit sourceUnit -> finishRound(sourceUnit) } as CompilationUnit.ISourceUnitOperation, Phases.CANONICALIZATION)
            InjectTransform.registerInjection(compilationUnit)
            GroovyGeneratedSourceFiles.registerCanonicalizationQueue(compilationUnit)
        }
    }

    private void visitTypes(SourceUnit source) {
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
                        def visitor = new ElementVisitor(source, compilationUnit, classNode, loadedVisitor, visitorContext, targetClassElement)
                        visitor.visitClass(classNode)
                    } catch (ProcessingException ex) {
                        def element = ex.getOriginatingElement()
                        if (element instanceof ASTNode) {
                            visitorContext.fail(ex.getMessage(), element as ASTNode)
                        } else if (element instanceof GroovyNativeElement) {
                            visitorContext.fail(ex.getMessage(), (element as GroovyNativeElement).annotatedNode())
                        } else {
                            visitorContext.fail(ex.getMessage(), (ASTNode) null)
                        }
                    }
                }
            }
        }

        loadedVisitors.set(visitors)
        beanDefinitionBuilders.get().addAll(visitorContext.getBeanElementBuilders())
        roundPending.set(Boolean.TRUE)

        visitorContext.finish()
    }

    /**
     * Ends the round of the sources the compiler is taking through canonicalization: the classes of the compilation,
     * then each set of generated sources it queues. A phase operation runs for every source before the next operation
     * runs for any, so when this one runs for the first source of the round, every source of the round has been
     * visited. It runs before the bean definitions are written and the generated sources are queued.
     */
    private void finishRound(SourceUnit source) {
        if (!finishedRoundSources.add(source)) {
            return
        }
        Iterator<SourceUnit> sources = compilationUnit.iterator()
        while (sources.hasNext()) {
            finishedRoundSources.add(sources.next())
        }
        finishRound(new GroovyVisitorContext(source, compilationUnit), source)
    }

    /**
     * Calls {@link io.micronaut.inject.visitor.TypeElementVisitor#finishRound} on the loaded visitors, when a class was
     * visited since the last call.
     *
     * @param visitorContext The visitor context
     * @param sourceUnit The source unit of the visitor context
     */
    static void finishRound(GroovyVisitorContext visitorContext, SourceUnit sourceUnit) {
        Map<String, LoadedVisitor> visitors = loadedVisitors.get()
        if (visitors == null || !roundPending.get()) {
            return
        }
        roundPending.set(Boolean.FALSE)
        List<LoadedVisitor> sortedVisitors = new ArrayList<>(visitors.values())
        OrderUtil.reverseSort(sortedVisitors)
        for (LoadedVisitor loadedVisitor : sortedVisitors) {
            try {
                loadedVisitor.getVisitor().finishRound(visitorContext)
            } catch (ProcessingException ex) {
                def element = ex.getOriginatingElement()
                visitorContext.fail(ex.getMessage(), element instanceof GroovyNativeElement ? (element as GroovyNativeElement).annotatedNode() : (ASTNode) null)
            } catch (Throwable e) {
                AstMessageUtils.error(
                        sourceUnit,
                        sourceUnit.getAST(),
                        "Error finishing the round of type visitor [$loadedVisitor.visitor]: $e.message")
            }
        }
        beanDefinitionBuilders.get().addAll(visitorContext.getBeanElementBuilders())
        visitorContext.finish()
    }

    @Override
    void setCompilationUnit(CompilationUnit unit) {
        this.compilationUnit = unit
    }

    @CompileStatic
    private static final class ElementVisitor {

        final SourceUnit sourceUnit
        final CompilationUnit compilationUnit
        final GroovyVisitorContext visitorContext
        private final ClassNode concreteClass
        private final LoadedVisitor visitor

        private ClassElement targetClassElement

        ElementVisitor(
                SourceUnit sourceUnit,
                CompilationUnit compilationUnit,
                ClassNode targetClassNode,
                LoadedVisitor visitor,
                GroovyVisitorContext visitorContext,
                ClassElement targetClassElement) {
            this.targetClassElement = targetClassElement
            this.compilationUnit = compilationUnit
            this.visitor = visitor
            this.concreteClass = targetClassNode
            this.sourceUnit = sourceUnit
            this.visitorContext = visitorContext
        }

        void visitClass(ClassNode node) {
            def classElement = targetClassElement as GroovyClassElement
            if (classElement.getNativeType().annotatedNode() != node) {
                targetClassElement = visitorContext.getElementFactory().newSourceClassElement(node, visitorContext.getElementAnnotationMetadataFactory())
            }
            if (visitor.matchesClass(targetClassElement)) {
                visitor.getVisitor().visitClass(targetClassElement, visitorContext)
            }
            def query = visitor.getVisitor().query()
            boolean includesFields = query.includesFields() || query.includesEnumConstants()
            boolean includesMethods = query.includesMethods()
            if (includesFields || includesMethods) {
                for (PropertyElement pn : classElement.getSyntheticBeanProperties()) {
                    visitNativeProperty(pn, query)
                }
            }
            if (query.includesConstructors()) {
                for (ConstructorNode cn : node.getDeclaredConstructors()) {
                    visitConstructor(cn)
                }
            }
            List<? extends MemberElement> elements
            if (includesMethods && includesFields) {
                elements = classElement.getSourceEnclosedElements(ElementQuery.ALL_FIELD_AND_METHODS)
            } else if (includesMethods) {
                elements = classElement.getSourceEnclosedElements(ElementQuery.ALL_METHODS)
            } else if (includesFields) {
                elements = classElement.getSourceEnclosedElements(ElementQuery.ALL_FIELDS)
            } else {
                elements = List.of()
            }
            for (MemberElement memberElement : elements) {
                if (memberElement instanceof EnumConstantElement) {
                    if (query.includeEnumConstants()) {
                        visitEnumConstant(memberElement)
                    }
                } else if (memberElement instanceof FieldElement) {
                    if (query.includesFields()) {
                        visitField(memberElement)
                    }
                } else if (memberElement instanceof MethodElement) {
                    if (query.includesMethods()) {
                        visitMethod(memberElement)
                    }
                } else {
                    throw new IllegalStateException("Unknown element: " + memberElement)
                }
            }
        }

        private void visitConstructor(ConstructorNode node) {
            def e = visitorContext.getElementFactory()
                    .newConstructorElement(targetClassElement, node, visitorContext.getElementAnnotationMetadataFactory())
            if (visitor.matchesElement(e)) {
                visitor.getVisitor().visitConstructor(e, visitorContext)
            }
        }

        private void visitMethod(MethodElement e) {
            if (visitor.matchesElement(e)) {
                visitor.getVisitor().visitMethod(e, visitorContext)
            }
        }

        private void visitField(FieldElement fieldElement) {
            if (visitor.matchesElement(fieldElement)) {
                visitor.getVisitor().visitField(fieldElement, visitorContext)
            }
        }

        private void visitEnumConstant(EnumConstantElement enumConstantElement) {
            if (visitor.matchesElement(enumConstantElement)) {
                visitor.getVisitor().visitEnumConstant(enumConstantElement, visitorContext)
            }
        }

        private void visitNativeProperty(PropertyElement propertyNode, TypeElementQuery query) {
            if (visitor.matchesElement(propertyNode)) {
                if (query.includesFields()) {
                    propertyNode.field.ifPresent(f -> visitor.getVisitor().visitField(f, visitorContext))
                }
                if (query.includesMethods()) {
                    // visit synthetic getter/setter methods
                    propertyNode.writeMethod.ifPresent(m -> visitor.getVisitor().visitMethod(m, visitorContext))
                    propertyNode.readMethod.ifPresent(m -> visitor.getVisitor().visitMethod(m, visitorContext))
                }
            }
        }
    }
}
