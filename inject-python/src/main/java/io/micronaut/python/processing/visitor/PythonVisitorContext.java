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
package io.micronaut.python.processing.visitor;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import io.micronaut.annotation.processing.visitor.JavaVisitorContext;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.value.MutableConvertibleValues;
import io.micronaut.core.convert.value.MutableConvertibleValuesMap;
import io.micronaut.expressions.context.ExpressionCompilationContextFactory;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.Element;
import io.micronaut.inject.ast.ElementFactory;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.inject.writer.GeneratedFile;
import io.micronaut.python.processing.PythonProcessingEnvironment;
import io.micronaut.python.processing.annotation.PythonAnnotationMetadataBuilder;
import io.micronaut.python.processing.annotation.PythonElementAnnotationMetadataFactory;

public class PythonVisitorContext implements VisitorContext {
    private final MutableConvertibleValues<Object> visitorAttributes = new MutableConvertibleValuesMap<>();
    private final Map<String, DecoratorDef> decorators;
    private final PythonProcessingEnvironment processingEnvironment;
    private final JavaVisitorContext javaVisitorContext;

    public PythonVisitorContext(Map<String, DecoratorDef> decorators, PythonProcessingEnvironment processingEnvironment) {
        this(decorators, processingEnvironment, null);
    }

    public PythonVisitorContext(Map<String, DecoratorDef> decorators, PythonProcessingEnvironment processingEnvironment, JavaVisitorContext javaVisitorContext) {
        this.decorators = decorators;
        this.processingEnvironment = processingEnvironment;
        this.javaVisitorContext = javaVisitorContext;
    }

    public JavaVisitorContext getJavaVisitorContext() {
        return javaVisitorContext;
    }

    public PythonProcessingEnvironment getProcessingEnvironment() {
        return processingEnvironment;
    }

    @Override
    public Language getLanguage() {
        return Language.PYTHON;
    }

    @Override
    public ElementFactory<?, ?, ?, ?> getElementFactory() {
        return new PythonElementFactory(processingEnvironment);
    }

    @Override
    public PythonElementAnnotationMetadataFactory getElementAnnotationMetadataFactory() {
        return new PythonElementAnnotationMetadataFactory(
            false,
            getAnnotationMetadataBuilder()
        );
    }

    @Override
    public ExpressionCompilationContextFactory getExpressionCompilationContextFactory() {
        throw new UnsupportedOperationException("Expressions not yet supported");
    }

    @Override
    public PythonAnnotationMetadataBuilder getAnnotationMetadataBuilder() {
        return new PythonAnnotationMetadataBuilder(decorators, this);
    }

    @Override
    public void info(String message, Element element) {
        System.out.println("INFO: " + message + " @ " + element);
    }

    @Override
    public void info(String message) {
        System.out.println("INFO: " + message);
    }

    @Override
    public void fail(String message, Element element) {
        System.err.println("ERROR: " + message + " @ " + element);
    }

    @Override
    public void warn(String message, Element element) {
        System.out.println("WARN: " + message);
    }

    @Override
    public Optional<GeneratedFile> visitMetaInfFile(String path, Element... originatingElements) {
        if (javaVisitorContext != null) {
            return javaVisitorContext.visitMetaInfFile(path, originatingElements);
        }
        return Optional.empty();
    }

    @Override
    public Optional<GeneratedFile> visitGeneratedFile(String path) {
        if (javaVisitorContext != null) {
            return javaVisitorContext.visitGeneratedFile(path);
        }
        return Optional.empty();
    }

    @Override
    public MutableConvertibleValues<Object> put(CharSequence key, Object value) {
        return visitorAttributes.put(key, value);
    }

    @Override
    public MutableConvertibleValues<Object> remove(CharSequence key) {
        return visitorAttributes.remove(key);
    }

    @Override
    public MutableConvertibleValues<Object> clear() {
        return visitorAttributes.clear();
    }

    @Override
    public Set<String> names() {
        return visitorAttributes.names();
    }

    @Override
    public Collection<Object> values() {
        return visitorAttributes.values();
    }

    @Override
    public <T> Optional<T> get(CharSequence name, ArgumentConversionContext<T> conversionContext) {
        return visitorAttributes.get(name, conversionContext);
    }

    @Override
    public OutputStream visitClass(String classname, Element... originatingElements) throws IOException {
        if (javaVisitorContext != null) {
            return javaVisitorContext.visitClass(classname, originatingElements);
        } else {
            throw new IllegalStateException("Java Visitor Context is null");
        }
    }

    @Override
    public void visitServiceDescriptor(String type, String classname) {
        if (javaVisitorContext != null) {
            javaVisitorContext.visitServiceDescriptor(type, classname);
        }
    }

    @Override
    public void visitServiceDescriptor(String type, String classname, Element originatingElement) {
        if (javaVisitorContext != null) {
            javaVisitorContext.visitServiceDescriptor(type, classname, originatingElement);
        }
    }

    @Override
    public Optional<GeneratedFile> visitGeneratedFile(String path, Element... originatingElements) {
        if (javaVisitorContext != null) {
            return javaVisitorContext.visitGeneratedFile(path, originatingElements);
        }
        return Optional.empty();
    }

    @Override
    public Optional<GeneratedFile> visitGeneratedSourceFile(String packageName, String fileNameWithoutExtension, Element... originatingElements) {
        if (javaVisitorContext != null) {
            return javaVisitorContext.visitGeneratedSourceFile(packageName, fileNameWithoutExtension, originatingElements);
        }
        return Optional.empty();
    }

    @Override
    public Optional<io.micronaut.inject.ast.ClassElement> getClassElement(String name) {
        // First try to find in Python environment
        if (!name.startsWith("java.") && !name.startsWith("javax.")) {
            Map<String, ClassElement> classes = processingEnvironment.classes();
            io.micronaut.inject.ast.ClassElement pythonClass = classes.get(name);
            String defaultPackage = PythonClassElement.PYTHON_DEFAULT_PACKAGE + '.';
            if (pythonClass == null && name.startsWith(defaultPackage)) {
                // try default package
                pythonClass = classes.get(name.substring(defaultPackage.length()));
            }
            if (pythonClass != null) {
                return Optional.of(pythonClass);
            }
        }
        // Fallback to Java visitor context
        if (javaVisitorContext != null) {
            return javaVisitorContext.getClassElement(name);
        }
        return Optional.empty();
    }

    @Override
    public io.micronaut.inject.ast.ClassElement[] getClassElements(String aPackage, String... stereotypes) {
        var pythonClasses = new java.util.ArrayList<io.micronaut.inject.ast.ClassElement>();
        // Add Python classes from the environment
        for (var entry : processingEnvironment.classes().entrySet()) {
            var classElement = entry.getValue();
            if (classElement.getPackageName().equals(aPackage)) {
                pythonClasses.add(classElement);
            }
        }
        // Add Java classes if visitor context is available
        if (javaVisitorContext != null) {
            var javaClasses = java.util.Arrays.asList(javaVisitorContext.getClassElements(aPackage, stereotypes));
            pythonClasses.addAll(javaClasses);
        }
        return pythonClasses.toArray(new io.micronaut.inject.ast.ClassElement[0]);
    }

    @Override
    public void finish() {
        if (javaVisitorContext != null) {
            javaVisitorContext.finish();
        }
    }
}
