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

import io.micronaut.core.annotation.Experimental;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
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
import io.micronaut.inject.ast.annotation.ElementAnnotationMetadataFactory;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.inject.writer.GeneratedFile;
import io.micronaut.python.processing.PythonProcessingEnvironment;
import io.micronaut.python.processing.annotation.PythonAnnotationMetadataBuilder;
import io.micronaut.python.processing.annotation.PythonElementAnnotationMetadataFactory;

/**
 * Visitor context implementation backed by the Python processing environment.
 */
@Experimental
public final class PythonVisitorContext implements VisitorContext {

    private static final String INFO_PREFIX = "INFO: ";
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
        if (javaVisitorContext != null) {
            return javaVisitorContext.getExpressionCompilationContextFactory();
        }
        throw new UnsupportedOperationException("Expressions require a Java visitor context");
    }

    @Override
    public PythonAnnotationMetadataBuilder getAnnotationMetadataBuilder() {
        return new PythonAnnotationMetadataBuilder(decorators, this);
    }

    @Override
    public void info(String message, Element element) {
        if (element != null) {
            System.out.println(INFO_PREFIX + message + " @ " + element);
        } else {
            System.out.println(INFO_PREFIX + message);
        }
    }

    @Override
    public void info(String message) {
        System.out.println(INFO_PREFIX + message);
    }

    @Override
    public void fail(String message, Element element) {
        if (javaVisitorContext != null) {
            javaVisitorContext.fail(message, element);
            return;
        }
        if (element != null) {
            System.err.println("ERROR: " + message + " @ " + element);
        } else {
            System.err.println("ERROR: " + message);
        }
    }

    @Override
    public void warn(String message, Element element) {
        if (element != null) {
            System.out.println("WARN: " + message + " @ " + element);
        } else {
            System.out.println("WARN: " + message);
        }
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
    public Optional<ClassElement> getClassElement(String name, ElementAnnotationMetadataFactory annotationMetadataFactory) {
        return getClassElement(name, true);
    }

    @Override
    public Optional<io.micronaut.inject.ast.ClassElement> getClassElement(String name) {
        return getClassElement(name, false);
    }

    private Optional<io.micronaut.inject.ast.ClassElement> getClassElement(String name, boolean useAnnotationMetadataFactory) {
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
            Map<String, ClassElement> scripts = processingEnvironment.scriptsIfInitialized();
            if (scripts != null) {
                io.micronaut.inject.ast.ClassElement pythonScript = scripts.get(name);
                if (pythonScript == null && name.startsWith(defaultPackage)) {
                    pythonScript = scripts.get(name.substring(defaultPackage.length()));
                }
                if (pythonScript != null) {
                    return Optional.of(pythonScript);
                }
            }
        }
        // Fallback to Java visitor context
        if (javaVisitorContext != null) {
            Optional<ClassElement> javaClass = useAnnotationMetadataFactory
                ? javaVisitorContext.getClassElement(name, javaVisitorContext.getElementAnnotationMetadataFactory().readOnly())
                : javaVisitorContext.getClassElement(name);
            if (javaClass.isPresent()) {
                return javaClass;
            }
        }
        return getDecoratorClassElement(name);
    }

    private Optional<ClassElement> getDecoratorClassElement(String name) {
        DecoratorDef decoratorDef = decorators.get(name);
        String defaultPackage = PythonClassElement.PYTHON_DEFAULT_PACKAGE + '.';
        if (decoratorDef == null && name.startsWith(defaultPackage)) {
            decoratorDef = decorators.get(name.substring(defaultPackage.length()));
        }
        if (decoratorDef == null) {
            for (DecoratorDef candidate : decorators.values()) {
                if (candidate.annotationName().equals(name)
                    || candidate.name().equals(name)
                    || (name.startsWith(defaultPackage) && candidate.name().equals(name.substring(defaultPackage.length())))) {
                    decoratorDef = candidate;
                    break;
                }
            }
        }
        return decoratorDef == null ? Optional.empty() : Optional.of(toDecoratorClassElement(decoratorDef));
    }

    private ClassElement toDecoratorClassElement(DecoratorDef decoratorDef) {
        String annotationName = decoratorDef.annotationName();
        int packageIndex = annotationName.lastIndexOf('.');
        String packageName = packageIndex > -1 ? annotationName.substring(0, packageIndex) : "";
        String simpleName = packageIndex > -1 ? annotationName.substring(packageIndex + 1) : annotationName;

        Set<String> memberNames = new LinkedHashSet<>();
        memberNames.addAll(decoratorDef.memberTypes().keySet());
        memberNames.addAll(decoratorDef.members().keySet());
        memberNames.addAll(decoratorDef.memberDecorators().keySet());

        List<FunctionDef> functions = new ArrayList<>(memberNames.size());
        for (String memberName : memberNames) {
            TypeRef memberType = decoratorDef.memberTypes().get(memberName);
            ReturnDef returnType = ReturnDef.of(memberType == null ? new TypeRef("str") : memberType);
            functions.add(new FunctionDef(
                memberName,
                ArgumentsDef.empty(),
                decoratorDef.memberDecorators().getOrDefault(memberName, List.of()),
                returnType
            ));
        }

        ClassDef annotationDef = new ClassDef(
            simpleName,
            packageName,
            List.of(),
            decoratorDef.stereotypes(),
            List.of(),
            functions,
            List.of(),
            List.of(),
            null,
            false,
            List.of(),
            null
        );
        return new PythonClassElement(annotationDef, processingEnvironment);
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
        Map<String, ClassElement> scripts = processingEnvironment.scriptsIfInitialized();
        if (scripts != null) {
            for (var entry : scripts.entrySet()) {
                var classElement = entry.getValue();
                if (classElement.getPackageName().equals(aPackage)) {
                    pythonClasses.add(classElement);
                }
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

    @SuppressWarnings("removal")
    @Override
    public void addGeneratedResource(String resource) {
        if (javaVisitorContext != null) {
            javaVisitorContext.addGeneratedResource(resource);
        }
    }

    @Override
    @SuppressWarnings("removal")
    public Collection<String> getGeneratedResources() {
        if (javaVisitorContext != null) {
            return javaVisitorContext.getGeneratedResources();
        }
        return Collections.emptyList();
    }
}
