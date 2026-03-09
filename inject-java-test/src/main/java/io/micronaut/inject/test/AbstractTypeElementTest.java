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
package io.micronaut.inject.test;

import com.sun.source.util.JavacTask;
import io.micronaut.annotation.processing.AggregatingTypeElementVisitorProcessor;
import io.micronaut.annotation.processing.JavaAnnotationMetadataBuilder;
import io.micronaut.annotation.processing.JavaNativeElementsHelper;
import io.micronaut.annotation.processing.ModelUtils;
import io.micronaut.annotation.processing.TypeElementVisitorProcessor;
import io.micronaut.annotation.processing.test.JavaFileObjects;
import io.micronaut.annotation.processing.test.JavaParser;
import io.micronaut.annotation.processing.test.TestingAggregatingTypeElementVisitorProcessor;
import io.micronaut.annotation.processing.test.TestingTypeElementVisitorProcessor;
import io.micronaut.annotation.processing.visitor.JavaElementFactory;
import io.micronaut.annotation.processing.visitor.JavaVisitorContext;
import io.micronaut.aop.internal.InterceptorRegistryBean;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.ApplicationContextBuilder;
import io.micronaut.context.DefaultBeanDefinitionsProvider;
import io.micronaut.context.Qualifier;
import io.micronaut.context.event.ApplicationEventPublisherFactory;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationMetadataProvider;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.convert.value.MutableConvertibleValuesMap;
import io.micronaut.core.graal.GraalReflectionConfigurer;
import io.micronaut.core.io.IOUtils;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.inject.BeanConfiguration;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.BeanDefinitionReference;
import io.micronaut.inject.annotation.AbstractAnnotationMetadataBuilder;
import io.micronaut.inject.annotation.AnnotationMapper;
import io.micronaut.inject.annotation.AnnotationMetadataWriter;
import io.micronaut.inject.annotation.AnnotationTransformer;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.GenericPlaceholderElement;
import io.micronaut.inject.ast.WildcardElement;
import io.micronaut.inject.provider.BeanProviderDefinition;
import io.micronaut.inject.provider.JakartaProviderBeanDefinition;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.writer.BeanConfigurationWriter;
import io.micronaut.inject.writer.BeanDefinitionVisitor;
import io.micronaut.inject.writer.BeanDefinitionWriter;
import org.intellij.lang.annotations.Language;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import spock.lang.Specification;

