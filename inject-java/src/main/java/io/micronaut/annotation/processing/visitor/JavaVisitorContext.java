/*
 * Copyright 2017-2019 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
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
import io.micronaut.annotation.processing.ModelUtils;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.value.MutableConvertibleValues;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.util.VisitorContextUtils;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.inject.writer.GeneratedFile;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
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
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The visitor context when visiting Java code.
 *
 * @author James Kleeh
 * @since 1.0
 */
@Internal
public class JavaVisitorContext implements VisitorContext {

    private final Messager messager;
    private final Elements elements;
    private final AnnotationUtils annotationUtils;
    private final Types types;
    private final ModelUtils modelUtils;
    private final AnnotationProcessingOutputVisitor outputVisitor;
    private final MutableConvertibleValues<Object> visitorAttributes;
    private final GenericUtils genericUtils;
    private final ProcessingEnvironment processingEnv;
    private @Nullable
    JavaFileManager standardFileManager;

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
            MutableConvertibleValues<Object> visitorAttributes) {
        this.messager = messager;
        this.elements = elements;
        this.annotationUtils = annotationUtils;
        this.types = types;
        this.modelUtils = modelUtils;
        this.genericUtils = genericUtils;
        this.outputVisitor = new AnnotationProcessingOutputVisitor(filer);
        this.visitorAttributes = visitorAttributes;
        this.processingEnv = processingEnv;
    }

    @Nonnull
    @Override
    public Iterable<URL> getClasspathResources(@Nonnull String path) {
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
        TypeElement typeElement = elements.getTypeElement(name);
        return Optional.ofNullable(typeElement).map(typeElement1 ->
                new JavaClassElement(typeElement1, annotationUtils.getAnnotationMetadata(typeElement1), this, Collections.emptyMap())
        );
    }

    @Override
    public @Nonnull
    ClassElement[] getClassElements(@Nonnull String aPackage, @Nonnull String... stereotypes) {
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
            if (element != null) {
                Element el = (Element) element.getNativeType();
                messager.printMessage(kind, message, el);
            } else {
                messager.printMessage(kind, message);
            }
        }
    }

    @Override
    public OutputStream visitClass(String classname) throws IOException {
        return outputVisitor.visitClass(classname);
    }

    @Override
    public void visitServiceDescriptor(String type, String classname) {
        outputVisitor.visitServiceDescriptor(type, classname);
    }

    @Override
    public Optional<GeneratedFile> visitMetaInfFile(String path) {
        return outputVisitor.visitMetaInfFile(path);
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

    private void populateClassElements(@Nonnull String[] stereotypes, PackageElement packageElement, List<ClassElement> classElements) {
        final List<? extends Element> enclosedElements = packageElement.getEnclosedElements();

        for (Element enclosedElement : enclosedElements) {
            if (enclosedElement instanceof TypeElement) {
                final AnnotationMetadata annotationMetadata = annotationUtils.getAnnotationMetadata(enclosedElement);
                if (Arrays.stream(stereotypes).anyMatch(annotationMetadata::hasStereotype)) {
                    JavaClassElement classElement = new JavaClassElement(
                            (TypeElement) enclosedElement,
                            annotationMetadata,
                            this
                    );

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
}
