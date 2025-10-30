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

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;

import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;

import io.micronaut.annotation.processing.AbstractInjectAnnotationProcessor;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.python.processing.annotation.PythonApplication;
import io.micronaut.python.processing.beans.PythonBeanDefinitionProcessor;
import io.micronaut.python.processing.visitor.PythonTypeElementVisitorProcessor;
import org.jetbrains.annotations.Nullable;

/**
 * Annotation processor for {@link PythonApplication} that enables Python AST processing
 * during Java compilation.
 *
 * @author Micronaut
 * @since 4.8.0
 */
@SupportedAnnotationTypes("io.micronaut.python.processing.annotation.PythonApplication")
public class PythonAnnotationProcessor extends AbstractInjectAnnotationProcessor {

    private PythonAstParser parser;
    private Consumer<ClassElement> classElementCallback;

    /**
     * Set the callback to be invoked for each class element created during processing.
     * This is primarily used for testing purposes.
     *
     * @param callback The callback function
     */
    public void setClassElementCallback(Consumer<ClassElement> callback) {
        this.classElementCallback = callback;
    }

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        parser = new PythonAstParser();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (annotations.isEmpty()) {
            return false;
        }

        for (TypeElement annotation : annotations) {
            if (PythonApplication.class.getName().equals(annotation.getQualifiedName().toString())) {
                processPythonApplications(roundEnv);
            }
        }
        if (roundEnv.processingOver()) {
            parser.close();
        }
        return false;
    }

    private void processPythonApplications(RoundEnvironment roundEnv) {
        Set<? extends Element> elements = roundEnv.getElementsAnnotatedWith(PythonApplication.class);

        for (Element element : elements) {
            PythonApplication annotation = element.getAnnotation(PythonApplication.class);
            if (annotation != null) {
                processAnnotation(element, annotation);
            }
        }
    }

    private void processAnnotation(Element element, PythonApplication annotation) {
        try {
            PythonEnvironment environment;
            String transformedCode = applyASTTransforms(annotation);
            if (transformedCode == null) {
                return;
            }

            // Then parse the transformed code
            try {
                environment = parser.parse(transformedCode);
            } catch (Exception e) {
                error("Error parsing transformed python code: " + e.getMessage(), e);
                return;
            }

            // Create processing environment and visitor context
            PythonProcessingEnvironment processingEnvironment =
                new PythonProcessingEnvironment(environment, javaVisitorContext, element);

            // Run type element visitor processing
            PythonTypeElementVisitorProcessor typeElementVisitorProcessor =
                new PythonTypeElementVisitorProcessor();
            typeElementVisitorProcessor.init(processingEnvironment);
            typeElementVisitorProcessor.process(processingEnvironment);

            // Process bean definitions for Python classes
            var beanDefinitionProcessor = new PythonBeanDefinitionProcessor();
            beanDefinitionProcessor.processBeanDefinitions(processingEnvironment);

            // Invoke callback for each class element if callback is set
            if (classElementCallback != null) {
                processingEnvironment.classes().values().forEach(classElementCallback);
            }

            // Write transformed Python code to META-INF
            if (!transformedCode.isEmpty()) {
                javaVisitorContext.visitMetaInfFile("pyronaut_application.py")
                    .ifPresent(generatedFile -> {
                        try (var writer = generatedFile.openWriter()) {
                            writer.write(transformedCode);
                        } catch (IOException e) {
                            error("Failed to write transformed Python code to META-INF", e);
                        }
                    });
            }

            // The visitor context is now ready for use by Micronaut's type visitors
            note("Successfully processed Python environment with " +
                environment.classes().size() + " classes and " +
                environment.decorators().size() + " decorators");

        } catch (Exception e) {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            e.printStackTrace(pw);
            String stacktrace = sw.toString();
            error("Failed Trace: %s", stacktrace);
            error("Failed to process Python code: %s", e.getMessage());
        }
    }

    private @Nullable String applyASTTransforms(PythonApplication annotation) {
        String transformedCode;
        // Process inline code if provided
        String code = annotation.code();
        if (!code.isEmpty()) {
            // Transform the source code first
            try {
                transformedCode = parser.transform(code, javaVisitorContext);
            } catch (Exception e) {
                error("Error transforming python code: " + e.getMessage(), e);
                return null;
            }

        } else {
            // Process directory scanning if provided
            String srcDir = annotation.src();
            if (!srcDir.isEmpty()) {
                Path directory = Paths.get(srcDir);
                if (Files.isDirectory(directory)) {
                    try (Stream<Path> stream = Files.walk(directory)) {
                        Path[] files = stream
                            .filter(Files::isRegularFile)
                            .filter(path -> path.toString().endsWith(".py"))
                            .toArray(Path[]::new);
                        StringBuilder combinedSources = new StringBuilder();
                        for (Path file : files) {
                            combinedSources.append(Files.readString(file)).append("\n");
                        }
                        String sourceCode = combinedSources.toString();
                        // Transform the source code first
                        try {
                            transformedCode = parser.transform(sourceCode, javaVisitorContext);
                        } catch (Exception e) {
                            error("Error transforming python code: " + e.getMessage(), e);
                            return null;
                        }
                    } catch (IOException e) {
                        error("Failed to scan directory for Python files: " + srcDir, e);
                        return null;
                    } catch (Exception e) {
                        error("Error processing python code: " + e.getMessage(), e);
                        return null;
                    }
                } else {
                    error("Source directory does not exist: " + srcDir);
                    return null;
                }
            } else {
                note("No code or src specified in @PyronautApplication");
                return null;
            }
        }
        return transformedCode;
    }
}
