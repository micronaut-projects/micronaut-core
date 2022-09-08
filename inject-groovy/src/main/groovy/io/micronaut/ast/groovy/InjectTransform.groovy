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
import io.micronaut.ast.groovy.utils.InMemoryByteCodeGroovyClassLoader
import io.micronaut.ast.groovy.utils.InMemoryClassWriterOutputVisitor
import io.micronaut.ast.groovy.visitor.GroovyPackageElement
import io.micronaut.ast.groovy.visitor.GroovyVisitorContext
import io.micronaut.context.annotation.Configuration
import io.micronaut.context.annotation.Context
import io.micronaut.inject.ProcessingException
import io.micronaut.inject.processing.BeanDefinitionBuilder
import io.micronaut.inject.processing.BeanDefinitionBuilderFactory
import io.micronaut.inject.visitor.VisitorConfiguration
import io.micronaut.inject.writer.BeanConfigurationWriter
import io.micronaut.inject.writer.BeanDefinitionReferenceWriter
import io.micronaut.inject.writer.BeanDefinitionVisitor
import io.micronaut.inject.writer.ClassWriterOutputVisitor
import io.micronaut.inject.writer.DirectoryClassWriterOutputVisitor
import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.AnnotatedNode
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.InnerClassNode
import org.codehaus.groovy.ast.ModuleNode
import org.codehaus.groovy.ast.PackageNode
import org.codehaus.groovy.control.CompilationUnit
import org.codehaus.groovy.control.CompilePhase
import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.control.io.StringReaderSource
import org.codehaus.groovy.transform.ASTTransformation
import org.codehaus.groovy.transform.GroovyASTTransformation

import java.lang.reflect.Modifier
/**
 * An AST transformation that produces metadata for use by the injection container
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@CompileStatic
// IMPORTANT NOTE: This transform runs in phase CANONICALIZATION so it runs after TypeElementVisitorTransform
@GroovyASTTransformation(phase = CompilePhase.CANONICALIZATION)
class InjectTransform implements ASTTransformation, CompilationUnitAware {

    CompilationUnit unit

    @Override
    void visit(ASTNode[] nodes, SourceUnit source) {
        ModuleNode moduleNode = source.getAST()
        Map<AnnotatedNode, BeanDefinitionVisitor> beanDefinitionWriters = [:]
        File classesDir = source.configuration.targetDirectory
        boolean defineClassesInMemory = source.classLoader instanceof InMemoryByteCodeGroovyClassLoader
        ClassWriterOutputVisitor outputVisitor
        if (defineClassesInMemory) {
            outputVisitor = new InMemoryClassWriterOutputVisitor(source.classLoader as InMemoryByteCodeGroovyClassLoader)

        } else {
            outputVisitor = new DirectoryClassWriterOutputVisitor(classesDir)
        }
        List<ClassNode> classes = moduleNode.getClasses()
        if (classes.size() == 1) {
            ClassNode classNode = classes[0]
            if (classNode.nameWithoutPackage == 'package-info') {
                PackageNode packageNode = classNode.getPackage()
                GroovyVisitorContext visitorContext = new GroovyVisitorContext(source, unit)
                GroovyPackageElement groovyPackageElement = new GroovyPackageElement(visitorContext, packageNode, visitorContext.getElementAnnotationMetadataFactory())
                if (groovyPackageElement.hasStereotype(Configuration)) {
                    BeanConfigurationWriter writer = new BeanConfigurationWriter(
                            classNode.packageName,
                            groovyPackageElement,
                            groovyPackageElement.getAnnotationMetadata()
                    )
                    try {
                        writer.accept(outputVisitor)
                        outputVisitor.finish()
                    } catch (Throwable e) {
                        AstMessageUtils.error(source, classNode, "Error generating bean configuration for package-info class [${classNode.name}]: $e.message")
                    }
                }
                return
            }
        }

        GroovyVisitorContext groovyVisitorContext = new GroovyVisitorContext(source, unit) {
            @Override
            VisitorConfiguration getConfiguration() {
                new VisitorConfiguration() {
                    @Override
                    boolean includeTypeLevelAnnotationsInGenericArguments() {
                        return false
                    }
                }
            }
        }
        def elementAnnotationMetadataFactory = groovyVisitorContext
                .getElementAnnotationMetadataFactory()
                .readOnly()
        for (ClassNode classNode in classes) {
            if ((classNode instanceof InnerClassNode && !Modifier.isStatic(classNode.getModifiers()))) {
                continue
            }
            try {
                def classElement = groovyVisitorContext.getElementFactory().newClassElement(classNode, elementAnnotationMetadataFactory)
                BeanDefinitionBuilder beanProcessor = BeanDefinitionBuilderFactory.produce(classElement, groovyVisitorContext);
                beanProcessor.build().forEach(writer -> {
                    if (writer.getBeanTypeName() == classNode.getName()) {
                        beanDefinitionWriters.put(classNode, writer)
                    } else {
                        beanDefinitionWriters.put(new AnnotatedNode(), writer)
                    }
                })
            } catch (ProcessingException ex) {
                groovyVisitorContext.fail(ex.getMessage(), ex.getOriginatingElement() as ASTNode)
            }
        }

        for (entry in beanDefinitionWriters) {
            BeanDefinitionVisitor beanDefWriter = entry.value
            String beanTypeName = beanDefWriter.beanTypeName
            AnnotatedNode beanClassNode = entry.key
            try {
                BeanDefinitionReferenceWriter beanReferenceWriter = new BeanDefinitionReferenceWriter(beanDefWriter)
                beanReferenceWriter.setRequiresMethodProcessing(beanDefWriter.requiresMethodProcessing())
                beanReferenceWriter.setContextScope(beanDefWriter.getAnnotationMetadata().hasDeclaredAnnotation(Context))
                beanDefWriter.visitBeanDefinitionEnd()
                if (classesDir != null) {
                    beanReferenceWriter.accept(outputVisitor)
                    beanDefWriter.accept(outputVisitor)
                } else if (source.source instanceof StringReaderSource && defineClassesInMemory) {
                    beanReferenceWriter.accept(outputVisitor)
                    beanDefWriter.accept(outputVisitor)
                }
            } catch (Throwable e) {
                AstMessageUtils.error(source, beanClassNode, "Error generating bean definition class for dependency injection of class [${beanTypeName}]: $e.message")
                e.printStackTrace(System.err)
            }
        }
        if (!beanDefinitionWriters.isEmpty()) {
            try {
                outputVisitor.finish()
            } catch (Throwable e) {
                AstMessageUtils.error(source, moduleNode, "Error generating META-INF/services files: $e.message")
                if (e.message == null) {
                    e.printStackTrace(System.err)
                }
            }
        }
    }

    @Override
    void setCompilationUnit(CompilationUnit unit) {
        this.unit = unit
    }

}
