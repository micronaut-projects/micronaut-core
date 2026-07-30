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
import io.micronaut.annotation.processing.visitor.JavaVisitorContext;
import io.micronaut.aop.Around;
import io.micronaut.aop.InterceptorBinding;
import io.micronaut.aop.runtime.RuntimeProxy;
import io.micronaut.context.annotation.Mixin;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.visitor.VisitorUtils;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.Generated;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.io.service.SoftServiceLoader;
import io.micronaut.core.order.OrderUtil;
import io.micronaut.core.util.StringUtils;
import io.micronaut.core.version.VersionUtils;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ConstructorElement;
import io.micronaut.inject.ast.Element;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.ast.EnumConstantElement;
import io.micronaut.inject.ast.FieldElement;
import io.micronaut.inject.ast.MemberElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.PropertyElement;
import io.micronaut.inject.processing.ProcessingException;
import io.micronaut.inject.processing.definition.DefaultElementBeanDefinitionBuilderFactory;
import io.micronaut.inject.processing.definition.OutputObjectDef;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.TypeElementQuery;
import io.micronaut.inject.writer.AbstractBeanDefinitionBuilder;
import io.micronaut.inject.writer.ByteCodeWriterUtils;
import io.micronaut.inject.writer.OriginatingElements;
import io.micronaut.python.processing.PythonProcessingEnvironment;
import io.micronaut.sourcegen.model.ObjectDef;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Runs Micronaut type element visitors against Python class elements.
 */
@Experimental
public final class PythonTypeElementVisitorProcessor {
    private final ClassLoader classLoader;
    private final TypeElementVisitor.VisitorKind visitorKind;

    private Collection<? extends TypeElementVisitor<?, ?>> typeElementVisitors;
    private List<LoadedVisitor> loadedVisitors;

    public PythonTypeElementVisitorProcessor(ClassLoader classLoader) {
        this(classLoader, null);
    }

    /**
     * @param classLoader The visitor class loader
     * @param visitorKind The visitor kind to execute
     */
    @Internal
    public PythonTypeElementVisitorProcessor(ClassLoader classLoader,
                                             TypeElementVisitor.VisitorKind visitorKind) {
        this.classLoader = classLoader;
        this.visitorKind = visitorKind;
    }

    /**
     * Initialise the processor.
     *
     * @param environment The processing environment
     */
    public synchronized void init(PythonProcessingEnvironment environment) {

        Collection<? extends TypeElementVisitor<?, ?>> typeElementVisitors = findTypeElementVisitors();

        this.loadedVisitors = new ArrayList<>(typeElementVisitors.size());

        for (TypeElementVisitor<?, ?> visitor : typeElementVisitors) {
            if (isSupportedVisitorKind(visitor.getVisitorKind())) {
                try {
                    loadedVisitors.add(new LoadedVisitor(visitor));
                } catch (TypeNotPresentException | NoClassDefFoundError e) {
                    // ignored, means annotations referenced are not on the classpath
                }
            }
        }

        OrderUtil.reverseSort(loadedVisitors);

        PythonVisitorContext visitorContext = environment.visitorContext();
        for (LoadedVisitor loadedVisitor : loadedVisitors) {
            try {
                loadedVisitor.getVisitor().start(visitorContext);
            } catch (Throwable e) {
                visitorContext.fail(String.format("Error initializing type visitor [%s]: %s", loadedVisitor.getVisitor(), e.getMessage()), null);
            }
        }
    }

    /**
     * Process the given elements.
     *
     * @param environment The processing environment
     */
    public void process(PythonProcessingEnvironment environment) {
        process(environment, ignored -> true, true, true);
    }