import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.tools.JavaFileObject;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Base class to extend from to allow compilation of Java sources
 * at runtime to allow testing of compile time behavior.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public abstract class AbstractTypeElementTest extends Specification {

    /**
     * Builds a class element for the given source code.
     *
     * @param cls The source
     * @return The class element
     * @deprecated Use closure equivalent and supply assertions
     */
    @Deprecated
    protected final ClassElement buildClassElement(@Language("java") String cls) {
        try (TypeElementInfo typeElementInfo = buildTypeElementInfo(cls)) {
            TypeElement typeElement = typeElementInfo.getTypeElement();
            JavaParser parser = typeElementInfo.getJavaParser();
            JavacTask lastTask = parser.getLastTask().orElseThrow();
            ProcessingEnvironment processingEnv = parser.getProcessingEnv();
            Messager messager = processingEnv.getMessager();
            ModelUtils modelUtils = new ModelUtils(lastTask.getElements(), lastTask.getTypes()) { };

            JavaVisitorContext visitorContext = new JavaVisitorContext(
                processingEnv,
                messager,
                lastTask.getElements(),
                lastTask.getTypes(),
                modelUtils,
                parser.getFiler(),
                new MutableConvertibleValuesMap<>(),
                TypeElementVisitor.VisitorKind.ISOLATING
            );

            return new JavaElementFactory(visitorContext).newClassElement(typeElement, visitorContext.getElementAnnotationMetadataFactory());
        }
    }

    protected final <T> T buildClassElement(@Language("java") String packageInfo,
                                      @Language("java") String cls,
                                      Function<? super ClassElement, T> function) {
        Objects.requireNonNull(function, "function");
        JavaFiles files = new JavaFiles().add("Test", cls).add("package-info", packageInfo);
        return buildClassElement(files, function);
    }

    protected final <T> T buildClassElement(@Language("java") String cls, Function<? super ClassElement, T> function) {
        Objects.requireNonNull(function, "function");
        JavaFiles files = new JavaFiles().add("", cls);
        return buildClassElement(files, function);
    }

    protected final <T> T buildClassElement(JavaFiles files, Function<? super ClassElement, T> function) {
        Objects.requireNonNull(files, "files");
        Objects.requireNonNull(function, "function");
        return buildTypeElementInfo(files, typeElementInfo -> {
            JavaParser parser = typeElementInfo.getJavaParser();
            JavacTask lastTask = parser.getLastTask().orElseThrow();
            ProcessingEnvironment processingEnv = parser.getProcessingEnv();
            Messager messager = processingEnv.getMessager();
            ModelUtils modelUtils = new ModelUtils(lastTask.getElements(), lastTask.getTypes()) { };

            JavaVisitorContext visitorContext = new JavaVisitorContext(
                processingEnv,
                messager,
                lastTask.getElements(),
                lastTask.getTypes(),
                modelUtils,
                parser.getFiler(),
                new MutableConvertibleValuesMap<>(),
                TypeElementVisitor.VisitorKind.ISOLATING
            );

            ClassElement classElement = new JavaElementFactory(visitorContext)
                .newClassElement(typeElementInfo.getTypeElement(), visitorContext.getElementAnnotationMetadataFactory());
            return function.apply(classElement);
        });
    }

    /**
     * @param cls The class string
     * @return The annotation metadata for the class
     */
    protected AnnotationMetadata buildTypeAnnotationMetadata(@Language("java") String cls) {
        AbstractAnnotationMetadataBuilder.clearMutated();
        Element element = buildTypeElement(cls);
        JavaAnnotationMetadataBuilder builder = newJavaAnnotationBuilder();
        AnnotationMetadata metadata = element != null ? builder.lookupOrBuildForType(element) : null;
        AbstractAnnotationMetadataBuilder.copyToRuntime();
        return metadata;
    }

    protected final AnnotationMetadata buildMethodArgumentAnnotationMetadata(@Language("java") String cls,
                                                                       String methodName,
                                                                       String argumentName) {
        AbstractAnnotationMetadataBuilder.clearMutated();
        TypeElement element = buildTypeElement(cls);
        ExecutableElement method = null;
        for (Element enclosed : element.getEnclosedElements()) {
            if (methodName.equals(enclosed.getSimpleName().toString()) && enclosed instanceof ExecutableElement executable) {
                method = executable;
                break;
            }
        }
        if (method == null) {
            return null;
        }
        VariableElement argument = null;
        for (VariableElement parameter : method.getParameters()) {
            if (argumentName.equals(parameter.getSimpleName().toString())) {
                argument = parameter;
                break;
            }
        }
        JavaAnnotationMetadataBuilder builder = newJavaAnnotationBuilder();
        AnnotationMetadata metadata = argument != null ? builder.lookupOrBuildForMethod(element, argument) : null;
        AbstractAnnotationMetadataBuilder.copyToRuntime();
        return metadata;
    }

    /**
     * Build and return a {@link BeanIntrospection} for the given class name and class data.
     *
     * @param className The class name
     * @param cls       The source
     * @return the introspection if it is correct
     */
    protected BeanIntrospection<?> buildBeanIntrospection(String className, @Language("java") String cls) {
        String simpleName = NameUtils.getSimpleName(className);
        String beanDefName = (simpleName.startsWith("$") ? "" : "$") + simpleName + "$Introspection";
        String packageName = NameUtils.getPackageName(className);
        String beanFullName = packageName + '.' + beanDefName;
        ClassLoader classLoader = buildClassLoader(className, cls);
        try {
            Class<?> introspectionClass = classLoader.loadClass(beanFullName);
            return (BeanIntrospection<?>) introspectionClass.getDeclaredConstructor().newInstance();
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            throw new IllegalStateException("Failed to instantiate BeanIntrospection: " + beanFullName, e);
        }
    }

    /**
     * Build and return a {@link GraalReflectionConfigurer} for the given class name and class data.
     *
     * @param className The class name
     * @param cls       The source
     * @return the GraalReflectionConfigurer if it is correct
     */
    protected GraalReflectionConfigurer buildReflectionConfigurer(String className, @Language("java") String cls) {
        String beanDefName = (className.startsWith("$") ? "" : "$") + NameUtils.getSimpleName(className) + GraalReflectionConfigurer.CLASS_SUFFIX;
        String packageName = NameUtils.getPackageName(className);
        String beanFullName = packageName + '.' + beanDefName;
        ClassLoader classLoader = buildClassLoader(className, cls);
        try {
            Class<?> configurerClass = classLoader.loadClass(beanFullName);
            return (GraalReflectionConfigurer) configurerClass.getDeclaredConstructor().newInstance();
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            throw new IllegalStateException("Failed to instantiate GraalReflectionConfigurer: " + beanFullName, e);
        }
    }

    /**
     * @param annotationExpression the annotation expression
     * @param packages             the packages to import
     * @return The metadata
     */
    protected AnnotationMetadata buildAnnotationMetadata(String annotationExpression, String... packages) {
        LinkedHashSet<String> packageSet = new LinkedHashSet<>();
        packageSet.add("io.micronaut.core.annotation");
        packageSet.add("io.micronaut.inject.annotation");
        if (packages != null) {
            packageSet.addAll(Arrays.asList(packages));
        }
        String lineSeparator = System.lineSeparator();
        String imports = packageSet.stream()
            .map(pkg -> "import " + pkg + ".*;")
            .collect(Collectors.joining(lineSeparator));

        StringBuilder source = new StringBuilder();
        source.append(imports).append(lineSeparator).append(lineSeparator);
        source.append(annotationExpression).append(lineSeparator);
        source.append("class Test {").append(lineSeparator).append(lineSeparator);
        source.append("}").append(lineSeparator);

        return buildTypeAnnotationMetadata(source.toString());
    }

    /**
     * Reads a generated file.
     *
     * @param filePath  The file path
     * @param className The class name
     * @param code      The code
     * @return The reader
     * @throws IOException When an error occurs reading the file
     */
    public @Nullable Reader readGenerated(@NonNull String filePath,
                                          String className,
                                          @Language("java") String code) throws IOException {
        try (JavaParser parser = newJavaParser()) {
            return parser.readGenerated(filePath, className, code);
        }
    }

    /**
     * Gets a bean from the context for the given class name.
     *
     * @param context   The context
     * @param className The class name
     * @param qualifier The qualifier
     * @return The bean instance
     */
    protected final Object getBean(ApplicationContext context, String className, @Nullable Qualifier<?> qualifier) {
        try {
            Class beanType = context.getClassLoader().loadClass(className);
            return context.getBean(beanType, qualifier);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    protected final Object getBean(ApplicationContext context, String className) {
        return getBean(context, className, null);
    }

    /**
     * Gets a bean definition from the context for the given class name.
     *
     * @param context   The context
     * @param className The class name
     * @param qualifier The qualifier
     * @return The bean definition
     */
    protected final BeanDefinition<?> getBeanDefinition(ApplicationContext context,
                                                  String className,
                                                  @Nullable Qualifier<?> qualifier) {
        try {
            Class beanType = context.getClassLoader().loadClass(className);
            return context.getBeanDefinition(beanType, qualifier);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    protected final BeanDefinition<?> getBeanDefinition(ApplicationContext context, String className) {
        return getBeanDefinition(context, className, null);
    }

    /**
     * Builds a {@link ApplicationContext} containing only the classes produced by the given source.
     *
     * @param source The source code
     * @return The context. Should be shutdown after use
     */
    protected final ApplicationContext buildContext(@Language("java") String source) {
        return buildContext("test.Source" + System.currentTimeMillis(), source, false, Collections.emptyMap());
    }

    protected final ApplicationContext buildContext(String className,
                                              @Language("java") String cls) {
        return buildContext(className, cls, false, Collections.emptyMap());
    }

    protected final ApplicationContext buildContext(String className,
                                              @Language("java") String cls,
                                              boolean includeAllBeans) {
        return buildContext(className, cls, includeAllBeans, Collections.emptyMap());
    }

    protected final ApplicationContext buildContext(String className,
                                              @Language("java") String cls,
                                              boolean includeAllBeans,
                                              Map<String, Object> properties) {
        return buildContext(null, className, cls, includeAllBeans, properties);
    }

    protected final ApplicationContext buildContext(@Nullable @Language("java") String packageJava,
                                              String className,
                                              @Language("java") String cls) {
        return buildContext(packageJava, className, cls, false, Collections.emptyMap());
    }

    protected final ApplicationContext buildContext(@Nullable @Language("java") String packageJava,
                                              String className,
                                              @Language("java") String cls,
                                              boolean includeAllBeans) {
        return buildContext(packageJava, className, cls, includeAllBeans, Collections.emptyMap());
    }

    protected final ApplicationContext buildContext(@Nullable @Language("java") String packageJava,
                                              String className,
                                              @Language("java") String cls,
                                              boolean includeAllBeans,
                                              Map<String, Object> properties) {
        JavaFiles files = new JavaFiles().add(className, cls);
        if (packageJava != null) {
            files.add("package-info", packageJava);
        }
        return buildContext(files, includeAllBeans, properties);
    }

    protected final ApplicationContext buildContext(JavaFiles files,
                                              boolean includeAllBeans,
                                              Map<String, Object> properties) {
        try (JavaParser parser = newJavaParser()) {
            Iterable<? extends JavaFileObject> javaFiles = parser.generate(
                files.getFiles().stream()
                    .map(entry -> JavaFileObjects.forSourceString(entry.getKey(), entry.getValue()))
                    .toArray(JavaFileObject[]::new)
            );
            ClassLoader classLoader = new JavaFileObjectClassLoader(javaFiles);

            ApplicationContextBuilder builder = ApplicationContext.builder();
            builder.classLoader(classLoader);
            builder.environments("test");
            builder.properties(properties);
            configureContext(builder);
            builder.beanDefinitionsProvider(loader -> {
                List<BeanDefinitionReference<?>> references = StreamSupport.stream(javaFiles.spliterator(), false)
                    .filter(jfo -> jfo.getKind() == JavaFileObject.Kind.CLASS
                        && (jfo.getName().endsWith(BeanDefinitionWriter.CLASS_SUFFIX + "$Reference" + ".class")
                        || jfo.getName().endsWith(BeanDefinitionWriter.CLASS_SUFFIX + ".class")))
                    .map(jfo -> {
                        String name = jfo.toUri().toString();
                        String prefix = "mem:///CLASS_OUTPUT/";
                        if (name.startsWith(prefix)) {
                            name = name.substring(prefix.length());
                        }
                        name = name.replace('/', '.');
                        if (name.endsWith(".class")) {
                            name = name.substring(0, name.length() - 6);
                        }
                        try {
                            return (BeanDefinitionReference<?>) classLoader.loadClass(name).getDeclaredConstructor().newInstance();
                        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException
                                 | InvocationTargetException | NoSuchMethodException e) {
                            throw new IllegalStateException("Failed to load bean definition reference: " + name, e);
                        }
                    })
                    .collect(Collectors.toCollection(ArrayList::new));

                if (includeAllBeans) {
                    references.addAll(new DefaultBeanDefinitionsProvider().provide(loader));
                } else {
                    references.addAll(getBuiltInBeanReferences());
                }
                return references;
            });
            return builder.build().start();
        }
    }

    /**
     * @return Obtains the built in bean references required for the context to function correctly
     */
    protected List<BeanDefinitionReference<?>> getBuiltInBeanReferences() {
        return List.of(
            new InterceptorRegistryBean(),
            new BeanProviderDefinition(),
            new JakartaProviderBeanDefinition(),
            new ApplicationEventPublisherFactory<>()
        );
    }

    /**
     * Create and return a new Java parser.
     *
     * @return The Java parser to use
     */
    protected JavaParser newJavaParser() {
        Collection<TypeElementVisitor<?, ?>> visitors = getLocalTypeElementVisitors();
        if (visitors.isEmpty()) {
            return new JavaParser();
        }
        return new JavaParser() {
            @Override
            protected TypeElementVisitorProcessor getTypeElementVisitorProcessor() {
                return new TestingTypeElementVisitorProcessor(visitors);
            }

            @Override
            protected AggregatingTypeElementVisitorProcessor getAggregatingTypeElementVisitorProcessor() {
                return new TestingAggregatingTypeElementVisitorProcessor(visitors);
            }
        };
    }

    protected final AnnotationMetadata buildMethodAnnotationMetadata(@Language("java") String cls, String methodName) {
        TypeElement element = buildTypeElement(cls);
        if (element == null) {
            return null;
        }
        Element method = null;
        for (Element enclosed : element.getEnclosedElements()) {
            if (methodName.equals(enclosed.getSimpleName().toString())) {
                method = enclosed;
                break;
            }
        }
        if (method == null) {
            return null;
        }
        JavaAnnotationMetadataBuilder builder = newJavaAnnotationBuilder();
        return builder.lookupOrBuildForMethod(element, method);
    }

    protected final TypeElement buildTypeElement(@Language("java") String cls) {
        try (JavaParser parser = newJavaParser()) {
            List<Element> elements = new ArrayList<>();
            parser.parseLines("", cls).forEach(elements::add);
            Element element = elements.isEmpty() ? null : elements.get(0);
            return element instanceof TypeElement typeElement ? typeElement : null;
        }
    }

    protected final TypeElementInfo buildTypeElementInfo(@Language("java") String cls) {
        JavaParser parser = newJavaParser();
        try {
            List<Element> elements = new ArrayList<>();
            parser.parseLines("", cls).forEach(elements::add);
            Element element = elements.isEmpty() ? null : elements.get(0);
            TypeElement typeElement = element instanceof TypeElement te ? te : null;
            return new TypeElementInfo(typeElement, parser);
        } catch (RuntimeException | Error e) {
            parser.close();
            throw e;
        }
    }

    protected final <T> T buildTypeElementInfo(JavaFiles files, Function<TypeElementInfo, T> callable) {
        Objects.requireNonNull(files, "files");
        Objects.requireNonNull(callable, "callable");
        try (JavaParser parser = newJavaParser()) {
            JavaFileObject[] sources = files.getFiles().stream()
                .map(entry -> JavaFileObjects.forSourceLines(entry.getKey(), entry.getValue()))
                .toArray(JavaFileObject[]::new);
            Iterator<? extends Element> iterator = parser.parse(sources).iterator();
            TypeElement element = null;
            while (iterator.hasNext()) {
                Element current = iterator.next();
                if (current instanceof TypeElement typeElement) {
                    element = typeElement;
                    break;
                }
            }
            return callable.apply(new TypeElementInfo(element, parser));
        }
    }

    protected final String buildAndReadResourceAsString(String resourceName, @Language("java") String cls) {
        ClassLoader classLoader = buildClassLoader("test.Test", cls);
        try {
            List<URL> resources = Collections.list(classLoader.getResources(resourceName));
            if (resources.isEmpty()) {
                throw new IllegalStateException("Resource not found: " + resourceName);
            }
            URL resource = resources.get(resources.size() - 1);
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.openStream()))) {
                return IOUtils.readText(reader);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to read resource: " + resourceName, e);
        }
    }

    protected final BeanDefinition<?> buildBeanDefinition(String className, @Language("java") String cls) {
        String classSimpleName = NameUtils.getSimpleName(className);
        String beanDefName = (classSimpleName.startsWith("$") ? "" : "$") + classSimpleName + BeanDefinitionWriter.CLASS_SUFFIX;
        String packageName = NameUtils.getPackageName(className);
        String beanFullName = packageName + '.' + beanDefName;
        ClassLoader classLoader = buildClassLoader(className, cls);
        try {
            Class<?> beanDefClass = classLoader.loadClass(beanFullName);
            return (BeanDefinition<?>) beanDefClass.getDeclaredConstructor().newInstance();
        } catch (ClassNotFoundException e) {
            return null;
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            throw new IllegalStateException("Failed to instantiate bean definition: " + beanFullName, e);
        }
    }

    protected final BeanDefinition<?> buildBeanDefinition(String packageName,
                                                     String className,
                                                     @Language("java") String cls) {
        String beanDefName = (className.startsWith("$") ? "" : "$") + className + BeanDefinitionWriter.CLASS_SUFFIX;
        String beanFullName = packageName + '.' + beanDefName;
        ClassLoader classLoader = buildClassLoader(className, cls);
        try {
            Class<?> beanDefClass = classLoader.loadClass(beanFullName);
            return (BeanDefinition<?>) beanDefClass.getDeclaredConstructor().newInstance();
        } catch (ClassNotFoundException e) {
            return null;
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            throw new IllegalStateException("Failed to instantiate bean definition: " + beanFullName, e);
        }
    }

    /**
     * Builds the bean definition for an AOP proxy bean.
     *
     * @param className The class name
     * @param cls       The class source
     * @return The bean definition
     */
    protected BeanDefinition<?> buildInterceptedBeanDefinition(String className, @Language("java") String cls) {
        String classSimpleName = NameUtils.getSimpleName(className);
        String beanDefName = (classSimpleName.startsWith("$") ? "" : "$") + classSimpleName
            + BeanDefinitionWriter.CLASS_SUFFIX + BeanDefinitionVisitor.PROXY_SUFFIX + BeanDefinitionWriter.CLASS_SUFFIX;
        String packageName = NameUtils.getPackageName(className);
        String beanFullName = packageName + '.' + beanDefName;
        ClassLoader classLoader = buildClassLoader(className, cls);
        try {
            Class<?> beanDefClass = classLoader.loadClass(beanFullName);
            return (BeanDefinition<?>) beanDefClass.getDeclaredConstructor().newInstance();
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException
                 | InvocationTargetException | NoSuchMethodException e) {
            throw new IllegalStateException("Failed to build intercepted bean definition: " + beanFullName, e);
        }
    }

    /**
     * Builds the bean definition for an AOP proxy bean.
     *
     * @param className The class name
     * @param cls       The class source
     * @return The bean definition
     */
    protected BeanDefinition<?> buildSimpleInterceptedBeanDefinition(String className, @Language("java") String cls) {
        String classSimpleName = NameUtils.getSimpleName(className);
        String beanDefName = (classSimpleName.startsWith("$") ? "" : "$") + classSimpleName
            + BeanDefinitionVisitor.PROXY_SUFFIX + BeanDefinitionWriter.CLASS_SUFFIX;
        String packageName = NameUtils.getPackageName(className);
        String beanFullName = packageName + '.' + beanDefName;
        ClassLoader classLoader = buildClassLoader(className, cls);
        try {
            Class<?> beanDefClass = classLoader.loadClass(beanFullName);
            return (BeanDefinition<?>) beanDefClass.getDeclaredConstructor().newInstance();
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException
                 | InvocationTargetException | NoSuchMethodException e) {
            throw new IllegalStateException("Failed to build simple intercepted bean definition: " + beanFullName, e);
        }
    }

    /**
     * Retrieve additional annotation mappers to apply.
     *
     * @param annotationName The annotation name
     * @return The mappers for the annotation
     */
    protected List<AnnotationMapper<? extends Annotation>> getLocalAnnotationMappers(@NonNull String annotationName) {
        return Collections.emptyList();
    }

    /**
     * Retrieve additional annotation transformers to apply.
     *
     * @param annotationName The annotation name
     * @return The transformers for the annotation
     */
    protected List<AnnotationTransformer<? extends Annotation>> getLocalAnnotationTransformers(@NonNull String annotationName) {
        return Collections.emptyList();
    }

    /**
     * Retrieve additional type element visitors for this test.
     *
     * @return the visitors
     */
    protected Collection<TypeElementVisitor<?, ?>> getLocalTypeElementVisitors() {
        return Collections.emptyList();
    }

    protected final BeanDefinitionReference<?> buildInterceptedBeanDefinitionReference(String className,
                                                                                 @Language("java") String cls) {
        String classSimpleName = NameUtils.getSimpleName(className);
        String beanDefName = (classSimpleName.startsWith("$") ? "" : "$") + classSimpleName
            + BeanDefinitionWriter.CLASS_SUFFIX + BeanDefinitionVisitor.PROXY_SUFFIX + BeanDefinitionWriter.CLASS_SUFFIX;
        String packageName = NameUtils.getPackageName(className);
        String beanFullName = packageName + '.' + beanDefName;
        ClassLoader classLoader = buildClassLoader(className, cls);
        try {
            Class<?> referenceClass = classLoader.loadClass(beanFullName);
            return (BeanDefinitionReference<?>) referenceClass.getDeclaredConstructor().newInstance();
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException
                 | InvocationTargetException | NoSuchMethodException e) {
            throw new IllegalStateException("Failed to build intercepted bean definition reference: " + beanFullName, e);
        }
    }

    protected final BeanDefinitionReference<?> buildBeanDefinitionReference(String className,
                                                                       @Language("java") String cls) {
        String classSimpleName = NameUtils.getSimpleName(className);
        String beanDefName = (classSimpleName.startsWith("$") ? "" : "$") + classSimpleName + BeanDefinitionWriter.CLASS_SUFFIX;
        String packageName = NameUtils.getPackageName(className);
        String beanFullName = packageName + '.' + beanDefName;
        ClassLoader classLoader = buildClassLoader(className, cls);
        try {
            Class<?> referenceClass = classLoader.loadClass(beanFullName);
            return (BeanDefinitionReference<?>) referenceClass.getDeclaredConstructor().newInstance();
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException
                 | InvocationTargetException | NoSuchMethodException e) {
            throw new IllegalStateException("Failed to build bean definition reference: " + beanFullName, e);
        }
    }

    protected final BeanConfiguration buildBeanConfiguration(String packageName, @Language("java") String cls) {
        ClassLoader classLoader = buildClassLoader(packageName + ".package-info", cls);
        try {
            Class<?> configClass = classLoader.loadClass(packageName + '.' + BeanConfigurationWriter.CLASS_SUFFIX);
            return (BeanConfiguration) configClass.getDeclaredConstructor().newInstance();
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException
                 | InvocationTargetException | NoSuchMethodException e) {
            throw new IllegalStateException("Failed to build bean configuration for package: " + packageName, e);
        }
    }

    protected final ClassLoader buildClassLoader(String className, @Language("java") String cls) {
        AbstractAnnotationMetadataBuilder.clearMutated();
        try (JavaParser parser = newJavaParser()) {
            Iterable<? extends JavaFileObject> files = parser.generate(className, cls);
            return new JavaFileObjectClassLoader(files);
        }
    }

    protected final AnnotationMetadata writeAndLoadMetadata(String className, AnnotationMetadata toWrite) {
        byte[] bytecode = AnnotationMetadataWriter.write(className, toWrite);
        String metadataClassName = className + AnnotationMetadata.CLASS_NAME_SUFFIX;
        ClassLoader classLoader = new DefiningClassLoader(metadataClassName, bytecode);
        try {
            Class<?> metadataClass = classLoader.loadClass(metadataClassName);
            AnnotationMetadataProvider provider = (AnnotationMetadataProvider) metadataClass.getDeclaredConstructor().newInstance();
            return provider.getAnnotationMetadata();
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException
                 | InvocationTargetException | NoSuchMethodException e) {
            throw new IllegalStateException("Failed to load annotation metadata for: " + metadataClassName, e);
        }
    }

    protected final JavaAnnotationMetadataBuilder newJavaAnnotationBuilder() {
        try (JavaParser parser = newJavaParser()) {
            JavacTask javacTask = parser.getJavacTask();
            ProcessingEnvironment processingEnv = parser.getProcessingEnv();
            Messager messager = processingEnv.getMessager();
            ModelUtils modelUtils = new ModelUtils(javacTask.getElements(), javacTask.getTypes()) { };
            JavaVisitorContext visitorContext = new JavaVisitorContext(
                processingEnv,
                messager,
                javacTask.getElements(),
                javacTask.getTypes(),
                modelUtils,
                parser.getFiler(),
                new MutableConvertibleValuesMap<>(),
                TypeElementVisitor.VisitorKind.ISOLATING
            );
            JavaNativeElementsHelper helper = new JavaNativeElementsHelper(javacTask.getElements(), modelUtils.getTypeUtils());
            return new JavaAnnotationMetadataBuilder(javacTask.getElements(), messager, modelUtils, helper, visitorContext) {
                @Override
                protected List<AnnotationMapper<Annotation>> getAnnotationMappers(@NonNull String annotationName) {
                    List<AnnotationMapper<Annotation>> loaded = super.getAnnotationMappers(annotationName);
                    List local = getLocalAnnotationMappers(annotationName);
                    if (!local.isEmpty()) {
                        List<AnnotationMapper<Annotation>> combined = new ArrayList<>();
                        if (loaded != null && !loaded.isEmpty()) {
                            combined.addAll(loaded);
                        }
                        combined.addAll(local);
                        return combined;
                    }
                    if (loaded == null) {
                        return Collections.emptyList();
                    }
                    return loaded;
                }

                @Override
                protected List<AnnotationTransformer<Annotation>> getAnnotationTransformers(@NonNull String annotationName) {
                    List<AnnotationTransformer<Annotation>> loaded = super.getAnnotationTransformers(annotationName);
                    List<AnnotationTransformer<? extends Annotation>> local = getLocalAnnotationTransformers(annotationName);
                    if (!local.isEmpty()) {
                        List<AnnotationTransformer<Annotation>> combined = new ArrayList<>();
                        if (loaded != null && !loaded.isEmpty()) {
                            combined.addAll(loaded);
                        }
                        for (AnnotationTransformer<? extends Annotation> transformer : local) {
                            @SuppressWarnings("unchecked")
                            AnnotationTransformer<Annotation> cast = (AnnotationTransformer<Annotation>) transformer;
                            combined.add(cast);
                        }
                        return combined;
                    }
                    return loaded == null ? Collections.emptyList() : loaded;
                }
            };
        }
    }

    /**
     * Allows configuring the context.
     *
     * @param contextBuilder The context builder
     */
    protected void configureContext(ApplicationContextBuilder contextBuilder) {
        // no-op by default
    }

    /**
     * Create a rough source signature of the given ClassElement, using {@link ClassElement#getBoundGenericTypes()}.
     * Can be used to test that {@link ClassElement#getBoundGenericTypes()} returns the right types in the right
     * context.
     *
     * @param classElement            The class element to reconstruct
     * @param typeVarsAsDeclarations  Whether type variables should be represented as declarations
     * @return a String representing the type signature.
     */
    @Experimental
    protected static String reconstructTypeSignature(ClassElement classElement, boolean typeVarsAsDeclarations) {
        return reconstructTypeSignatureInternal(classElement, typeVarsAsDeclarations);
    }

    @Experimental
    protected static String reconstructTypeSignature(ClassElement classElement) {
        return reconstructTypeSignatureInternal(classElement, false);
    }

    private static String reconstructTypeSignatureInternal(ClassElement classElement, boolean typeVarsAsDeclarations) {
        if (classElement.isArray()) {
            return reconstructTypeSignatureInternal(classElement.fromArray(), false) + "[]";
        } else if (classElement.isGenericPlaceholder()) {
            GenericPlaceholderElement genericPlaceholderElement = (GenericPlaceholderElement) classElement;
            String name = genericPlaceholderElement.getVariableName();
            if (typeVarsAsDeclarations) {
                List<? extends ClassElement> bounds = genericPlaceholderElement.getBounds();
                if (!bounds.isEmpty()) {
                    String firstBound = reconstructTypeSignatureInternal(bounds.get(0), false);
                    if (!"Object".equals(firstBound)) {
                        String joined = bounds.stream()
                            .map(bound -> reconstructTypeSignatureInternal(bound, false))
                            .collect(Collectors.joining(" & ", " extends ", ""));
                        name = name + joined;
                    }
                }
            } else {
                Optional<ClassElement> resolved = genericPlaceholderElement.getResolved();
                if (resolved.isPresent()) {
                    return reconstructTypeSignatureInternal(resolved.get(), false);
                }
            }
            return name;
        } else if (classElement.isWildcard()) {
            WildcardElement wildcardElement = (WildcardElement) classElement;
            List<? extends ClassElement> lowerBounds = wildcardElement.getLowerBounds();
            if (!lowerBounds.isEmpty()) {
                return lowerBounds.stream()
                    .map(bound -> reconstructTypeSignatureInternal(bound, false))
                    .collect(Collectors.joining(" | ", "? super ", ""));
            }
            List<? extends ClassElement> upperBounds = wildcardElement.getUpperBounds();
            if (upperBounds.size() == 1
                && "Object".equals(reconstructTypeSignatureInternal(upperBounds.get(0), false))) {
                return "?";
            }
            return upperBounds.stream()
                .map(bound -> reconstructTypeSignatureInternal(bound, false))
                .collect(Collectors.joining(" & ", "? extends ", ""));
        } else {
            Collection<? extends ClassElement> typeArguments = classElement.getTypeArguments().values();
            if (typeArguments.isEmpty() || typeArguments.stream().allMatch(ClassElement::isRawType)) {
                return classElement.getSimpleName();
            }
            return classElement.getSimpleName()
                + typeArguments.stream()
                    .map(arg -> reconstructTypeSignatureInternal(arg, false))
                    .collect(Collectors.joining(", ", "<", ">"));
        }
    }

    static final class TypeElementInfo implements AutoCloseable {
        private final TypeElement typeElement;
        private final JavaParser javaParser;
        private boolean closed;

        TypeElementInfo(TypeElement typeElement, JavaParser javaParser) {
            this.typeElement = typeElement;
            this.javaParser = javaParser;
        }

        TypeElement getTypeElement() {
            return typeElement;
        }

        JavaParser getJavaParser() {
            return javaParser;
        }

        @Override
        public void close() {
            if (!closed) {
                javaParser.close();
                closed = true;
            }
        }
    }

    static final class JavaFiles {
        private final List<Map.Entry<String, String>> files = new ArrayList<>();

        JavaFiles add(String filename, @Language("java") String code) {
            files.add(Map.entry(filename, code));
            return this;
        }

        List<Map.Entry<String, String>> getFiles() {
            return files;
        }
    }

    private static final class DefiningClassLoader extends ClassLoader {
        private final String className;
        private final byte[] bytecode;

        DefiningClassLoader(String className, byte[] bytecode) {
            this.className = className;
            this.bytecode = bytecode;
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            if (name.equals(className)) {
                return defineClass(name, bytecode, 0, bytecode.length);
            }
            return super.findClass(name);
        }
    }

}
