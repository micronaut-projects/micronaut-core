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
import io.micronaut.ast.groovy.visitor.GroovyVisitorContext
import io.micronaut.ast.groovy.visitor.LoadedVisitor
import io.micronaut.core.order.OrderUtil
import io.micronaut.inject.DefaultElementBeanDefinitionBuilderFactory
import io.micronaut.inject.OutputObjectDef
import io.micronaut.inject.visitor.VisitorContext
import io.micronaut.inject.writer.AbstractBeanDefinitionBuilder
import io.micronaut.inject.writer.BeanDefinitionWriter
import io.micronaut.inject.writer.ByteCodeWriterUtils
import io.micronaut.inject.writer.OriginatingElements
import io.micronaut.sourcegen.model.ObjectDef
import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.control.CompilationUnit
import org.codehaus.groovy.control.CompilePhase
import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.transform.ASTTransformation
import org.codehaus.groovy.transform.GroovyASTTransformation
/**
 * Finishes the type element visitors.
 *
 * @author James Kleeh
 * @since 1.0
 */
@CompileStatic
@GroovyASTTransformation(phase = CompilePhase.CLASS_GENERATION)
class TypeElementVisitorEnd implements ASTTransformation, CompilationUnitAware {

    private CompilationUnit compilationUnit

    @Override
    void visit(ASTNode[] nodes, SourceUnit source) {
        Map<String, LoadedVisitor> loadedVisitors = TypeElementVisitorTransform.loadedVisitors.get()

        GroovyVisitorContext visitorContext = new GroovyVisitorContext(source, compilationUnit)
        if (loadedVisitors != null) {
            List<LoadedVisitor> values = new ArrayList<>(loadedVisitors.values())
            OrderUtil.reverseSort(values)
            for (loadedVisitor in values) {
                try {
                    loadedVisitor.finish(visitorContext)
                } catch (Throwable e) {
                    AstMessageUtils.error(
                            source,
                            source.getAST(),
                            "Error finalizing type visitor [$loadedVisitor.visitor]: $e.message")
                }
            }
        }

        final List<AbstractBeanDefinitionBuilder> beanDefinitionBuilders = TypeElementVisitorTransform.beanDefinitionBuilders.get()
        if (beanDefinitionBuilders) {
            DefaultElementBeanDefinitionBuilderFactory beanDefinitionBuilderFactory = new DefaultElementBeanDefinitionBuilderFactory(visitorContext)
            try {
                List<OutputObjectDef> result = AbstractBeanDefinitionBuilder.build(
                        beanDefinitionBuilders,
                        beanDefinitionBuilderFactory
                )
                for (OutputObjectDef outputObjectDef : result) {
                    write(outputObjectDef, source, visitorContext)
                }
            } catch (Exception e) {
                // raise a compile error
                AstMessageUtils.error(
                        source,
                        source.getAST(),
                        "Error writing bean definitions: $e.message")
            }
        }

        visitorContext.finish()

        TypeElementVisitorTransform.loadedVisitors.remove()
        TypeElementVisitorTransform.beanDefinitionBuilders.remove()
        BeanDefinitionWriter.finish()
    }

    private static void write(OutputObjectDef outputObjectDef, SourceUnit source, VisitorContext visitorContext) {
        try {
            ObjectDef objectDef = outputObjectDef.objectDef();
            Class<?> serviceClass = outputObjectDef.serviceClass();
            OriginatingElements originatingElements = outputObjectDef.originatingElements();
            if (serviceClass != null) {
                visitorContext.visitServiceDescriptor(serviceClass, objectDef.getName(), originatingElements.getOriginatingElements()[0]);
            }
            try (OutputStream outputStream = visitorContext.visitClass(objectDef.getName(), originatingElements.getOriginatingElements())) {
                outputStream.write(ByteCodeWriterUtils.writeByteCode(objectDef, visitorContext));
            }
        } catch (Exception e) {
            AstMessageUtils.error(
                    source,
                    source.getAST(),
                    "Error writing bean definitions: $e.message")
        }
    }

    @Override
    void setCompilationUnit(CompilationUnit unit) {
        this.compilationUnit = unit
    }
}
