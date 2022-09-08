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
package io.micronaut.annotation.processing.visitor;

import io.micronaut.annotation.processing.AnnotationProcessingOutputVisitor;
import io.micronaut.annotation.processing.AnnotationUtils;
import io.micronaut.annotation.processing.GenericUtils;
import io.micronaut.annotation.processing.JavaAnnotationMetadataBuilder;
import io.micronaut.annotation.processing.JavaElementAnnotationMetadataFactory;
import io.micronaut.annotation.processing.ModelUtils;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.value.MutableConvertibleValues;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ElementAnnotationMetadataFactory;
import io.micronaut.inject.ast.beans.BeanElement;
import io.micronaut.inject.ast.beans.BeanElementBuilder;
import io.micronaut.inject.configuration.ConfigurationMetadataBuilder;
import io.micronaut.inject.util.VisitorContextUtils;
import io.micronaut.inject.visitor.BeanElementVisitorContext;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.inject.writer.AbstractBeanDefinitionBuilder;
import io.micronaut.inject.writer.GeneratedFile;

import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import javax.tools.JavaFileManager;
import javax.tools.StandardLocation;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The visitor context when visiting Java code.
 *
 * @author James Kleeh
 * @since 1.0
 */
@Internal
public class JavaVisitorContext implements VisitorContext, BeanElementVisitorContext {

    private final Messager messager;
    private final Elements elements;
    private final AnnotationUtils annotationUtils;
    private final Types types;
    private final ModelUtils modelUtils;
    private final AnnotationProcessingOutputVisitor outputVisitor;
    private final MutableConvertibleValues<Object> visitorAttributes;
    private final GenericUtils genericUtils;
    private final ProcessingEnvironment processingEnv;
    private final List<String> generatedResources = new ArrayList<>();
    private final List<AbstractBeanDefinitionBuilder> beanDefinitionBuilders = new ArrayList<>();
    private final JavaElementFactory elementFactory;
    private final TypeElementVisitor.VisitorKind visitorKind;
    private @Nullable
    JavaFileManager standardFileManager;
    private final JavaAnnotationMetadataBuilder annotationMetadataBuilder;
    private final JavaElementAnnotationMetadataFactory elementAnnotationMetadataFactory;

    /**
     * The default constructor.
     *
     * @param processingEnv     The processing environment
     * @param messager          The messager
     * @param elements          The elements
     * @param annotationUtils   The annotation utils
     * @param types             Type types
     * @param modelUtils        The model utils
     * @param genericUtils      The generic type utils
     * @param filer             The filer
     * @param visitorAttributes The attributes
     * @param visitorKind       The visitor kind
     */
    public JavaVisitorContext(
        ProcessingEnvironment processingEnv,
        Messager messager,
        Elements elements,
        AnnotationUtils annotationUtils,
        Types types,
        ModelUtils modelUtils,
        GenericUtils genericUtils,
        Filer filer,
        MutableConvertibleValues<Object> visitorAttributes,
        TypeElementVisitor.VisitorKind visitorKind) {
        this.messager = messager;
        this.elements = elements;
        this.annotationUtils = annotationUtils;
        this.types = types;
        this.modelUtils = modelUtils;
        this.genericUtils = genericUtils;
        this.outputVisitor = new AnnotationProcessingOutputVisitor(filer);
        this.visitorAttributes = visitorAttributes;
        this.processingEnv = processingEnv;
        this.elementFactory = new JavaElementFactory(this);
        this.visitorKind = visitorKind;
        this.annotationMetadataBuilder = new JavaAnnotationMetadataBuilder(elements, messager, annotationUtils, modelUtils);
        this.elementAnnotationMetadataFactory = new JavaElementAnnotationMetadataFactory(false, this.annotationMetadataBuilder);
    }

    /**
     * @return The visitor kind
     */
    public TypeElementVisitor.VisitorKind getVisitorKind() {
        return visitorKind;
    }

    /**
     * @return The processing environment
     */
    public ProcessingEnvironment getProcessingEnv() {
        return processingEnv;
    }