    /**
     * Processes a selected set of source elements for one visitor kind.
     *
     * @param environment The processing environment
     * @param sourceFilter The source element filter
     * @param applyMixins Whether mixins should be applied
     * @param writeAssociatedBeans Whether associated bean definitions should be written
     */
    @Internal
    public void process(PythonProcessingEnvironment environment,
                        Predicate<ClassElement> sourceFilter,
                        boolean applyMixins,
                        boolean writeAssociatedBeans) {
        PythonVisitorContext pythonVisitorContext = environment.visitorContext();
        for (LoadedVisitor loadedVisitor : loadedVisitors) {
            try {
                loadedVisitor.getVisitor().start(pythonVisitorContext);
            } catch (Throwable e) {
                failVisitor(pythonVisitorContext, loadedVisitor, "start", e);
            }
        }

        if (applyMixins) {
            applyMixins(environment, pythonVisitorContext, sourceFilter);
        }
        List<ClassElement> allClasses = collectClassElements(environment, pythonVisitorContext, sourceFilter);
        for (LoadedVisitor loadedVisitor : loadedVisitors) {
            for (ClassElement element : allClasses) {
                if (element.hasAnnotation(Generated.class)) {
                    continue;
                }
                if (loadedVisitor.matchesClass(element)) {
                    annotatePythonAopProxy(element);
                    try {
                        visitClass(loadedVisitor, element, pythonVisitorContext);
                    } catch (Throwable e) {
                        failVisitor(pythonVisitorContext, loadedVisitor, "visitClass", e);
                    }
                }
            }
        }

        for (LoadedVisitor loadedVisitor : loadedVisitors) {
            try {
                loadedVisitor.getVisitor().finish(pythonVisitorContext);
            } catch (Throwable e) {
                failVisitor(pythonVisitorContext, loadedVisitor, "finish", e);
            }
        }
        if (writeAssociatedBeans) {
            writeAssociatedBeanDefinitions(pythonVisitorContext);
        }
    }

    private static void failVisitor(PythonVisitorContext visitorContext, LoadedVisitor loadedVisitor, String phase, Throwable throwable) {
        if (throwable instanceof ProcessingException processingException) {
            throw processingException;
        }
        String message = throwable.getMessage();
        visitorContext.fail(String.format(
            "TypeElementVisitor [%s] failed during %s: %s",
            loadedVisitor.getVisitor().getClass().getName(),
            phase,
            message == null ? throwable.getClass().getSimpleName() : message
        ), null);
    }

    private void applyMixins(PythonProcessingEnvironment environment,
                             PythonVisitorContext pythonVisitorContext,
                             Predicate<ClassElement> sourceFilter) {
        for (ClassElement mixin : collectPythonClassElements(environment, sourceFilter)) {
            AnnotationValue<Mixin> mixinAnnotation = mixin.getAnnotation(Mixin.class);
            if (mixinAnnotation == null) {
                continue;
            }
            String target = mixinAnnotation.stringValue("target")
                .orElse(mixinAnnotation.stringValue().orElse(null));
            if (target == null || Object.class.getName().equals(target)) {
                continue;
            }
            ClassElement mixinTarget = pythonVisitorContext.getClassElement(target).orElse(null);
            if (mixinTarget == null) {
                pythonVisitorContext.warn("Cannot access class: " + target, mixin);
                continue;
            }
            VisitorUtils.applyMixin(mixinAnnotation, mixin, mixinTarget, pythonVisitorContext);
            copyPythonPropertyMixinAnnotations(mixinAnnotation, mixin, mixinTarget);
        }
    }

    private List<ClassElement> collectPythonClassElements(PythonProcessingEnvironment environment,
                                                          Predicate<ClassElement> sourceFilter) {
        Map<String, ClassElement> classes = environment.classes();
        Map<String, ClassElement> scripts = environment.scripts();
        List<ClassElement> allClasses = new ArrayList<>(classes.size() + scripts.size());
        classes.values().stream().filter(sourceFilter).forEach(allClasses::add);
        scripts.values().stream().filter(sourceFilter).forEach(allClasses::add);
        return allClasses;
    }

