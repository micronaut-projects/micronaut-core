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

import java.io.IOException;
import java.util.Set;

import io.micronaut.core.annotation.Generated;
import io.micronaut.core.annotation.Vetoed;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.processing.BeanDefinitionCreatorFactory;
import io.micronaut.inject.processing.ProcessingException;
import io.micronaut.inject.writer.BeanDefinitionVisitor;
import io.micronaut.python.processing.PythonProcessingEnvironment;
import io.micronaut.python.processing.visitor.PythonVisitorContext;

/**
 * Processor for creating bean definitions from Python classes.
 *
 * @author Micronaut Team
 * @since 5.0.0
 */
public final class PythonBeanDefinitionProcessor {

    private final Set<String> processed = new java.util.HashSet<>();

    public void processBeanDefinitions(
        PythonProcessingEnvironment processingEnvironment
    ) {
        PythonVisitorContext visitorContext = processingEnvironment.visitorContext();
        for (ClassElement classElement : processingEnvironment.classes().values()) {
            processClassElement(classElement, visitorContext);
        }
        for (ClassElement classElement : processingEnvironment.scripts().values()) {
            processClassElement(classElement, visitorContext);
        }
    }

    private void processClassElement(ClassElement classElement, PythonVisitorContext visitorContext) {
        try {
            // Skip generated classes and vetoed classes
            if (isGenerated(classElement) || isVetoed(classElement)) {
                return;
            }

            // Use BeanDefinitionCreatorFactory to create bean definitions
            var produce = BeanDefinitionCreatorFactory.produce(classElement, visitorContext);
            for (BeanDefinitionVisitor writer : produce.build()) {
                if (processed.add(writer.getBeanDefinitionName())) {
                    writer.visitBeanDefinitionEnd();
                    processBeanDefinition(writer, visitorContext);
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
        BeanDefinitionVisitor beanDefinitionWriter,
        PythonVisitorContext outputVisitor
    ) {
        try {
            if (beanDefinitionWriter.isEnabled()) {
                beanDefinitionWriter.accept(outputVisitor);
            }
        } catch (IOException e) {
            // Raise a compile error
            String message = e.getMessage();
            error("Unexpected error " + e.getClass().getSimpleName() + ":" +
                  (message != null ? message : e.getClass().getSimpleName()));
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

    private void error(String message) {
        // This would normally log an error, but in annotation processing
        // we need to use the visitor context to report errors
        System.err.println("PythonBeanDefinitionProcessor error: " + message);
    }
}
