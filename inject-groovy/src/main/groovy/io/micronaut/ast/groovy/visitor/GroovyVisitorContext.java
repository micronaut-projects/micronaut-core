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
package io.micronaut.ast.groovy.visitor;

import groovy.lang.GroovyClassLoader;
import io.micronaut.ast.groovy.annotation.GroovyAnnotationMetadataBuilder;
import io.micronaut.ast.groovy.annotation.GroovyElementAnnotationMetadataFactory;
import io.micronaut.ast.groovy.scan.ClassPathAnnotationScanner;
import io.micronaut.ast.groovy.utils.AstMessageUtils;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.value.MutableConvertibleValues;
import io.micronaut.core.convert.value.MutableConvertibleValuesMap;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.inject.annotation.AbstractAnnotationMetadataBuilder;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.Element;
import io.micronaut.inject.ast.ElementAnnotationMetadataFactory;
import io.micronaut.inject.util.VisitorContextUtils;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.inject.writer.AbstractBeanDefinitionBuilder;
import io.micronaut.inject.writer.ClassWriterOutputVisitor;
import io.micronaut.inject.writer.GeneratedFile;
import org.codehaus.groovy.ast.ASTNode;
import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.control.CompilationUnit;
import org.codehaus.groovy.control.Janitor;
import org.codehaus.groovy.control.SourceUnit;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The visitor context when visiting Groovy code.
 *
 * @author James Kleeh
 * @author Graeme Rocher
 * @since 1.0
 */
public class GroovyVisitorContext implements VisitorContext {
    private static final MutableConvertibleValues<Object> VISITOR_ATTRIBUTES = new MutableConvertibleValuesMap<>();
    private final CompilationUnit compilationUnit;
    private final ClassWriterOutputVisitor outputVisitor;
    private final SourceUnit sourceUnit;
    private final MutableConvertibleValues<Object> attributes;
    private final List<String> generatedResources = new ArrayList<>();
    private final GroovyElementFactory groovyElementFactory;
    private final List<AbstractBeanDefinitionBuilder> beanDefinitionBuilders = new ArrayList<>();
    private final GroovyElementAnnotationMetadataFactory elementAnnotationMetadataFactory;

    /**
     * @param sourceUnit      The source unit
     * @param compilationUnit The compilation unit
     */
    public GroovyVisitorContext(SourceUnit sourceUnit, @Nullable CompilationUnit compilationUnit) {
        this(sourceUnit, compilationUnit, new GroovyClassWriterOutputVisitor(compilationUnit));
    }

    /**
     * @param sourceUnit      The source unit
     * @param compilationUnit The compilation unit
     * @param outputVisitor   The class writer output visitor
     */
    public GroovyVisitorContext(SourceUnit sourceUnit, @Nullable CompilationUnit compilationUnit, ClassWriterOutputVisitor outputVisitor) {
        this.sourceUnit = sourceUnit;
        this.compilationUnit = compilationUnit;
        this.outputVisitor = outputVisitor;
        this.attributes = VISITOR_ATTRIBUTES;
        this.groovyElementFactory = new GroovyElementFactory(this);
        this.elementAnnotationMetadataFactory = new GroovyElementAnnotationMetadataFactory(false, new GroovyAnnotationMetadataBuilder(sourceUnit, compilationUnit));
    }

    @NonNull
    @Override
    public Iterable<URL> getClasspathResources(@NonNull String path) {
        try {
            final Enumeration<URL> resources = compilationUnit.getClassLoader().getResources(path);
            return CollectionUtils.enumerationToIterable(resources);
        } catch (IOException e) {
            return Collections.emptyList();
        }
    }

    @Override
    public Optional<ClassElement> getClassElement(String name) {
        return getClassElement(name, getElementAnnotationMetadataFactory());
    }

    @Override
    public Optional<ClassElement> getClassElement(String name, ElementAnnotationMetadataFactory annotationMetadataFactory) {
        if (name == null || compilationUnit == null) {
            return Optional.empty();
        }
        ClassNode classNode = Optional.ofNullable(compilationUnit.getClassNode(name))
            .orElseGet(() -> {
                if (sourceUnit != null) {
                    GroovyClassLoader classLoader = sourceUnit.getClassLoader();
                    if (classLoader != null) {
                        return ClassUtils.forName(name, classLoader).map(ClassHelper::make).orElse(null);
                    }
                }
                return null;
            });

        return Optional.ofNullable(classNode).map(cn -> groovyElementFactory.newClassElement(cn, annotationMetadataFactory));
    }

    @Override
    public Optional<ClassElement> getClassElement(Class<?> type) {
        final ClassNode classNode = ClassHelper.makeCached(type);
        return Optional.of(groovyElementFactory.newClassElement(classNode, getElementAnnotationMetadataFactory()));
    }

    @NonNull
    @Override
    public ClassElement[] getClassElements(@NonNull String aPackage, @NonNull String... stereotypes) {
        ArgumentUtils.requireNonNull("aPackage", aPackage);
        ArgumentUtils.requireNonNull("stereotypes", stereotypes);

        if (compilationUnit == null) {
            return new ClassElement[0];
        }

        ClassPathAnnotationScanner scanner = new ClassPathAnnotationScanner(compilationUnit.getClassLoader());
        List<ClassElement> classElements = new ArrayList<>();
        for (String s : stereotypes) {
            scanner.scan(s, aPackage).forEach(aClass -> {
                final ClassNode classNode = ClassHelper.make(aClass);
                classElements.add(groovyElementFactory.newClassElement(classNode, getElementAnnotationMetadataFactory()));
            });
        }
        return classElements.toArray(new ClassElement[0]);
    }

