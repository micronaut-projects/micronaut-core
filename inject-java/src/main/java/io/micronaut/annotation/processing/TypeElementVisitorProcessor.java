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
package io.micronaut.annotation.processing;

import io.micronaut.annotation.processing.visitor.JavaClassElement;
import io.micronaut.annotation.processing.visitor.JavaElementFactory;
import io.micronaut.annotation.processing.visitor.JavaNativeElement;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.visitor.VisitorUtils;
import io.micronaut.core.annotation.NextMajorVersion;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.io.service.SoftServiceLoader;
import io.micronaut.core.order.OrderUtil;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.core.version.VersionUtils;
import io.micronaut.inject.annotation.AbstractAnnotationMetadataBuilder;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ConstructorElement;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.ast.EnumConstantElement;
import io.micronaut.inject.ast.FieldElement;
import io.micronaut.inject.ast.MemberElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.processing.ProcessingException;
import io.micronaut.inject.visitor.ElementPostponedToNextRoundException;
import io.micronaut.inject.visitor.TypeElementQuery;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.inject.writer.AbstractBeanDefinitionBuilder;

import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedOptions;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.micronaut.core.util.StringUtils.EMPTY_STRING;

/**
 * <p>The annotation processed used to execute type element visitors.</p>
 *
 * @author James Kleeh
 * @author graemerocher
 * @since 1.0
 */
@SupportedOptions({
    AbstractInjectAnnotationProcessor.MICRONAUT_PROCESSING_INCREMENTAL,
    AbstractInjectAnnotationProcessor.MICRONAUT_PROCESSING_ANNOTATIONS,
    VisitorContext.MICRONAUT_PROCESSING_PROJECT_DIR,
    VisitorContext.MICRONAUT_PROCESSING_GROUP,
    VisitorContext.MICRONAUT_PROCESSING_MODULE
})
public class TypeElementVisitorProcessor extends AbstractInjectAnnotationProcessor {
    private static final Set<String> VISITOR_WARNINGS;
    private static final Set<String> SUPPORTED_ANNOTATION_NAMES;

    static {

        final var warnings = new HashSet<String>();
        var names = new HashSet<String>();
        for (TypeElementVisitor<?, ?> typeElementVisitor : findCoreTypeElementVisitors(warnings)) {
            final Set<String> supportedAnnotationNames;
            try {
                supportedAnnotationNames = typeElementVisitor.getSupportedAnnotationNames();
            } catch (Throwable e) {
                // ignore if annotations are not on the classpath
                continue;
            }
            if (!supportedAnnotationNames.equals(Collections.singleton("*"))) {
                names.addAll(supportedAnnotationNames);
            }
        }
        SUPPORTED_ANNOTATION_NAMES = names;

        if (warnings.isEmpty()) {
            VISITOR_WARNINGS = Collections.emptySet();
        } else {
            VISITOR_WARNINGS = Collections.unmodifiableSet(warnings);
        }
    }

    private List<LoadedVisitor> loadedVisitors;
    private Collection<? extends TypeElementVisitor<?, ?>> typeElementVisitors;