    private List<ClassElement> collectClassElements(PythonProcessingEnvironment environment,
                                                    PythonVisitorContext pythonVisitorContext,
                                                    Predicate<ClassElement> sourceFilter) {
        List<ClassElement> allClasses = collectPythonClassElements(environment, sourceFilter);
        Map<String, ClassElement> uniqueClasses = new LinkedHashMap<>();
        allClasses.forEach(classElement -> uniqueClasses.putIfAbsent(classElement.getName(), classElement));
        for (ClassElement classElement : new ArrayList<>(allClasses)) {
            for (ClassElement importedElement : VisitorUtils.collectImportedElements(classElement, pythonVisitorContext)) {
                // Imported-element collection can rediscover Python classes that are already part of
                // the source environment. Type visitors may register associated beans, so visiting the
                // same class twice would generate duplicate bean definitions for the same association.
                if (!(importedElement instanceof AbstractPythonClassElement) || sourceFilter.test(importedElement)) {
                    uniqueClasses.putIfAbsent(importedElement.getName(), importedElement);
                }
            }
        }
        return new ArrayList<>(uniqueClasses.values());
    }

    private void copyPythonPropertyMixinAnnotations(AnnotationValue<Mixin> mixinAnnotation, ClassElement mixin, ClassElement mixinTarget) {
        Map<String, PropertyElement> targetProperties = mixinTarget.getBeanProperties()
            .stream()
            .collect(Collectors.toMap(PropertyElement::getName, property -> property, (left, right) -> left));
        for (PropertyElement mixinProperty : mixin.getBeanProperties()) {
            PropertyElement targetProperty = targetProperties.get(mixinProperty.getName());
            if (targetProperty != null && mixinProperty.getType().equals(targetProperty.getType())) {
                copyAnnotations(mixinProperty, targetProperty, mixinAnnotation);
            }
        }
    }

    private void copyAnnotations(AnnotationMetadata source, Element target, AnnotationValue<Mixin> mixinAnnotation) {
        for (String annotationName : source.getAnnotationNames()) {
            if (shouldCopyMixinAnnotation(annotationName, mixinAnnotation)) {
                AnnotationValue<?> annotation = source.getAnnotation(annotationName);
                if (annotation != null) {
                    target.annotate(annotation);
                }
            }
        }
    }

    private boolean shouldCopyMixinAnnotation(String annotationName, AnnotationValue<Mixin> mixinAnnotation) {
        if (Mixin.Filter.class.getName().equals(annotationName)
            || Mixin.class.getName().equals(annotationName)
            || Introspected.Property.class.getName().equals(annotationName)) {
            return false;
        }
        String[] includedAnnotations = mixinAnnotation.stringValues("includeAnnotations");
        if (includedAnnotations.length > 0) {
            for (String includedAnnotation : includedAnnotations) {
                if (annotationName.startsWith(includedAnnotation)) {
                    return true;
                }
            }
            return false;
        }
        for (String excludedAnnotation : mixinAnnotation.stringValues("excludeAnnotations")) {
            if (annotationName.startsWith(excludedAnnotation)) {
                return false;
            }
        }
        return true;
    }

    private void annotatePythonAopProxy(ClassElement element) {
        if (!(element instanceof AbstractPythonClassElement) || !isAopProxy(element)) {
            return;
        }
        if (element.hasStereotype(Around.class)) {
            element.annotate(Around.class, builder -> builder.member("proxyTarget", true));
        }
        element.annotate(RuntimeProxy.class, builder ->
            builder.value("io.micronaut.context.python.aop.PythonProxyCreator")
                .member("proxyTarget", true)
        );
    }

    private void writeAssociatedBeanDefinitions(PythonVisitorContext pythonVisitorContext) {
        JavaVisitorContext javaVisitorContext = pythonVisitorContext.getJavaVisitorContext();
        if (javaVisitorContext == null) {
            return;
        }
        List<AbstractBeanDefinitionBuilder> beanElementBuilders = javaVisitorContext.getBeanElementBuilders();
        if (beanElementBuilders.isEmpty()) {
            return;
        }
        try {
            DefaultElementBeanDefinitionBuilderFactory beanDefinitionBuilderFactory = new DefaultElementBeanDefinitionBuilderFactory(pythonVisitorContext);
            for (OutputObjectDef outputObjectDef : AbstractBeanDefinitionBuilder.build(beanElementBuilders, beanDefinitionBuilderFactory)) {
                write(outputObjectDef, pythonVisitorContext);
            }
        } catch (IOException e) {
            String message = e.getMessage();
            pythonVisitorContext.fail("Unexpected error: " + (message != null ? message : e.getClass().getSimpleName()), null);
        }
    }

