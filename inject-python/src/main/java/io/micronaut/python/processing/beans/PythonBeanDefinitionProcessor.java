/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.python.processing.beans;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.Internal;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Set;
import java.util.function.Predicate;

import io.micronaut.core.annotation.Generated;
import io.micronaut.core.annotation.Vetoed;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.processing.BeanDefinitionCreatorFactory;
import io.micronaut.inject.processing.ProcessingException;
import io.micronaut.inject.processing.definition.DefaultElementBeanDefinitionBuilderFactory;
import io.micronaut.inject.processing.definition.OutputObjectDef;
import io.micronaut.inject.writer.ByteCodeWriterUtils;
import io.micronaut.inject.writer.OriginatingElements;
import io.micronaut.python.processing.PythonProcessingEnvironment;
import io.micronaut.python.processing.metadata.PythonMetadataBackend;
import io.micronaut.python.processing.metadata.PythonMetadataModelBuilder;
import io.micronaut.python.processing.metadata.PythonMetadataOutputs;
import io.micronaut.python.processing.visitor.PythonVisitorContext;
import io.micronaut.sourcegen.model.ObjectDef;
import org.jspecify.annotations.Nullable;

/**
 * Processor for creating bean definitions from Python classes.
 *
 * @author Micronaut Team
 * @since 5.2.0
 */
@Experimental
public final class PythonBeanDefinitionProcessor {

    private final Set<String> processed = new java.util.HashSet<>();
    private @Nullable PythonMetadataOutputs metadataOutputs;

    public void processBeanDefinitions(
        PythonProcessingEnvironment processingEnvironment
    ) {
        processBeanDefinitions(processingEnvironment, ignored -> true, false);
    }

    /**
     * Processes bean definitions for selected source elements.
     *
     * @param processingEnvironment The processing environment
     * @param sourceFilter The source element filter
     * @param incremental Whether only the classes affected by the changed sources are processed
     */
    @Internal
    public void processBeanDefinitions(
        PythonProcessingEnvironment processingEnvironment,
        Predicate<ClassElement> sourceFilter,
        boolean incremental
    ) {
        PythonVisitorContext visitorContext = processingEnvironment.visitorContext();
        PythonMetadataBackend backend = PythonMetadataBackend.of(visitorContext);
        metadataOutputs = backend.isModel() ? new PythonMetadataOutputs(backend, incremental) : null;
        for (ClassElement classElement : processingEnvironment.classes().values().stream().filter(sourceFilter).toList()) {
            processClassElement(classElement, visitorContext);
        }
        for (ClassElement classElement : processingEnvironment.scripts().values().stream().filter(sourceFilter).toList()) {
            processClassElement(classElement, visitorContext);
        }
        if (metadataOutputs != null) {
            metadataOutputs.finish(visitorContext);
        }
    }

    private void processClassElement(ClassElement classElement, PythonVisitorContext visitorContext) {
        try {
            // Skip generated classes and vetoed classes
            if (isGenerated(classElement) || isVetoed(classElement)) {
                return;
            }
            if (metadataOutputs != null && PythonMetadataBackend.isSelected(classElement, visitorContext)) {
                // The model backends: the same analysis, recorded after every visitor ran, instead of emitted
                PythonMetadataModelBuilder modelBuilder = new PythonMetadataModelBuilder(visitorContext);
                modelBuilder.build(classElement).ifPresent(model -> metadataOutputs.write(classElement, model, visitorContext));
                if (!modelBuilder.writesDefinitionsWithCompiler()) {
                    return;
                }
                // The class needs interception: the compiler writes its definitions and the proxy they need
            }

            DefaultElementBeanDefinitionBuilderFactory beanDefinitionBuilderFactory = new DefaultElementBeanDefinitionBuilderFactory(visitorContext);
            for (OutputObjectDef outputObjectDef : BeanDefinitionCreatorFactory.produce(classElement, beanDefinitionBuilderFactory, visitorContext)) {
                if (processed.add(outputObjectDef.objectDef().getName())) {
                    processBeanDefinition(outputObjectDef, visitorContext);
                }
            }
        } catch (ProcessingException e) {
            handleProcessingException(visitorContext, e);
        }
    }

    private boolean isGenerated(ClassElement classElement) {
        return classElement.hasAnnotation(Generated.class);
    }

    private boolean isVetoed(ClassElement classElement) {
        return classElement.hasAnnotation(Vetoed.class);
    }

    private void processBeanDefinition(
        OutputObjectDef outputObjectDef,
        PythonVisitorContext outputVisitor
    ) {
        try {
            ObjectDef objectDef = outputObjectDef.objectDef();
            Class<?> serviceClass = outputObjectDef.serviceClass();
            OriginatingElements originatingElements = outputObjectDef.originatingElements();
            if (serviceClass != null) {
                outputVisitor.visitServiceDescriptor(serviceClass, objectDef.getName(), originatingElements.getOriginatingElements()[0]);
            }
            try (OutputStream outputStream = outputVisitor.visitClass(objectDef.getName(), originatingElements.getOriginatingElements())) {
                outputStream.write(ByteCodeWriterUtils.writeByteCode(objectDef, outputVisitor));
            }
        } catch (IOException e) {
            String message = e.getMessage();
            outputVisitor.fail("Failed to write bean definition [" + outputObjectDef.objectDef().getName() + "]: "
                + (message != null ? message : e.getClass().getSimpleName()), null);
        }
    }

    private void handleProcessingException(PythonVisitorContext visitorContext, ProcessingException e) {
        String message = e.getMessage();
        if (message != null) {
            Object originatingElement = e.getOriginatingElement();
            if (originatingElement instanceof io.micronaut.inject.ast.Element element) {
                visitorContext.fail(message, element);
            } else {
                visitorContext.fail(message, null);
            }
        } else {
            visitorContext.fail("Unknown error processing element", null);
        }
    }
}
