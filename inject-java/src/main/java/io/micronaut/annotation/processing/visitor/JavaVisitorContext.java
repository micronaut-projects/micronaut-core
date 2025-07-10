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
import io.micronaut.annotation.processing.JavaNativeElementsHelper;
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
import io.micronaut.expressions.context.DefaultExpressionCompilationContextFactory;
import io.micronaut.expressions.context.ExpressionCompilationContextFactory;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.annotation.AbstractAnnotationElement;
import io.micronaut.inject.ast.annotation.ElementAnnotationMetadataFactory;
import io.micronaut.inject.ast.beans.BeanElement;
import io.micronaut.inject.ast.beans.BeanElementBuilder;
import io.micronaut.inject.visitor.BeanElementVisitorContext;
import io.micronaut.inject.visitor.ElementPostponedToNextRoundException;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.inject.visitor.util.VisitorContextUtils;
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
import java.util.HashSet;
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
public final class JavaVisitorContext implements VisitorContext, BeanElementVisitorContext {

    private final Messager messager;
    private final Elements elements;
    private final Types types;
    private final ModelUtils modelUtils;
    private final AnnotationProcessingOutputVisitor outputVisitor;
    private final MutableConvertibleValues<Object> visitorAttributes;
    private final ProcessingEnvironment processingEnv;
    private final List<String> generatedResources = new ArrayList<>();
    private final List<AbstractBeanDefinitionBuilder> beanDefinitionBuilders = new ArrayList<>();
    private final JavaElementFactory elementFactory;
    private final TypeElementVisitor.VisitorKind visitorKind;
    private final DefaultExpressionCompilationContextFactory expressionCompilationContextFactory;
    @Nullable
    private JavaFileManager standardFileManager;
    private final JavaAnnotationMetadataBuilder annotationMetadataBuilder;
    private final JavaElementAnnotationMetadataFactory elementAnnotationMetadataFactory;
    private final JavaNativeElementsHelper nativeElementsHelper;
    private final Filer filer;
    private final Set<String> postponedTypes;
    private boolean visitUnresolvedInterfaces;