    private void write(OutputObjectDef outputObjectDef, PythonVisitorContext pythonVisitorContext) throws IOException {
        ObjectDef objectDef = outputObjectDef.objectDef();
        Class<?> serviceClass = outputObjectDef.serviceClass();
        OriginatingElements originatingElements = outputObjectDef.originatingElements();
        if (serviceClass != null) {
            pythonVisitorContext.visitServiceDescriptor(serviceClass, objectDef.getName(), originatingElements.getOriginatingElements()[0]);
        }
        try (OutputStream outputStream = pythonVisitorContext.visitClass(objectDef.getName(), originatingElements.getOriginatingElements())) {
            outputStream.write(ByteCodeWriterUtils.writeByteCode(objectDef, pythonVisitorContext));
        }
    }

    private void visitClass(LoadedVisitor loadedVisitor, ClassElement element, PythonVisitorContext pythonVisitorContext) {
        TypeElementVisitor<?, ?> visitor = loadedVisitor.getVisitor();
        TypeElementQuery query = visitor.query();
        visitor.visitClass(element, pythonVisitorContext);

        if (query.includesConstructors()) {
            for (ConstructorElement constructorElement : element.getEnclosedElements(ElementQuery.CONSTRUCTORS)) {
                visitConstructor(loadedVisitor, constructorElement, pythonVisitorContext);
            }
        }

        boolean includesFields = query.includesFields() || query.includesEnumConstants();
        boolean includesMethods = query.includesMethods();
        List<? extends MemberElement> elements;
        if (includesMethods && includesFields) {
            elements = element.getEnclosedElements(ElementQuery.ALL_FIELD_AND_METHODS);
        } else if (includesMethods) {
            elements = element.getEnclosedElements(ElementQuery.ALL_METHODS);
        } else if (includesFields) {
            elements = element.getEnclosedElements(ElementQuery.ALL_FIELDS);
        } else {
            elements = List.of();
        }

        for (MemberElement memberElement : elements) {
            if (memberElement instanceof EnumConstantElement enumConstantElement) {
                if (query.includesEnumConstants()) {
                    visitEnumConstant(loadedVisitor, enumConstantElement, pythonVisitorContext);
                }
            } else if (memberElement instanceof FieldElement fieldElement) {
                if (query.includesFields()) {
                    visitField(loadedVisitor, fieldElement, pythonVisitorContext);
                }
            } else if (memberElement instanceof MethodElement methodElement) {
                if (includesMethods) {
                    visitMethod(loadedVisitor, methodElement, pythonVisitorContext);
                }
            }
        }
    }

    private void visitConstructor(LoadedVisitor visitor, ConstructorElement constructorElement, PythonVisitorContext pythonVisitorContext) {
        if (visitor.matchesElement(constructorElement.getAnnotationMetadata())) {
            visitor.getVisitor().visitConstructor(constructorElement, pythonVisitorContext);
        }
    }

    private void visitMethod(LoadedVisitor visitor, MethodElement methodElement, PythonVisitorContext pythonVisitorContext) {
        if (visitor.matchesElement(methodElement.getAnnotationMetadata())) {
            visitor.getVisitor().visitMethod(methodElement, pythonVisitorContext);
        }
    }

    private void visitEnumConstant(LoadedVisitor visitor, EnumConstantElement enumConstantElement, PythonVisitorContext pythonVisitorContext) {
        if (visitor.matchesElement(enumConstantElement.getAnnotationMetadata())) {
            visitor.getVisitor().visitEnumConstant(enumConstantElement, pythonVisitorContext);
        }
    }

