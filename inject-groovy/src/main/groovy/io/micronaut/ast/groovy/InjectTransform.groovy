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
import io.micronaut.ast.groovy.visitor.GroovyNativeElement
import io.micronaut.ast.groovy.visitor.GroovyVisitorContext
import io.micronaut.inject.processing.definition.DefaultElementBeanDefinitionBuilderFactory
import io.micronaut.inject.processing.definition.OutputObjectDef
import io.micronaut.inject.processing.BeanDefinitionCreatorFactory
import io.micronaut.inject.processing.ProcessingException
import io.micronaut.inject.visitor.VisitorContext
import io.micronaut.inject.writer.ByteCodeWriterUtils
import io.micronaut.inject.writer.OriginatingElements
import io.micronaut.sourcegen.model.ObjectDef
import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.CompileUnit
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
 * An AST transformation that produces metadata for use by the injection container
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@CompileStatic
// IMPORTANT NOTE: This transform runs in phase CANONICALIZATION; the bean definitions are written at the end of
// that phase, after Groovy's own local transforms and after the deferred TypeElementVisitorTransform (see visit)
@GroovyASTTransformation(phase = CompilePhase.CANONICALIZATION)
class InjectTransform implements ASTTransformation, CompilationUnitAware {

    CompilationUnit unit

    /**
     * Groovy runs the global transforms of a phase before the local ones, so the bean definitions written here would
     * miss the constructors of a record or of a {@code @TupleConstructor} class, which
     * {@code TupleConstructorASTTransformation} adds in this phase. The work is therefore deferred to a phase
     * operation registered from within CANONICALIZATION: the compiler runs it after every operation of the phase,
     * including the operation {@link TypeElementVisitorTransform} registered from an earlier phase. It is registered
     * once per compilation unit and processes every source unit of the compilation, the ones queued later included.
     */
    @Override
    void visit(ASTNode[] nodes, SourceUnit source) {
        if (unit == null) {
            inject(source)
            return
        }
        CompileUnit ast = unit.getAST()
        if (ast.getNodeMetaData(InjectTransform) == null) {
            ast.putNodeMetaData(InjectTransform, Boolean.TRUE)
            unit.addNewPhaseOperation({ SourceUnit sourceUnit -> inject(sourceUnit) } as CompilationUnit.ISourceUnitOperation, Phases.CANONICALIZATION)
        }
    }

    private void inject(SourceUnit source) {
        ModuleNode moduleNode = source.getAST()
        List<ClassNode> classes = moduleNode.getClasses()

        def groovyVisitorContext = new GroovyVisitorContext(source, unit)
        def elementAnnotationMetadataFactory = groovyVisitorContext
                .getElementAnnotationMetadataFactory()
                .readOnly()
        for (ClassNode classNode in classes) {
            if ((classNode instanceof InnerClassNode && !Modifier.isStatic(classNode.getModifiers()))) {
                continue
            }
            try {
                def classElement = groovyVisitorContext.getElementFactory().newClassElement(classNode, elementAnnotationMetadataFactory)
                DefaultElementBeanDefinitionBuilderFactory beanDefinitionBuilderFactory = new DefaultElementBeanDefinitionBuilderFactory(groovyVisitorContext)
                List<OutputObjectDef> result = BeanDefinitionCreatorFactory.produce(classElement, beanDefinitionBuilderFactory, groovyVisitorContext);
                for (OutputObjectDef outputObjectDef : result) {
                    write(outputObjectDef, source, groovyVisitorContext)
                }
            } catch (ProcessingException ex) {
                groovyVisitorContext.fail(ex.getMessage(), (ex.getOriginatingElement() as GroovyNativeElement).annotatedNode())
            }
        }

        groovyVisitorContext.finish()
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
        } catch (Throwable e) {
            def element = outputObjectDef.originatingElements().originatingElements[0]
            ASTNode type
            if (element.nativeType instanceof ASTNode astNode) {
               type = astNode
            } else if (element.nativeType instanceof GroovyNativeElement groovyNativeElement) {
               type = groovyNativeElement.annotatedNode()
            } else {
                type = null
            }
            AstMessageUtils.error(source, type, "Error generating bean definition class for dependency injection of class [${element.name}]: $e.message")
            e.printStackTrace(System.err)
        }
    }

    @Override
    void setCompilationUnit(CompilationUnit unit) {
        this.unit = unit
    }

}