    /**
     * The default constructor.
     *
     * @param processingEnv The processing environment
     * @param messager The messager
     * @param elements The elements
     * @param annotationUtils The annotation utils
     * @param types Type types
     * @param modelUtils The model utils
     * @param genericUtils The generic type utils
     * @param filer The filer
     * @param visitorAttributes The attributes
     * @param visitorKind The visitor kind
     * @deprecated No longer needed
     */
    @Deprecated(forRemoval = true, since = "4.3.0")
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
        this(processingEnv, messager, elements, types, modelUtils, filer, visitorAttributes, visitorKind, new HashSet<>());
    }

    /**
     * The default constructor.
     *
     * @param processingEnv The processing environment
     * @param messager The messager
     * @param elements The elements
     * @param types Type types
     * @param modelUtils The model utils
     * @param filer The filer
     * @param visitorAttributes The attributes
     * @param visitorKind The visitor kind
     * @deprecated No longer needed
     */
    @Deprecated(forRemoval = true, since = "4.7.0")
    public JavaVisitorContext(
        ProcessingEnvironment processingEnv,
        Messager messager,
        Elements elements,
        Types types,
        ModelUtils modelUtils,
        Filer filer,
        MutableConvertibleValues<Object> visitorAttributes,
        TypeElementVisitor.VisitorKind visitorKind) {
        this(processingEnv, messager, elements, types, modelUtils, filer, visitorAttributes, visitorKind, Set.of());
    }

    /**
     * The default constructor.
     *
     * @param processingEnv The processing environment
     * @param messager The messager
     * @param elements The elements
     * @param types Type types
     * @param modelUtils The model utils
     * @param filer The filer
     * @param visitorAttributes The attributes
     * @param visitorKind The visitor kind
     * @param postponedTypes The postponed types
     */
    public JavaVisitorContext(
        ProcessingEnvironment processingEnv,
        Messager messager,
        Elements elements,
        Types types,
        ModelUtils modelUtils,
        Filer filer,
        MutableConvertibleValues<Object> visitorAttributes,
        TypeElementVisitor.VisitorKind visitorKind,
        Set<String> postponedTypes) {
        this.messager = messager;
        this.elements = elements;
        this.types = types;
        this.modelUtils = modelUtils;
        this.outputVisitor = new AnnotationProcessingOutputVisitor(filer);
        this.visitorAttributes = visitorAttributes;
        this.processingEnv = processingEnv;
        this.elementFactory = new JavaElementFactory(this);
        this.visitorKind = visitorKind;
        this.nativeElementsHelper = new JavaNativeElementsHelper(elements, types);
        this.annotationMetadataBuilder = new JavaAnnotationMetadataBuilder(elements, messager, modelUtils, nativeElementsHelper, this);
        this.elementAnnotationMetadataFactory = new JavaElementAnnotationMetadataFactory(false, this.annotationMetadataBuilder);
        this.expressionCompilationContextFactory = new DefaultExpressionCompilationContextFactory(this);
        this.filer = filer;
        this.postponedTypes = postponedTypes;
    }

    @Override
    public Language getLanguage() {
        return Language.JAVA;
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

    /**
     * @return True if the unresolved interfaces should be visited
     * @since 4.9
     */
    public boolean isVisitUnresolvedInterfaces() {
        return visitUnresolvedInterfaces;
    }

    /**
     * @param visitUnresolvedInterfaces True to visit unresolved interfaces
     * @since 4.9
     */
    public void setVisitUnresolvedInterfaces(boolean visitUnresolvedInterfaces) {
        this.visitUnresolvedInterfaces = visitUnresolvedInterfaces;
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
        try {
            TypeElement typeElement = elements.getTypeElement(name);
            if (typeElement == null) {
                // maybe inner class?
                typeElement = elements.getTypeElement(name.replace('$', '.'));
            }
            return Optional.ofNullable(typeElement)
                .map(typeElement1 -> elementFactory.newClassElement(typeElement1, annotationMetadataFactory));
        } catch (RuntimeException e) {
            // can throw exception on Eclipse JDT which is brain dead
            return Optional.empty();
        }
    }

    @Override
    public @NonNull
    ClassElement[] getClassElements(@NonNull String aPackage, @NonNull String... stereotypes) {
        ArgumentUtils.requireNonNull("aPackage", aPackage);
        ArgumentUtils.requireNonNull("stereotypes", stereotypes);
        final PackageElement packageElement = elements.getPackageElement(aPackage);
        if (packageElement != null) {
            var classElements = new ArrayList<ClassElement>();

            populateClassElements(stereotypes, packageElement, classElements);
            return classElements.toArray(ClassElement.ZERO_CLASS_ELEMENTS);
        }
        return ClassElement.ZERO_CLASS_ELEMENTS;
    }

    @NonNull
    @Override
    public JavaElementFactory getElementFactory() {
        return elementFactory;
    }

    @NonNull
    @Override
    public JavaElementAnnotationMetadataFactory getElementAnnotationMetadataFactory() {
        return elementAnnotationMetadataFactory;
    }

    @NonNull
    @Override
    public ExpressionCompilationContextFactory getExpressionCompilationContextFactory() {
        return expressionCompilationContextFactory;
    }

    @NonNull
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

    /**
     * Print warning message.
     *
     * @param message The message
     * @param element The element
     * @since 4.0.0
     */
    public void warn(String message, @Nullable Element element) {
        if (element == null) {
            messager.printMessage(Diagnostic.Kind.WARNING, message);
        } else {
            messager.printMessage(Diagnostic.Kind.WARNING, message, element);
        }
    }

    private void printMessage(String message, Diagnostic.Kind kind, @Nullable io.micronaut.inject.ast.Element element) {
        if (StringUtils.isNotEmpty(message)) {
            if (element instanceof BeanElement beanElement) {
                element = beanElement.getDeclaringClass();
            }
            if (element instanceof AbstractJavaElement abstractJavaElement) {
                Element el = abstractJavaElement.getNativeType().element();
                messager.printMessage(kind, message, el);
            } else {
                messager.printMessage(kind, message);
            }
        }
    }

    private void checkForPostponedOriginalElement(io.micronaut.inject.ast.Element originatingElement) {
        if (originatingElement != null && postponedTypes.contains(originatingElement.getName())) {
            throw new ElementPostponedToNextRoundException(originatingElement);
        }
    }

    private void checkForPostponedOriginalElements(io.micronaut.inject.ast.Element[] originatingElements) {
        if (originatingElements != null) {
            for (io.micronaut.inject.ast.Element originatingElement : originatingElements) {
                checkForPostponedOriginalElement(originatingElement);
            }
        }
    }

    @Override
    public OutputStream visitClass(String classname, @Nullable io.micronaut.inject.ast.Element originatingElement) throws IOException {
        checkForPostponedOriginalElement(originatingElement);
        return outputVisitor.visitClass(classname, new io.micronaut.inject.ast.Element[] {originatingElement});
    }

    @Override
    public OutputStream visitClass(String classname, io.micronaut.inject.ast.Element... originatingElements) throws IOException {
        checkForPostponedOriginalElements(originatingElements);
        return outputVisitor.visitClass(classname, originatingElements);
    }

    @Override
    public void visitServiceDescriptor(String type, String classname) {
        outputVisitor.visitServiceDescriptor(type, classname);
    }

    @Override
    public void visitServiceDescriptor(String type, String classname, io.micronaut.inject.ast.Element originatingElement) {
        checkForPostponedOriginalElement(originatingElement);
        outputVisitor.visitServiceDescriptor(type, classname, originatingElement);
    }

    @Override
    public Optional<GeneratedFile> visitMetaInfFile(String path, io.micronaut.inject.ast.Element... originatingElements) {
        checkForPostponedOriginalElements(originatingElements);
        return outputVisitor.visitMetaInfFile(path, originatingElements);
    }

    @Override
    public Optional<GeneratedFile> visitGeneratedFile(String path) {
        return outputVisitor.visitGeneratedFile(path);
    }

    @Override
    public Optional<GeneratedFile> visitGeneratedFile(String path, io.micronaut.inject.ast.Element... originatingElements) {
        checkForPostponedOriginalElements(originatingElements);
        return outputVisitor.visitGeneratedFile(path, originatingElements);
    }

    @Override
    public Optional<GeneratedFile> visitGeneratedSourceFile(String packageName, String fileNameWithoutExtension, io.micronaut.inject.ast.Element... originatingElements) {
        checkForPostponedOriginalElements(originatingElements);
        return outputVisitor.visitGeneratedSourceFile(packageName, fileNameWithoutExtension, originatingElements);
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
     * @deprecated No longer used
     */
    @Deprecated(forRemoval = true, since = "4.3.0")
    public AnnotationUtils getAnnotationUtils() {
        return new AnnotationUtils(processingEnv, elements, messager, types, modelUtils, getGenericUtils(), filer);
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
     * @deprecated No longer used
     */
    @Deprecated(forRemoval = true, since = "4.3.0")
    public GenericUtils getGenericUtils() {
        return new GenericUtils(elements, types, modelUtils);
    }

    /**
     * @return The elements helper
     */
    public JavaNativeElementsHelper getNativeElementsHelper() {
        return nativeElementsHelper;
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
        boolean includeAll = Arrays.equals(stereotypes, new String[] {"*"});
        for (Element enclosedElement : enclosedElements) {
            populateClassElements(stereotypes, includeAll, packageElement, enclosedElement, classElements);
        }
    }

    private void populateClassElements(@NonNull String[] stereotypes, boolean includeAll, PackageElement packageElement, Element enclosedElement, List<ClassElement> classElements) {
        if (enclosedElement instanceof TypeElement element) {
            JavaClassElement classElement = elementFactory.newClassElement(element, elementAnnotationMetadataFactory);
            if ((includeAll || Arrays.stream(stereotypes).anyMatch(classElement::hasStereotype)) && !classElement.isAbstract()) {
                classElements.add(classElement);
            }
            List<? extends Element> nestedElements = enclosedElement.getEnclosedElements();
            for (Element nestedElement : nestedElements) {
                populateClassElements(stereotypes, includeAll, packageElement, nestedElement, classElements);
            }
        } else if (enclosedElement instanceof PackageElement element) {
            populateClassElements(stereotypes, element, classElements);
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
        final var current = new ArrayList<>(beanDefinitionBuilders);
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
            type instanceof AbstractAnnotationElement aae ? aae.getElementAnnotationMetadataFactory() : elementAnnotationMetadataFactory,
            this
        );
    }
}