    @NonNull
    @Override
    public Iterable<URL> getClasspathResources(@NonNull String path) {
        // reflective hack required because no way to get the JavaFileManager
        // from public processor API
        info("EXPERIMENTAL: Compile time resource scanning is experimental", null);
        JavaFileManager standardFileManager = getStandardFileManager(processingEnv).orElse(null);
        if (standardFileManager != null) {
            try {
                final ClassLoader classLoader = standardFileManager
                    .getClassLoader(StandardLocation.CLASS_PATH);

                if (classLoader != null) {
                    final Enumeration<URL> resources = classLoader.getResources(path);
                    return CollectionUtils.enumerationToIterable(resources);
                }
            } catch (IOException e) {
                // ignore
            }
        }
        return Collections.emptyList();
    }

    @Override
    public Optional<ClassElement> getClassElement(String name) {
        return getClassElement(name, elementAnnotationMetadataFactory);
    }

    @Override
    public Optional<ClassElement> getClassElement(String name, ElementAnnotationMetadataFactory annotationMetadataFactory) {
        TypeElement typeElement = elements.getTypeElement(name);
        if (typeElement == null) {
            // maybe inner class?
            typeElement = elements.getTypeElement(name.replace('$', '.'));
        }
        return Optional.ofNullable(typeElement)
            .map(typeElement1 -> elementFactory.newClassElement(typeElement1, annotationMetadataFactory));
    }

    @Override
    public @NonNull
    ClassElement[] getClassElements(@NonNull String aPackage, @NonNull String... stereotypes) {
        ArgumentUtils.requireNonNull("aPackage", aPackage);
        ArgumentUtils.requireNonNull("stereotypes", stereotypes);
        final PackageElement packageElement = elements.getPackageElement(aPackage);
        if (packageElement != null) {
            List<ClassElement> classElements = new ArrayList<>();

            populateClassElements(stereotypes, packageElement, classElements);
            return classElements.toArray(new ClassElement[0]);
        }
        return new ClassElement[0];
    }

    @NonNull
    @Override
    public JavaElementFactory getElementFactory() {
        return elementFactory;
    }

    @Override
    public JavaElementAnnotationMetadataFactory getElementAnnotationMetadataFactory() {
        return elementAnnotationMetadataFactory;
    }

    @Override
    public JavaAnnotationMetadataBuilder getAnnotationMetadataBuilder() {
        return annotationMetadataBuilder;
    }

    @Override
    public void info(String message, @Nullable io.micronaut.inject.ast.Element element) {
        printMessage(message, Diagnostic.Kind.NOTE, element);
    }

    @Override
    public void info(String message) {
        if (StringUtils.isNotEmpty(message)) {
            messager.printMessage(Diagnostic.Kind.NOTE, message);
        }
    }

    @Override
    public void fail(String message, @Nullable io.micronaut.inject.ast.Element element) {
        printMessage(message, Diagnostic.Kind.ERROR, element);
    }

    @Override
    public void warn(String message, @Nullable io.micronaut.inject.ast.Element element) {
        printMessage(message, Diagnostic.Kind.WARNING, element);
    }

    private void printMessage(String message, Diagnostic.Kind kind, @Nullable io.micronaut.inject.ast.Element element) {
        if (StringUtils.isNotEmpty(message)) {
            if (element instanceof BeanElement) {
                element = ((BeanElement) element).getDeclaringClass();
            }
            if (element instanceof AbstractJavaElement) {
                Element el = (Element) element.getNativeType();
                messager.printMessage(kind, message, el);
            } else {
                messager.printMessage(kind, message);
            }
        }
    }

    @Override
    public OutputStream visitClass(String classname, @Nullable io.micronaut.inject.ast.Element originatingElement) throws IOException {
        return outputVisitor.visitClass(classname, new io.micronaut.inject.ast.Element[]{originatingElement});
    }

    @Override
    public OutputStream visitClass(String classname, io.micronaut.inject.ast.Element... originatingElements) throws IOException {
        return outputVisitor.visitClass(classname, originatingElements);
    }

    @Override
    public void visitServiceDescriptor(String type, String classname) {
        outputVisitor.visitServiceDescriptor(type, classname);
    }

    @Override
    public void visitServiceDescriptor(String type, String classname, io.micronaut.inject.ast.Element originatingElement) {
        outputVisitor.visitServiceDescriptor(type, classname, originatingElement);
    }

