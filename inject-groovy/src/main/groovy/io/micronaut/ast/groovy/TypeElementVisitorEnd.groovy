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
import io.micronaut.ast.groovy.utils.AstAnnotationUtils
import io.micronaut.ast.groovy.utils.AstMessageUtils
import io.micronaut.ast.groovy.utils.InMemoryByteCodeGroovyClassLoader
import io.micronaut.ast.groovy.utils.InMemoryClassWriterOutputVisitor
import io.micronaut.ast.groovy.visitor.GroovyVisitorContext
import io.micronaut.ast.groovy.visitor.LoadedVisitor
import io.micronaut.core.order.OrderUtil
import io.micronaut.core.util.CollectionUtils
import io.micronaut.inject.annotation.AbstractAnnotationMetadataBuilder
import io.micronaut.inject.writer.AbstractBeanDefinitionBuilder
import io.micronaut.inject.writer.BeanDefinitionReferenceWriter
import io.micronaut.inject.writer.BeanDefinitionWriter
import io.micronaut.inject.writer.ClassWriterOutputVisitor
import io.micronaut.inject.writer.DirectoryClassWriterOutputVisitor
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
        Map<String, LoadedVisitor> loadedVisitors = TypeElementVisitorTransform.loadedVisitors

        if (loadedVisitors != null) {
            List<LoadedVisitor> values = new ArrayList<>(loadedVisitors.values())
            OrderUtil.reverseSort(values)
            for(loadedVisitor in values) {
                try {
                    loadedVisitor.finish(new GroovyVisitorContext(source, compilationUnit))
                } catch (Throwable e) {
                    AstMessageUtils.error(
                            source,
                            source.getAST(),
                            "Error finalizing type visitor [$loadedVisitor.visitor]: $e.message")
                }
            }
        }

        final List<AbstractBeanDefinitionBuilder> beanDefinitionBuilders = TypeElementVisitorTransform.beanDefinitionBuilders
        if (beanDefinitionBuilders) {
            File classesDir = compilationUnit.configuration.targetDirectory
            ClassWriterOutputVisitor classWriterOutputVisitor
            if (source.classLoader instanceof InMemoryByteCodeGroovyClassLoader) {
                classWriterOutputVisitor = new InMemoryClassWriterOutputVisitor(source.classLoader as InMemoryByteCodeGroovyClassLoader)
            } else {
                classWriterOutputVisitor = new DirectoryClassWriterOutputVisitor(classesDir)
            }
            for (AbstractBeanDefinitionBuilder beanDefinitionBuilder : beanDefinitionBuilders) {
                final BeanDefinitionWriter beanDefinitionWriter = beanDefinitionBuilder.build()
                if (beanDefinitionWriter != null) {
                    try {
                        beanDefinitionWriter.accept(classWriterOutputVisitor)
                        String beanTypeName = beanDefinitionWriter.getBeanTypeName()
                        BeanDefinitionReferenceWriter beanDefinitionReferenceWriter =
                                new BeanDefinitionReferenceWriter(beanTypeName, beanDefinitionWriter)
                        beanDefinitionReferenceWriter
                                .setRequiresMethodProcessing(beanDefinitionWriter.requiresMethodProcessing())
                        beanDefinitionReferenceWriter.accept(classWriterOutputVisitor)
                    } catch (IOException e) {
                        // raise a compile error
                        AstMessageUtils.error(
                                source,
                                source.getAST(),
                                "Error writing bean definitions: $e.message")

                    }
                }
            }
            classWriterOutputVisitor.finish()
        }

        TypeElementVisitorTransform.loadedVisitors = null
        TypeElementVisitorTransform.beanDefinitionBuilders.clear()
        AstAnnotationUtils.invalidateCache()
        AbstractAnnotationMetadataBuilder.clearMutated()
    }

    @Override
    void setCompilationUnit(CompilationUnit unit) {
        this.compilationUnit = unit
    }
}
