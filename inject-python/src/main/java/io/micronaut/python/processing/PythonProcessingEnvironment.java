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
package io.micronaut.python.processing;

import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import io.micronaut.annotation.processing.visitor.JavaVisitorContext;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.python.processing.annotation.PythonAnnotationMetadataBuilder;
import io.micronaut.python.processing.annotation.PythonElementAnnotationMetadataFactory;
import io.micronaut.python.processing.visitor.ClassDef;
import io.micronaut.python.processing.visitor.PythonClassElement;
import io.micronaut.python.processing.visitor.PythonEnumElement;
import io.micronaut.python.processing.visitor.PythonVisitorContext;

import javax.lang.model.element.Element;

/**
 * Represents a Python processing environment that extends the basic Python environment with additional
 * processing capabilities for annotation metadata and visitor context. This record is used during
 * Micronaut's annotation processing phase to handle Python code integration.
 *
 * @param environment The underlying Python environment containing classes and decorators.
 * @param annotationMetadataBuilder The builder for creating annotation metadata from Python elements.
 * @param metadataFactory The factory for creating element annotation metadata.
 * @param visitorContext The visitor context for processing Python elements.
 * @since 4.8.0
 * @author Micronaut
 */
public record PythonProcessingEnvironment(
    PythonEnvironment environment,
    PythonAnnotationMetadataBuilder annotationMetadataBuilder,
    PythonElementAnnotationMetadataFactory metadataFactory,
    JavaVisitorContext javaVisitorContext,
    PythonVisitorContext visitorContext,
    Element originatingElement
) implements AutoCloseable {

    /**
     * Creates a Python processing environment with the specified Python environment.
     * The annotation metadata builder, metadata factory, and visitor context will be initialized automatically.
     *
     * @param environment The Python environment to use.
     * @param javaVisitorContext The java visitor context
     * @param originatingElement  The originating element
     */
    public PythonProcessingEnvironment(PythonEnvironment environment, JavaVisitorContext javaVisitorContext, Element originatingElement) {
        this(
            environment,
            null,
            null,
            javaVisitorContext,
            null,
            originatingElement
        );
    }

    /**
     * Creates a Python processing environment with the specified Python environment.
     * The annotation metadata builder, metadata factory, and visitor context will be initialized automatically.
     *
     * @param environment The Python environment to use.
     * @param javaVisitorContext The java visitor context
     */
    public PythonProcessingEnvironment(PythonEnvironment environment, JavaVisitorContext javaVisitorContext) {
        this(
            environment,
            null,
            null,
            javaVisitorContext,
            null,
            null
        );
    }


    /**
     * Creates a Python processing environment with the specified Python environment.
     * The annotation metadata builder, metadata factory, and visitor context will be initialized automatically.
     *
     * @param environment The Python environment to use.
     */
    public PythonProcessingEnvironment(PythonEnvironment environment) {
        this(
            environment,
            null,
            null,
            null,
            null,
            null
        );
    }

    public PythonProcessingEnvironment {
        Objects.requireNonNull(environment, "Python environment cannot be null");

        if (visitorContext == null) {
            visitorContext = new PythonVisitorContext(environment.decorators(), this, javaVisitorContext);
        }

        if (annotationMetadataBuilder == null) {
            annotationMetadataBuilder = visitorContext.getAnnotationMetadataBuilder();
        }
        if (metadataFactory == null) {
            metadataFactory = visitorContext.getElementAnnotationMetadataFactory();
        }
    }

    /**
     * Closes the Python processing environment by closing the underlying Python environment.
     */
    @Override
    public void close() {
        environment.close();
    }

    /**
     * Returns a map of Python class elements, converting the raw class definitions into
     * processing-ready elements with annotation metadata support.
     *
     * @return A map of class names to Python class elements.
     */
    public Map<String, ClassElement> classes() {
        return toMapOfClassElement(environment.classes(), this);
    }

    /**
     * Returns a map of Python script elements, converting the raw script definitions into
     * processing-ready elements with annotation metadata support.
     *
     * @return A map of script names to Python script elements.
     */
    public Map<String, ClassElement> scripts() {
        return toMapOfScriptElement(environment.scripts(), this);
    }

    private static Map<String, ClassElement> toMapOfScriptElement(java.util.Map<String, io.micronaut.python.processing.visitor.ScriptDef> scripts, PythonProcessingEnvironment environment) {
        return scripts.entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                entry -> new io.micronaut.python.processing.visitor.PythonScriptElement(entry.getValue(), environment)
            ));
    }

    private static Map<String, ClassElement> toMapOfClassElement(Map<String, ClassDef> classes, PythonProcessingEnvironment environment) {
        return classes.entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                entry -> {
                    ClassDef classDef = entry.getValue();
                    if (classDef.isEnum()) {
                        return new PythonEnumElement(classDef, environment);
                    } else {
                        return new PythonClassElement(classDef, environment);
                    }
                }
            ));
    }
}
