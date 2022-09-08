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
import io.micronaut.annotation.processing.visitor.LoadedVisitor;
import io.micronaut.aop.Introduction;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Generated;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.io.service.SoftServiceLoader;
import io.micronaut.core.order.OrderUtil;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.core.version.VersionUtils;
import io.micronaut.inject.ProcessingException;
import io.micronaut.inject.ast.ConstructorElement;
import io.micronaut.inject.ast.EnumConstantElement;
import io.micronaut.inject.ast.FieldElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.processing.JavaModelUtils;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.inject.writer.AbstractBeanDefinitionBuilder;

import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedOptions;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementScanner8;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static javax.lang.model.element.ElementKind.FIELD;

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
    private static final SoftServiceLoader<TypeElementVisitor> SERVICE_LOADER = SoftServiceLoader.load(TypeElementVisitor.class, TypeElementVisitorProcessor.class.getClassLoader()).disableFork();
    private static final Set<String> VISITOR_WARNINGS;
    private static final Set<String> SUPPORTED_ANNOTATION_NAMES;

    static {

        final HashSet<String> warnings = new HashSet<>();
        Set<String> names = new HashSet<>();
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
    private Collection<TypeElementVisitor> typeElementVisitors;

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

        this.typeElementVisitors = findTypeElementVisitors();

        // set supported options as system properties to keep compatibility
        // in particular for micronaut-openapi
        processingEnv.getOptions().entrySet().stream()
            .filter(entry -> entry.getKey() != null && entry.getKey().startsWith(VisitorContext.MICRONAUT_BASE_OPTION_NAME))
            .forEach(entry -> System.setProperty(entry.getKey(), entry.getValue() == null ? "" : entry.getValue()));

        this.loadedVisitors = new ArrayList<>(typeElementVisitors.size());

        for (TypeElementVisitor<?, ?> visitor : typeElementVisitors) {
            TypeElementVisitor.VisitorKind visitorKind = visitor.getVisitorKind();
            TypeElementVisitor.VisitorKind incrementalProcessorKind = getIncrementalProcessorKind();

            if (incrementalProcessorKind == visitorKind) {
                try {
                    loadedVisitors.add(new LoadedVisitor(visitor, genericUtils, processingEnv));
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
        for (TypeElementVisitor<?, ?> typeElementVisitor : typeElementVisitors) {
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

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (!loadedVisitors.isEmpty() && !(annotations.size() == 1
            && Generated.class.getName().equals(annotations.iterator().next().getQualifiedName().toString()))) {

            TypeElement groovyObjectTypeElement = elementUtils.getTypeElement("groovy.lang.GroovyObject");
            TypeMirror groovyObjectType = groovyObjectTypeElement != null ? groovyObjectTypeElement.asType() : null;

            Set<TypeElement> elements = new LinkedHashSet<>();

            for (TypeElement annotation : annotations) {
                final Set<? extends Element> annotatedElements = roundEnv.getElementsAnnotatedWith(annotation);
                includeElements(elements, annotatedElements, groovyObjectType);
            }

            // This call to getRootElements() should be removed in Micronaut 4. It should not be possible
            // to process elements without at least one annotation present and this call breaks that assumption.
            final Set<? extends Element> rootElements = roundEnv.getRootElements();
            includeElements(elements, rootElements, groovyObjectType);

            if (!elements.isEmpty()) {

                JavaElementFactory elementFactory = javaVisitorContext.getElementFactory();
                JavaElementAnnotationMetadataFactory elementAnnotationMetadataFactory = javaVisitorContext.getElementAnnotationMetadataFactory();

                // The visitor X with a higher priority should process elements of A before
                // the visitor Y which is processing elements of B but also using elements A

                // Micronaut Data use-case: EntityMapper with a higher priority needs to process entities first
                // before RepositoryMapper is going to process repositories and read entities

                for (LoadedVisitor loadedVisitor : loadedVisitors) {
                    for (TypeElement typeElement : elements) {
                        try {
                            JavaClassElement javaClassElement = elementFactory.newSourceClassElement(
                                typeElement,
                                elementAnnotationMetadataFactory
                            );
                            if (!loadedVisitor.matchesClass(javaClassElement)) {
                                continue;
                            }
                            String className = typeElement.getQualifiedName().toString();
                            typeElement.accept(new ElementVisitor(javaClassElement, typeElement, Collections.singletonList(loadedVisitor)), className);
                        } catch (ProcessingException e) {
                            error((Element) e.getOriginatingElement(), e.getMessage());
                        } catch (PostponeToNextRoundException e) {
                            // Ignore
                            continue;
                        }
                    }
                }
            }

            for (LoadedVisitor loadedVisitor : loadedVisitors) {
                try {
                    loadedVisitor.getVisitor().finish(javaVisitorContext);
                } catch (Throwable e) {
                    error("Error finalizing type visitor [%s]: %s", loadedVisitor.getVisitor(), e.getMessage());
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
            writeBeanDefinitionsToMetaInf();
        }
        return false;
    }

    private void includeElements(Set<TypeElement> target,
                                 Set<? extends Element> annotatedElements, TypeMirror groovyObjectType) {
        annotatedElements
            .stream()
            .filter(element -> JavaModelUtils.isClassOrInterface(element) || JavaModelUtils.isEnum(element) || JavaModelUtils.isRecord(element))
            .map(modelUtils::classElementFor)
            .filter(Objects::nonNull)
            .filter(element -> element.getAnnotation(Generated.class) == null)
            .filter(typeElement -> groovyObjectType == null || !typeUtils.isAssignable(typeElement.asType(), groovyObjectType))
            .forEach(target::add);
    }

    /**
     * Discovers the {@link TypeElementVisitor} instances that are available.
     *
     * @return A collection of type element visitors.
     */
    protected @NonNull
    Collection<TypeElementVisitor> findTypeElementVisitors() {
        for (String visitorWarning : VISITOR_WARNINGS) {
            warning(visitorWarning);
        }
        return findCoreTypeElementVisitors(null);
    }

    /**
     * Writes {@link io.micronaut.inject.BeanDefinitionReference} into /META-INF/services/io.micronaut.inject.BeanDefinitionReference.
     */
    private void writeBeanDefinitionsToMetaInf() {
        try {
            classWriterOutputVisitor.finish();
        } catch (Exception e) {
            String message = e.getMessage();
            error("Error occurred writing META-INF files: %s", message != null ? message : e);
        }
    }

    private static @NonNull
    Collection<TypeElementVisitor> findCoreTypeElementVisitors(
        @Nullable Set<String> warnings) {
        return SERVICE_LOADER.collectAll(visitor -> {
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
            // remove duplicate classes
            .collect(Collectors.toMap(v -> v.getClass(), v -> v, (a, b) -> a)).values();
    }

    /**
     * The class to visit the type elements.
     */
    private class ElementVisitor extends ElementScanner8<Object, Object> {

        private final TypeElement concreteClass;
        private final List<LoadedVisitor> visitors;
        private JavaClassElement javaClassElement;

        ElementVisitor(JavaClassElement javaClassElement, TypeElement concreteClass, List<LoadedVisitor> visitors) {
            this.javaClassElement = javaClassElement;
            this.concreteClass = concreteClass;
            this.visitors = visitors;
        }

        @Override
        public Object visitUnknown(Element e, Object o) {
            // ignore
            return o;
        }

        @Override
        public Object visitType(TypeElement classElement, Object o) {
            if (!classElement.equals(javaClassElement.getNativeTypeElement())) {
                javaClassElement = javaVisitorContext.getElementFactory().newSourceClassElement(
                    classElement,
                    javaVisitorContext.getElementAnnotationMetadataFactory()
                );
            }
            for (LoadedVisitor visitor : visitors) {
                visitor.getVisitor().visitClass(javaClassElement, javaVisitorContext);
            }

            Element enclosingElement = classElement.getEnclosingElement();
            // don't process inner class unless this is the visitor for it
            boolean shouldVisit = !JavaModelUtils.isClass(enclosingElement) ||
                concreteClass.getQualifiedName().equals(classElement.getQualifiedName());
            if (shouldVisit) {
                if (javaClassElement.hasStereotype(Introduction.class) || (javaClassElement.hasStereotype(Introspected.class) && javaClassElement.isAbstract())) {
                    classElement.asType().accept(new PublicAbstractMethodVisitor<Object, Object>(classElement, javaVisitorContext) {
                        @Override
                        protected void accept(DeclaredType type, Element element, Object o) {
                            if (element instanceof ExecutableElement) {
                                ElementVisitor.this.visitExecutable(
                                    (ExecutableElement) element,
                                    o
                                );
                            }
                        }
                    }, null);
                    return null;
                } else if (JavaModelUtils.isEnum(classElement)) {
                    return scan(classElement.getEnclosedElements(), o);
                } else {
                    List<? extends Element> elements = enclosedElements(classElement);
                    Object value = null;
                    for (Element element : elements) {
                        if (element instanceof TypeElement) {
                            // Ignore any inner classes, annotation processor will process them if needed
                            continue;
                        } else {
                            value = scan(element, o);
                        }
                    }
                    return value;
                }
            } else {
                return null;
            }
        }

        private List<? extends Element> enclosedElements(TypeElement classElement) {
            List<Element> enclosedElements = new ArrayList<>(classElement.getEnclosedElements());
            TypeElement superClass = modelUtils.superClassFor(classElement);
            // collect fields and methods, skip overrides
            while (superClass != null && !modelUtils.isObjectClass(superClass)) {
                List<? extends Element> elements = superClass.getEnclosedElements();
                for (Element elt1 : elements) {
                    if (elt1 instanceof ExecutableElement) {
                        checkMethodOverride(enclosedElements, elt1);
                    } else if (elt1 instanceof VariableElement) {
                        checkFieldHide(enclosedElements, elt1);
                    }
                }
                superClass = modelUtils.superClassFor(superClass);
            }
            return enclosedElements;
        }

        private void checkFieldHide(List<Element> enclosedElements, Element elt1) {
            boolean hides = false;
            for (Element elt2 : enclosedElements) {
                if (elt1.equals(elt2) || !(elt2 instanceof VariableElement)) {
                    continue;
                }
                if (elementUtils.hides(elt2, elt1)) {
                    hides = true;
                    break;
                }
            }
            if (!hides) {
                enclosedElements.add(elt1);
            }
        }

        private void checkMethodOverride(List<Element> enclosedElements, Element elt1) {
            boolean overrides = false;
            for (Element elt2 : enclosedElements) {
                if (elt1.equals(elt2) || !(elt2 instanceof ExecutableElement)) {
                    continue;
                }
                if (elementUtils.overrides((ExecutableElement) elt2, (ExecutableElement) elt1, modelUtils.classElementFor(elt2))) {
                    overrides = true;
                    break;
                }
            }
            if (!overrides) {
                enclosedElements.add(elt1);
            }
        }

        @Override
        public Object visitExecutable(ExecutableElement executableElement, Object o) {
            if (javaClassElement == null) {
                return null;
            }
            if (executableElement.getSimpleName().toString().equals("<init>")) {
                ConstructorElement constructorElement = javaVisitorContext.getElementFactory().newConstructorElement(
                    javaClassElement,
                    executableElement,
                    javaVisitorContext.getElementAnnotationMetadataFactory()
                );
                for (LoadedVisitor visitor : visitors) {
                    if (visitor.matchesElement(constructorElement)) {
                        visitor.getVisitor().visitConstructor(constructorElement, javaVisitorContext);
                    }
                }
                return constructorElement;
            }
            MethodElement methodElement = javaVisitorContext.getElementFactory().newSourceMethodElement(
                javaClassElement,
                executableElement,
                javaVisitorContext.getElementAnnotationMetadataFactory()
            );
            if (methodElement.getDeclaringType().isAssignable(Enum.class)) {
                return null;
            }
            for (LoadedVisitor visitor : visitors) {
                if (visitor.matchesElement(methodElement)) {
                    visitor.getVisitor().visitMethod(methodElement, javaVisitorContext);
                }
            }
            return methodElement;
        }

        @Override
        public Object visitVariable(VariableElement variable, Object o) {
            // assuming just fields, visitExecutable should be handling params for method calls
            if (variable.getKind() != FIELD) {
                return null;
            }
            if (variable.getKind() == ElementKind.ENUM_CONSTANT) {
                EnumConstantElement constantElement = javaVisitorContext.getElementFactory().newEnumConstantElement(
                    javaClassElement,
                    variable,
                    javaVisitorContext.getElementAnnotationMetadataFactory()
                );
                for (LoadedVisitor visitor : visitors) {
                    if (visitor.matchesElement(constantElement)) {
                        visitor.getVisitor().visitEnumConstant(constantElement, javaVisitorContext);
                    }
                }
                return constantElement;
            }
            FieldElement fieldElement = javaVisitorContext.getElementFactory()
                .newFieldElement(
                    javaClassElement,
                    variable,
                    javaVisitorContext.getElementAnnotationMetadataFactory()
                );
            for (LoadedVisitor visitor : visitors) {
                if (visitor.matchesElement(fieldElement)) {
                    visitor.getVisitor().visitField(fieldElement, javaVisitorContext);
                }
            }
            return fieldElement;
        }
    }
}