    private void visitField(LoadedVisitor visitor, FieldElement fieldElement, PythonVisitorContext pythonVisitorContext) {
        if (visitor.matchesElement(fieldElement.getAnnotationMetadata())) {
            visitor.getVisitor().visitField(fieldElement, pythonVisitorContext);
        }
    }

    private boolean isAopProxy(ClassElement element) {
        if (element.hasStereotype(InterceptorBinding.class)) {
            return true;
        }
        for (MethodElement method : element.getMethods()) {
            if (method.hasStereotype(InterceptorBinding.class)) {
                return true;
            }
        }
        return false;
    }

    private boolean isSupportedVisitorKind(TypeElementVisitor.VisitorKind visitorKind) {
        return this.visitorKind == null
            ? visitorKind == TypeElementVisitor.VisitorKind.ISOLATING
                || visitorKind == TypeElementVisitor.VisitorKind.AGGREGATING
            : this.visitorKind == visitorKind;
    }

    /**
     * Discovers the {@link TypeElementVisitor} instances that are available.
     *
     * @return A collection of type element visitors.
     */
    @NonNull
    synchronized Collection<? extends TypeElementVisitor<?, ?>> findTypeElementVisitors() {
        if (typeElementVisitors == null) {
            HashSet<String> warnings = new HashSet<>();
            typeElementVisitors = findCoreTypeElementVisitors(warnings);
            for (String visitorWarning : warnings) {
                warning(visitorWarning);
            }
        }
        return typeElementVisitors;
    }

    private void warning(String visitorWarning) {
        System.err.println("WARNING: " + visitorWarning);
    }

    @SuppressWarnings("rawtypes")
    @NonNull
    private Collection<? extends TypeElementVisitor<?, ?>> findCoreTypeElementVisitors(@Nullable Set<String> warnings) {
        List visitors = SoftServiceLoader.load(TypeElementVisitor.class, classLoader)
            .disableFork()
            .collectAll(visitor -> {
                if (!visitor.isEnabled()) {
                    return false;
                }

                final Requires requires = visitor.getClass().getAnnotation(Requires.class);
                if (requires != null) {
                    final Requires.Sdk sdk = requires.sdk();
                    if (sdk == Requires.Sdk.MICRONAUT) {
                        final String version = requires.version();
                        if (StringUtils.isNotEmpty(version) && !VersionUtils.isAtLeastMicronautVersion(version)) {
                            try {
                                if (warnings != null) {
                                    warnings.add("TypeElementVisitor [" + visitor.getClass().getName() + "] will be ignored because Micronaut version [" + VersionUtils.MICRONAUT_VERSION + "] must be at least " + version);
                                }
                                return false;
                            } catch (IllegalArgumentException e) {
                                // shouldn't happen, thrown when invalid version encountered
                            }
                        }
                    }
                }
                return true;
            }).stream()
            .filter(Objects::nonNull)
            .collect(Collectors.toMap(Object::getClass, v -> v, (a, b) -> a)).values().stream().toList();
        if (visitors.isEmpty()) {
            visitors = new ArrayList<>();
            Iterator<ServiceLoader.Provider<TypeElementVisitor>> it = ServiceLoader.load(TypeElementVisitor.class, classLoader).stream().iterator();
            while (it.hasNext()) {
                Class<? extends TypeElementVisitor> type = null;
                try {
                    ServiceLoader.Provider<TypeElementVisitor> provider = it.next();
                    type = provider.type();
                    visitors.add(provider.get());
                } catch (Throwable e) {
                    if (e instanceof VirtualMachineError virtualMachineError) {
                        throw virtualMachineError;
                    } else {
                        if (warnings != null) {
                            warnings.add("Error loading TypeElementVisitor " + (type != null ? type.getSimpleName() : "") + ": " + e.getMessage());
                        }
                    }
                }
            }
        }
        return (Collection<? extends TypeElementVisitor<?, ?>>) visitors;
    }
}
