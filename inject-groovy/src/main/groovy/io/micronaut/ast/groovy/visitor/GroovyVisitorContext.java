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
import io.micronaut.ast.groovy.GroovyNativeElementHelper;
import io.micronaut.ast.groovy.annotation.GroovyAnnotationMetadataBuilder;
import io.micronaut.ast.groovy.annotation.GroovyElementAnnotationMetadataFactory;
import io.micronaut.ast.groovy.scan.ClassPathAnnotationScanner;
import io.micronaut.ast.groovy.utils.AstMessageUtils;
import io.micronaut.ast.groovy.utils.InMemoryByteCodeGroovyClassLoader;
import io.micronaut.ast.groovy.utils.InMemoryClassWriterOutputVisitor;
import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.writer.DirectoryClassWriterOutputVisitor;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.value.MutableConvertibleValues;
import io.micronaut.core.convert.value.MutableConvertibleValuesMap;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.expressions.context.DefaultExpressionCompilationContextFactory;
import io.micronaut.expressions.context.ExpressionCompilationContextFactory;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.Element;
import io.micronaut.inject.ast.annotation.ElementAnnotationMetadataFactory;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.inject.visitor.util.VisitorContextUtils;
import io.micronaut.inject.writer.AbstractBeanDefinitionBuilder;
import io.micronaut.inject.writer.ClassWriterOutputVisitor;
import io.micronaut.inject.writer.GeneratedFile;
import org.codehaus.groovy.ast.ASTNode;
import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.control.ClassNodeResolver;
import org.codehaus.groovy.control.CompilationUnit;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.Janitor;
import org.codehaus.groovy.control.SourceUnit;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The visitor context when visiting Groovy code.
 *
 * @author James Kleeh
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
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
    private final ExpressionCompilationContextFactory expressionCompilationContextFactory;
    private final GroovyNativeElementHelper nativeElementHelper;
    private final GroovyAnnotationMetadataBuilder annotationMetadataBuilder;

    /**
     * @param sourceUnit The source unit
     * @param compilationUnit The compilation unit
     */
    public GroovyVisitorContext(SourceUnit sourceUnit, @Nullable CompilationUnit compilationUnit) {
        this(sourceUnit, compilationUnit, createOutputWriter(sourceUnit, compilationUnit));
    }

    /**
     * @param sourceUnit The source unit
     * @param compilationUnit The compilation unit
     * @param outputVisitor The class writer output visitor
     */
    public GroovyVisitorContext(SourceUnit sourceUnit, @Nullable CompilationUnit compilationUnit, ClassWriterOutputVisitor outputVisitor) {
        this.sourceUnit = sourceUnit;
        this.compilationUnit = compilationUnit;
        this.outputVisitor = outputVisitor;
        this.attributes = VISITOR_ATTRIBUTES;
        this.nativeElementHelper = new GroovyNativeElementHelper();
        this.groovyElementFactory = new GroovyElementFactory(this);
        this.annotationMetadataBuilder = new GroovyAnnotationMetadataBuilder(sourceUnit, compilationUnit, nativeElementHelper, this);
        this.elementAnnotationMetadataFactory = new GroovyElementAnnotationMetadataFactory(false, annotationMetadataBuilder);
        this.expressionCompilationContextFactory = new DefaultExpressionCompilationContextFactory(this);
    }

    private static ClassWriterOutputVisitor createOutputWriter(SourceUnit sourceUnit, @Nullable CompilationUnit compilationUnit) {
        if (sourceUnit != null) {
            if (sourceUnit.getClassLoader() instanceof InMemoryByteCodeGroovyClassLoader inMemoryByteCodeGroovyClassLoader) {
                return new InMemoryClassWriterOutputVisitor(inMemoryByteCodeGroovyClassLoader);
            }
            if (sourceUnit.getConfiguration() != null) {
                File classesDir = sourceUnit.getConfiguration().getTargetDirectory();
                if (classesDir != null) {
                    return new DirectoryClassWriterOutputVisitor(classesDir);
                }
            }
        }
        return new GroovyClassWriterOutputVisitor(compilationUnit);
    }

    @Override
    public Language getLanguage() {
        return Language.GROOVY;
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
        if (name == null) {
            return Optional.empty();
        } else if (compilationUnit == null) {
            return Optional.ofNullable(classNodeFromClassLoader(name)).map(cn ->
                groovyElementFactory.newClassElement(cn, annotationMetadataFactory)
            );
        }

        ClassNodeResolver.LookupResult lookupResult = compilationUnit.getClassNodeResolver().resolveName(name, compilationUnit);
        Optional<ClassNode> classNode;
        if (lookupResult != null) {
            classNode = Optional.ofNullable(lookupResult.getClassNode());
        } else {
            classNode = Optional.ofNullable(compilationUnit.getClassNode(name));
        }

        ClassNode finalClassNode = classNode.orElseGet(() -> classNodeFromClassLoader(name));

        return Optional.ofNullable(finalClassNode).map(cn -> groovyElementFactory.newClassElement(cn, annotationMetadataFactory));
    }

    private ClassNode classNodeFromClassLoader(String name) {
        ClassNode cn = null;
        if (sourceUnit != null) {
            GroovyClassLoader classLoader = sourceUnit.getClassLoader();
            if (classLoader != null) {
                cn = ClassUtils.forName(name, classLoader).map(ClassHelper::make).orElse(null);
            }
        }
        return cn;
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
            return ClassElement.ZERO_CLASS_ELEMENTS;
        }

        var scanner = new ClassPathAnnotationScanner(compilationUnit.getClassLoader());
        var classElements = new ArrayList<ClassElement>();
        for (String s : stereotypes) {
            scanner.scan(s, aPackage).forEach(aClass -> {
                final ClassNode classNode = ClassHelper.make(aClass);
                classElements.add(groovyElementFactory.newClassElement(classNode, getElementAnnotationMetadataFactory()));
            });
        }
        return classElements.toArray(ClassElement.ZERO_CLASS_ELEMENTS);
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
    public @NonNull ExpressionCompilationContextFactory getExpressionCompilationContextFactory() {
        return this.expressionCompilationContextFactory;
    }

    @Override
    public @NonNull GroovyAnnotationMetadataBuilder getAnnotationMetadataBuilder() {
        return annotationMetadataBuilder;
    }

    @Override
    public void info(String message, @Nullable Element element) {
        var msg = new StringBuilder("Note: ").append(message);
        if (element instanceof AbstractGroovyElement abstractGroovyElement) {
            ASTNode expr = abstractGroovyElement.getNativeType().annotatedNode();
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
        if (element instanceof AbstractGroovyElement abstractGroovyElement) {
            AstMessageUtils.error(sourceUnit, abstractGroovyElement.getNativeType().annotatedNode(), message);
        } else {
            AstMessageUtils.error(sourceUnit, null, message);
        }
    }

    public final void fail(String message, ASTNode expr) {
        AstMessageUtils.error(sourceUnit, expr, message);
    }

    @Override
    public void warn(String message, @Nullable Element element) {
        if (element instanceof AbstractGroovyElement abstractGroovyElement) {
            AstMessageUtils.warning(sourceUnit, abstractGroovyElement.getNativeType().annotatedNode(), message);
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
    public Optional<GeneratedFile> visitGeneratedFile(String path, Element... originatingElements) {
        return outputVisitor.visitGeneratedFile(path, originatingElements);
    }

    @Override
    public Optional<GeneratedFile> visitGeneratedSourceFile(String packageName, String fileNameWithoutExtension, Element... originatingElements) {
        return outputVisitor.visitGeneratedSourceFile(packageName, fileNameWithoutExtension, originatingElements);
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
     * @return The native element helper
     */
    @Internal
    public GroovyNativeElementHelper getNativeElementHelper() {
        return nativeElementHelper;
    }

    /**
     * Groovy visitor context options from the Groovy {@link CompilerConfiguration}
     * (joint-compilation {@code -A} flags / named values) and {@link System#getProperties()}.
     * <p><b>System properties have priority over compiler arguments.</b></p>
     * <p><b>All option names MUST start with {@link GroovyVisitorContext#MICRONAUT_BASE_OPTION_NAME}</b></p>
     *
     * @return options {@link Map}
     */
    @Override
    public Map<String, String> getOptions() {
        Map<String, String> compilerOptions = getCompilerOptions();
        Map<String, String> systemPropsOptions = VisitorContextUtils.getSystemOptions();
        return Stream.of(compilerOptions, systemPropsOptions)
            .flatMap(map -> map.entrySet().stream())
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                Map.Entry::getValue,
                (v1, v2) -> StringUtils.isNotEmpty(v2) ? v2 : v1));
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
     *
     * @param groovyBeanDefinitionBuilder The groovy bean definition builder
     */
    @Internal
    void addBeanDefinitionBuilder(GroovyBeanDefinitionBuilder groovyBeanDefinitionBuilder) {
        this.beanDefinitionBuilders.add(groovyBeanDefinitionBuilder);
    }

    private Map<String, String> getCompilerOptions() {
        CompilerConfiguration configuration = sourceUnit != null ? sourceUnit.getConfiguration() : null;
        if (configuration == null && compilationUnit != null) {
            configuration = compilationUnit.getConfiguration();
        }
        if (configuration == null) {
            return Collections.emptyMap();
        }
        Map<String, Object> jointCompilationOptions = configuration.getJointCompilationOptions();
        if (jointCompilationOptions == null || jointCompilationOptions.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, String> options = new LinkedHashMap<>();
        collectProcessorFlags(jointCompilationOptions.get("flags"), options);
        collectNamedProcessorValues(jointCompilationOptions.get("namedValues"), options);
        for (Map.Entry<String, Object> entry : jointCompilationOptions.entrySet()) {
            if (entry.getValue() != null) {
                putIfMicronautOption(entry.getKey(), String.valueOf(entry.getValue()), options);
            }
        }
        return options;
    }

    private static void collectProcessorFlags(Object flags, Map<String, String> options) {
        if (flags instanceof String[] array) {
            for (String flag : array) {
                collectProcessorToken(flag, options);
            }
        } else if (flags instanceof Iterable<?> iterable) {
            for (Object flag : iterable) {
                if (flag != null) {
                    collectProcessorToken(flag.toString(), options);
                }
            }
        }
    }

    private static void collectNamedProcessorValues(Object namedValues, Map<String, String> options) {
        if (namedValues instanceof String[] array) {
            for (int i = 0; i + 1 < array.length; i += 2) {
                collectNamedProcessorValue(array[i], array[i + 1], options);
            }
        } else if (namedValues instanceof List<?> list) {
            for (int i = 0; i + 1 < list.size(); i += 2) {
                Object name = list.get(i);
                Object value = list.get(i + 1);
                if (name != null && value != null) {
                    collectNamedProcessorValue(name.toString(), value.toString(), options);
                }
            }
        }
    }

    private static void collectNamedProcessorValue(String name, String value, Map<String, String> options) {
        if (name == null || value == null) {
            return;
        }
        String optionName = stripAnnotationProcessorPrefix(name);
        if (optionName != null) {
            putIfMicronautOption(optionName, value, options);
            return;
        }
        if ("A".equals(name) || "-A".equals(name)) {
            collectProcessorToken("-A" + value, options);
        }
    }

    private static void collectProcessorToken(String token, Map<String, String> options) {
        if (token == null || token.isEmpty()) {
            return;
        }
        String option = stripAnnotationProcessorPrefix(token);
        if (option == null) {
            return;
        }
        int eq = option.indexOf('=');
        if (eq < 0) {
            putIfMicronautOption(option, "", options);
        } else {
            putIfMicronautOption(option.substring(0, eq), option.substring(eq + 1), options);
        }
    }

    /**
     * Groovy joint-compilation stores javac switches without the leading dash
     * ({@code Amicronaut.foo=bar} becomes {@code -Amicronaut.foo=bar}).
     *
     * @param token a flag or named option
     * @return the processor option without the {@code -A}/{@code A} prefix, or {@code null}
     */
    private static String stripAnnotationProcessorPrefix(String token) {
        if (token.startsWith("-A")) {
            return token.substring(2);
        }
        if (token.startsWith("A") && token.length() > 1) {
            String remainder = token.substring(1);
            if (remainder.startsWith(MICRONAUT_BASE_OPTION_NAME)) {
                return remainder;
            }
        }
        return null;
    }

    private static void putIfMicronautOption(String key, String value, Map<String, String> options) {
        if (key != null && key.startsWith(MICRONAUT_BASE_OPTION_NAME)) {
            options.put(key, value);
        }
    }
}