    @Override
    public Optional<GeneratedFile> visitMetaInfFile(String path, io.micronaut.inject.ast.Element... originatingElements) {
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
     * The messager.
     *
     * @return The messager
     */
    public Messager getMessager() {
        return messager;
    }

    /**
     * The model utils.
     *
     * @return The model utils
     */
    public ModelUtils getModelUtils() {
        return modelUtils;
    }

    /**
     * The elements.
     *
     * @return The elements
     */
    public Elements getElements() {
        return elements;
    }

    /**
     * The annotation utils.
     *
     * @return The annotation utils
     */
    public AnnotationUtils getAnnotationUtils() {
        return annotationUtils;
    }

    /**
     * The types.
     *
     * @return The types
     */
    public Types getTypes() {
        return types;
    }

    /**
     * The generic utils object.
     *
     * @return The generic utils
     */
    public GenericUtils getGenericUtils() {
        return genericUtils;
    }

    /**
     * Java visitor context options from <code>javac</code> arguments and {@link System#getProperties()}
     * <p><b>System properties has priority over arguments.</b></p>
     *
     * @return Java visitor context options for all visitors
     * @see io.micronaut.inject.visitor.TypeElementVisitor
     * @see <a href="https://docs.oracle.com/javase/8/docs/technotes/tools/windows/javac.html">javac arguments</a>
     */
    @Override
    public Map<String, String> getOptions() {
        Map<String, String> processorOptions = VisitorContextUtils.getProcessorOptions(processingEnv);
        Map<String, String> systemPropsOptions = VisitorContextUtils.getSystemOptions();
        // Merge both options, with system props overriding on duplications
        return Stream.of(processorOptions, systemPropsOptions)
            .flatMap(map -> map.entrySet().stream())
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                Map.Entry::getValue,
                (v1, v2) -> StringUtils.isNotEmpty(v2) ? v2 : v1));
    }

    @Override
    public MutableConvertibleValues<Object> put(CharSequence key, @Nullable Object value) {
        visitorAttributes.put(key, value);
        return this;
    }

    @Override
    public MutableConvertibleValues<Object> remove(CharSequence key) {
        visitorAttributes.remove(key);
        return this;
    }

    @Override
    public MutableConvertibleValues<Object> clear() {
        visitorAttributes.clear();
        return this;
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

    private void populateClassElements(@NonNull String[] stereotypes, PackageElement packageElement, List<ClassElement> classElements) {
        final List<? extends Element> enclosedElements = packageElement.getEnclosedElements();
        boolean includeAll = Arrays.equals(stereotypes, new String[]{"*"});
        for (Element enclosedElement : enclosedElements) {
            if (enclosedElement instanceof TypeElement) {
                JavaClassElement classElement = elementFactory.newClassElement((TypeElement) enclosedElement, elementAnnotationMetadataFactory);
                if (includeAll || Arrays.stream(stereotypes).anyMatch(classElement::hasStereotype)) {
                    if (!classElement.isAbstract()) {
                        classElements.add(classElement);
                    }
                }
            } else if (enclosedElement instanceof PackageElement) {
                populateClassElements(stereotypes, (PackageElement) enclosedElement, classElements);
            }
        }
    }

    private Optional<JavaFileManager> getStandardFileManager(ProcessingEnvironment processingEnv) {
        if (this.standardFileManager == null) {

            final Optional<Method> contextMethod = ReflectionUtils.getMethod(processingEnv.getClass(), "getContext");
            if (contextMethod.isPresent()) {
                final Object context = ReflectionUtils.invokeMethod(processingEnv, contextMethod.get());
                try {
                    if (context != null) {

                        final Optional<Method> getMethod = ReflectionUtils.getMethod(context.getClass(), "get", Class.class);
                        this.standardFileManager = (JavaFileManager)
                            getMethod.map(method -> ReflectionUtils.invokeMethod(context, method, JavaFileManager.class)).orElse(null);
                    }
                } catch (Exception e) {
                    // ignore
                }
            }
        }
        return Optional.ofNullable(this.standardFileManager);
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
     * @param javaBeanDefinitionBuilder The bean builder
     */
    @Internal
    void addBeanDefinitionBuilder(JavaBeanDefinitionBuilder javaBeanDefinitionBuilder) {
        this.beanDefinitionBuilders.add(javaBeanDefinitionBuilder);
    }

    @Override
    public BeanElementBuilder addAssociatedBean(io.micronaut.inject.ast.Element originatingElement, ClassElement type) {
        return new JavaBeanDefinitionBuilder(
            originatingElement,
            type,
            ConfigurationMetadataBuilder.INSTANCE,
            type instanceof AbstractJavaElement ? ((AbstractJavaElement) type).elementAnnotationMetadataFactory : elementAnnotationMetadataFactory,
            this
        );
    }
}