    @NonNull
    @Override
    public GroovyElementFactory getElementFactory() {
        return groovyElementFactory;
    }

    @NonNull
    @Override
    public GroovyElementAnnotationMetadataFactory getElementAnnotationMetadataFactory() {
        return elementAnnotationMetadataFactory;
    }

    @Override
    public AbstractAnnotationMetadataBuilder getAnnotationMetadataBuilder() {
        return new GroovyAnnotationMetadataBuilder(sourceUnit, compilationUnit);
    }

    @Override
    public void info(String message, @Nullable Element element) {
        StringBuilder msg = new StringBuilder("Note: ").append(message);
        if (element != null) {
            ASTNode expr = (ASTNode) element.getNativeType();
            final String sample = sourceUnit.getSample(expr.getLineNumber(), expr.getColumnNumber(), new Janitor());
            msg.append("\n\n").append(sample);
        }
        System.out.println(msg);
    }

    @Override
    public void info(String message) {
        System.out.println("Note: " + message);
    }

    @Override
    public void fail(String message, @Nullable Element element) {
        if (element instanceof AbstractGroovyElement) {
            AstMessageUtils.error(sourceUnit, ((AbstractGroovyElement) element).getNativeType(), message);
        } else {
            AstMessageUtils.error(sourceUnit, null, message);
        }
    }

    public final void fail(String message, ASTNode expr) {
        AstMessageUtils.error(sourceUnit, expr, message);
    }

    @Override
    public void warn(String message, @Nullable Element element) {
        if (element instanceof AbstractGroovyElement) {
            AstMessageUtils.warning(sourceUnit, ((AbstractGroovyElement) element).getNativeType(), message);
        } else {
            AstMessageUtils.warning(sourceUnit, null, message);
        }
    }

    @Override
    public OutputStream visitClass(String classname, @Nullable Element originatingElement) throws IOException {
        return outputVisitor.visitClass(classname, originatingElement);
    }

    @Override
    public OutputStream visitClass(String classname, Element... originatingElements) throws IOException {
        return outputVisitor.visitClass(classname, originatingElements);
    }

    @Override
    public void visitServiceDescriptor(String type, String classname) {
        outputVisitor.visitServiceDescriptor(type, classname);
    }

    @Override
    public void visitServiceDescriptor(String type, String classname, Element originatingElement) {
        outputVisitor.visitServiceDescriptor(type, classname, originatingElement);
    }

    @Override
    public Optional<GeneratedFile> visitMetaInfFile(String path, Element... originatingElements) {
        return outputVisitor.visitMetaInfFile(path, originatingElements);
    }

    @Override
    public Optional<GeneratedFile> visitGeneratedFile(String path) {
        return outputVisitor.visitGeneratedFile(path);
    }

    @Override
    public void finish() {
        outputVisitor.finish();
    }

    /**
     * @return The source unit
     */
    SourceUnit getSourceUnit() {
        return sourceUnit;
    }

    /**
     * @return The compilation unit
     */
    @Internal
    public CompilationUnit getCompilationUnit() {
        return compilationUnit;
    }

    /**
     * Groovy options source are {@link System#getProperties()} based.
     * <p><b>All properties MUST start with {@link GroovyVisitorContext#MICRONAUT_BASE_OPTION_NAME}</b></p>
     * @return options {@link Map}
     */
    @Override
    public Map<String, String> getOptions() {
        return VisitorContextUtils.getSystemOptions();
    }

    @Override
    public MutableConvertibleValues<Object> put(CharSequence key, @Nullable Object value) {
        return attributes.put(key, value);
    }

    @Override
    public MutableConvertibleValues<Object> remove(CharSequence key) {
        return attributes.remove(key);
    }

    @Override
    public MutableConvertibleValues<Object> clear() {
        return attributes.clear();
    }

    @Override
    public Set<String> names() {
        return attributes.names();
    }

    @Override
    public Collection<Object> values() {
        return attributes.values();
    }

    @Override
    public <T> Optional<T> get(CharSequence name, ArgumentConversionContext<T> conversionContext) {
        return attributes.get(name, conversionContext);
    }

    @Override
    public Collection<String> getGeneratedResources() {
        return Collections.unmodifiableCollection(generatedResources);
    }

    @Override
    public void addGeneratedResource(@NonNull String resource) {
        generatedResources.add(resource);
    }

    /**
     * @return Gets the produced bean definition builders.
     */
    @Internal
    public List<AbstractBeanDefinitionBuilder> getBeanElementBuilders() {
        final ArrayList<AbstractBeanDefinitionBuilder> current = new ArrayList<>(beanDefinitionBuilders);
        beanDefinitionBuilders.clear();
        return current;
    }

    /**
     * Adds a java bean definition builder.
     * @param groovyBeanDefinitionBuilder The groovy bean definition builder
     */
    @Internal
    void addBeanDefinitionBuilder(GroovyBeanDefinitionBuilder groovyBeanDefinitionBuilder) {
        this.beanDefinitionBuilders.add(groovyBeanDefinitionBuilder);
    }
}