    /**
     * The visited annotation names.
     *
     * @return The names of all the visited annotations.
     */
    static Set<String> getVisitedAnnotationNames() {
        return SUPPORTED_ANNOTATION_NAMES;
    }

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);

        Collection<? extends TypeElementVisitor<?, ?>> typeElementVisitors = findTypeElementVisitors();

        // set supported options as system properties to keep compatibility
        // in particular for micronaut-openapi
        processingEnv.getOptions().entrySet().stream()
            .filter(entry -> entry.getKey() != null && entry.getKey().startsWith(VisitorContext.MICRONAUT_BASE_OPTION_NAME))
            .forEach(entry -> System.setProperty(entry.getKey(), entry.getValue() == null ? EMPTY_STRING : entry.getValue()));

        this.loadedVisitors = new ArrayList<>(typeElementVisitors.size());

        for (TypeElementVisitor<?, ?> visitor : typeElementVisitors) {
            TypeElementVisitor.VisitorKind visitorKind = visitor.getVisitorKind();
            TypeElementVisitor.VisitorKind incrementalProcessorKind = getIncrementalProcessorKind();

            if (incrementalProcessorKind == visitorKind) {
                try {
                    loadedVisitors.add(new LoadedVisitor(visitor, processingEnv));
                } catch (TypeNotPresentException | NoClassDefFoundError e) {
                    // ignored, means annotations referenced are not on the classpath
                }
            }
        }

        OrderUtil.reverseSort(loadedVisitors);

        for (LoadedVisitor loadedVisitor : loadedVisitors) {
            try {
                loadedVisitor.getVisitor().start(javaVisitorContext);
            } catch (Throwable e) {
                error("Error initializing type visitor [%s]: %s", loadedVisitor.getVisitor(), e.getMessage());
            }
        }
    }

    /**
     * Does this process have any visitors.
     *
     * @return True if visitors are present.
     */
    protected boolean hasVisitors() {
        for (TypeElementVisitor<?, ?> typeElementVisitor : findTypeElementVisitors()) {
            if (typeElementVisitor.getVisitorKind() == getVisitorKind()) {
                return true;
            }
        }
        return false;
    }

    /**
     * @return The loaded visitors.
     */
    protected List<LoadedVisitor> getLoadedVisitors() {
        return loadedVisitors;
    }

    /**
     * @return The incremental processor type.
     * @see #GRADLE_PROCESSING_AGGREGATING
     * @see #GRADLE_PROCESSING_ISOLATING
     */
    protected TypeElementVisitor.VisitorKind getIncrementalProcessorKind() {
        String type = getIncrementalProcessorType();
        if (type.equals(GRADLE_PROCESSING_AGGREGATING)) {
            return TypeElementVisitor.VisitorKind.AGGREGATING;
        }
        return TypeElementVisitor.VisitorKind.ISOLATING;
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        if (loadedVisitors.isEmpty()) {
            return Collections.emptySet();
        } else {
            return super.getSupportedAnnotationTypes();
        }
    }

    @Override
    public Set<String> getSupportedOptions() {
        Stream<String> baseOption = super.getSupportedOptions().stream();
        Collection<? extends TypeElementVisitor<?, ?>> typeElementVisitors = findTypeElementVisitors();
        Stream<String> visitorsOptions = typeElementVisitors
            .stream()
            .map(TypeElementVisitor::getSupportedOptions)
            .flatMap(Collection::stream);
        Stream<String> visitorsAnnotationsOptions = typeElementVisitors
            .stream()
            .filter(tev -> tev.getClass().isAnnotationPresent(SupportedOptions.class))
            .map(TypeElementVisitor::getClass)
            .map(cls -> (SupportedOptions) cls.getAnnotation(SupportedOptions.class))
            .flatMap((SupportedOptions supportedOptions) -> Arrays.stream(supportedOptions.value()));
        return Stream.of(baseOption, visitorsAnnotationsOptions, visitorsOptions)
            .flatMap(Stream::sequential)
            .collect(Collectors.toSet());
    }

    @NextMajorVersion("`roundEnv.getRootElements()` should be removed in Micronaut 4. " +
        "It should not be possible to process elements without at least one annotation present and this call breaks that assumption")
    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (!loadedVisitors.isEmpty() && !processingGeneratedAnnotation(annotations)) {

            TypeElement groovyObjectTypeElement = elementUtils.getTypeElement("groovy.lang.GroovyObject");
            TypeMirror groovyObjectType = groovyObjectTypeElement != null ? groovyObjectTypeElement.asType() : null;
            Predicate<TypeElement> notGroovyObject = typeElement -> groovyObjectType == null || !typeUtils.isAssignable(typeElement.asType(), groovyObjectType);

            var elements = new LinkedHashSet<TypeElement>();

            for (TypeElement annotation : annotations) {
                modelUtils.resolveTypeElements(
                    roundEnv.getElementsAnnotatedWith(annotation)
                ).filter(notGroovyObject).forEach(elements::add);
            }

            modelUtils.resolveTypeElements(
                roundEnv.getRootElements()
            ).filter(notGroovyObject).forEach(elements::add);

            for (Object nativeType : postponedTypes.values()) {
                if (nativeType instanceof Element element) {
                    AbstractAnnotationMetadataBuilder.CachedAnnotationMetadata cachedAnnotationMetadata = javaVisitorContext.getAnnotationMetadataBuilder().lookupOrBuildForType(element);
                    if (!cachedAnnotationMetadata.wasCleared()) {
                        AbstractAnnotationMetadataBuilder.clearMutated(nativeType);
                        cachedAnnotationMetadata = javaVisitorContext.getAnnotationMetadataBuilder().lookupOrBuildForType(element);
                        javaVisitorContext.getNativeElementsHelper().cleanupForClass(nativeType);
                        cachedAnnotationMetadata.markCleared();
                    }
                }
            }
            postponedTypes.keySet().stream().map(elementUtils::getTypeElement).filter(Objects::nonNull).forEach(elements::add);
            postponedTypes.clear();

            if (!elements.isEmpty()) {

                JavaElementFactory elementFactory = javaVisitorContext.getElementFactory();
                JavaElementAnnotationMetadataFactory elementAnnotationMetadataFactory = javaVisitorContext.getElementAnnotationMetadataFactory();

                // The visitor X with a higher priority should process elements of A before
                // the visitor Y which is processing elements of B but also using elements A

                // Micronaut Data use-case: EntityMapper with a higher priority needs to process entities first
                // before RepositoryMapper is going to process repositories and read entities

                List<JavaClassElement> javaClassElements = elements.stream()
                    .map(typeElement -> elementFactory.newSourceClassElement(typeElement, elementAnnotationMetadataFactory))
                    .collect(Collectors.toCollection(() -> new ArrayList<>(elements.size())));
                List<ClassElement> extraClasses = new ArrayList<>();
                for (JavaClassElement javaClassElement : new ArrayList<>(javaClassElements)) {
                    try {
                        extraClasses.addAll(VisitorUtils.collectImportedElements(javaClassElement, javaVisitorContext));
                        javaClassElements.addAll((Collection) extraClasses);
                    } catch (PostponeToNextRoundException e) {
                        postponeElement(javaClassElement, e.getNativeErrorElement(), e);
                    } catch (ElementPostponedToNextRoundException e) {
                        Element element = PostponeToNextRoundException.resolvedFailedElement(e.getOriginatingElement());
                        postponeElement(javaClassElement, element, e);
                    }
                }

                for (LoadedVisitor loadedVisitor : loadedVisitors) {
                    for (JavaClassElement javaClassElement : javaClassElements) {
                        try {
                            if (loadedVisitor.matchesClass(javaClassElement)) {
                                visitClass(loadedVisitor, javaClassElement);
                            }
                        } catch (ProcessingException e) {
                            var originatingElement = (JavaNativeElement) e.getOriginatingElement();
                            if (originatingElement == null) {
                                originatingElement = javaClassElement.getNativeType();
                            }
                            error(originatingElement.element(), e.getMessage());
                        } catch (PostponeToNextRoundException e) {
                            postponeElement(javaClassElement, e.getNativeErrorElement(), e);
                        } catch (ElementPostponedToNextRoundException e) {
                            Element element = PostponeToNextRoundException.resolvedFailedElement(e.getOriginatingElement());
                            postponeElement(javaClassElement, element, e);
                        }
                    }
                }
            }

            for (LoadedVisitor loadedVisitor : loadedVisitors) {
                try {
                    loadedVisitor.getVisitor().finish(javaVisitorContext);
                } catch (Throwable e) {
                    var stackTraceWriter = new StringWriter();
                    e.printStackTrace(new PrintWriter(stackTraceWriter));

                    error("Error finalizing type visitor [%s]: %s\n%s",
                        loadedVisitor.getVisitor(), e.getMessage(), stackTraceWriter);
                }
            }
        }

        final List<AbstractBeanDefinitionBuilder> beanDefinitionBuilders = javaVisitorContext.getBeanElementBuilders();
        if (CollectionUtils.isNotEmpty(beanDefinitionBuilders)) {
            try {
                AbstractBeanDefinitionBuilder.writeBeanDefinitionBuilders(classWriterOutputVisitor, beanDefinitionBuilders);
            } catch (IOException e) {
                // raise a compile error
                String message = e.getMessage();
                error("Unexpected error: %s", message != null ? message : e.getClass().getSimpleName());
            }
        }

        if (roundEnv.processingOver()) {
            javaVisitorContext.finish();
        }
        return false;
    }

    private <T extends Throwable> void postponeElement(JavaClassElement javaClassElement, Element originalElement, T e) throws T {
        if (originalElement == null) {
            // should never happen.
            throw e;
        }
        postponedTypes.put(javaClassElement.getCanonicalName(), originalElement);
    }

    private void visitClass(LoadedVisitor visitor, JavaClassElement classElement) {
        TypeElementQuery query = visitor.getVisitor().query();

        try {
            javaVisitorContext.setVisitUnresolvedInterfaces(query.visitsUnresolvedInterfaces());

            visitor.getVisitor().visitClass(classElement, javaVisitorContext);

            if (query.includesConstructors()) {
                for (ConstructorElement constructorElement : classElement.getSourceEnclosedElements(ElementQuery.CONSTRUCTORS)) {
                    visitConstructor(visitor, constructorElement);
                }
            }
            boolean includesFields = query.includesFields() || query.includesEnumConstants();
            boolean includesMethods = query.includesMethods();
            List<? extends MemberElement> elements;
            if (includesMethods && includesFields) {
                elements = classElement.getSourceEnclosedElements(ElementQuery.ALL_FIELD_AND_METHODS);
            } else if (includesMethods) {
                elements = classElement.getSourceEnclosedElements(ElementQuery.ALL_METHODS);
            } else if (includesFields) {
                elements = classElement.getSourceEnclosedElements(ElementQuery.ALL_FIELDS);
            } else {
                elements = List.of();
            }
            for (MemberElement memberElement : elements) {
                if (memberElement instanceof EnumConstantElement enumConstantElement) {
                    if (query.includesEnumConstants()) {
                        visitEnumConstant(visitor, enumConstantElement);
                    }
                } else if (memberElement instanceof FieldElement fieldElement) {
                    if (query.includesFields()) {
                        visitField(visitor, fieldElement);
                    }
                } else if (memberElement instanceof MethodElement methodElement) {
                    if (includesMethods) {
                        visitMethod(visitor, methodElement);
                    }
                } else {
                    throw new IllegalStateException("Unknown element: " + memberElement);
                }
            }
        } finally {
            javaVisitorContext.setVisitUnresolvedInterfaces(false);
        }
    }

    private void visitConstructor(LoadedVisitor visitor, ConstructorElement constructorElement) {
        if (visitor.matchesElement(constructorElement)) {
            visitor.getVisitor().visitConstructor(constructorElement, javaVisitorContext);
        }
    }

    private void visitMethod(LoadedVisitor visitor, MethodElement methodElement) {
        if (visitor.matchesElement(methodElement)) {
            visitor.getVisitor().visitMethod(methodElement, javaVisitorContext);
        }
    }

    private void visitEnumConstant(LoadedVisitor visitor, EnumConstantElement enumConstantElement) {
        if (visitor.matchesElement(enumConstantElement)) {
            visitor.getVisitor().visitEnumConstant(enumConstantElement, javaVisitorContext);
        }
    }

    private void visitField(LoadedVisitor visitor, FieldElement fieldElement) {
        if (visitor.matchesElement(fieldElement)) {
            visitor.getVisitor().visitField(fieldElement, javaVisitorContext);
        }
    }

    /**
     * Discovers the {@link TypeElementVisitor} instances that are available.
     *
     * @return A collection of type element visitors.
     */
    @NonNull
    protected synchronized Collection<? extends TypeElementVisitor<?, ?>> findTypeElementVisitors() {
        if (typeElementVisitors == null) {
            for (String visitorWarning : VISITOR_WARNINGS) {
                warning(visitorWarning);
            }
            typeElementVisitors = findCoreTypeElementVisitors(null);
        }
        return typeElementVisitors;
    }

    @NonNull
    private static Collection<? extends TypeElementVisitor<?, ?>> findCoreTypeElementVisitors(@Nullable Set<String> warnings) {
        return SoftServiceLoader.load(TypeElementVisitor.class, TypeElementVisitorProcessor.class.getClassLoader())
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
            .<TypeElementVisitor<?, ?>>map(e -> e)
            // remove duplicate classes
            .collect(Collectors.toMap(Object::getClass, v -> v, (a, b) -> a)).values();
    }
}
