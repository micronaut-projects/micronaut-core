/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.annotation.processing;

import io.micronaut.annotation.processing.visitor.LoadedVisitor;
import io.micronaut.aop.Introduction;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Generated;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.io.service.ServiceDefinition;
import io.micronaut.core.io.service.SoftServiceLoader;
import io.micronaut.core.order.OrderUtil;
import io.micronaut.core.util.StringUtils;
import io.micronaut.core.version.VersionUtils;
import io.micronaut.inject.annotation.AnnotationMetadataHierarchy;
import io.micronaut.inject.processing.JavaModelUtils;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedOptions;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementScanner8;

import java.util.*;
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

    private List<LoadedVisitor> loadedVisitors;
    private Collection<TypeElementVisitor> typeElementVisitors;

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
            // workaround for Micronaut Data until it is upgraded to Micronaut 2.x
            if (visitor.getClass().getName().startsWith("io.micronaut.data")) {
                if (incrementalProcessorKind == TypeElementVisitor.VisitorKind.ISOLATING) {
                    try {
                        loadedVisitors.add(new LoadedVisitor(
                                visitor,
                                javaVisitorContext,
                                genericUtils,
                                processingEnv
                        ));
                    } catch (TypeNotPresentException | NoClassDefFoundError e) {
                        // ignored, means annotations referenced are not on the classpath
                    }
                }
            } else {

                if (incrementalProcessorKind == visitorKind) {
                    try {
                        loadedVisitors.add(new LoadedVisitor(
                                visitor,
                                javaVisitorContext,
                                genericUtils,
                                processingEnv
                        ));
                    } catch (TypeNotPresentException | NoClassDefFoundError e) {
                        // ignored, means annotations referenced are not on the classpath
                    }
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
     * @return The loaded visitors.
     */
    protected List<LoadedVisitor> getLoadedVisitors() {
        return loadedVisitors;
    }

    /**
     *
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
                .map(cls -> cls.getAnnotation(SupportedOptions.class))
                .flatMap((SupportedOptions supportedOptions) -> Arrays.stream(supportedOptions.value()));
        return Stream.of(baseOption, visitorsAnnotationsOptions, visitorsOptions)
                .flatMap(Stream::sequential)
                .collect(Collectors.toSet());
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {

        if (!loadedVisitors.isEmpty()) {

            TypeElement groovyObjectTypeElement = elementUtils.getTypeElement("groovy.lang.GroovyObject");
            TypeMirror groovyObjectType = groovyObjectTypeElement != null ? groovyObjectTypeElement.asType() : null;

            roundEnv.getRootElements()
                    .stream()
                    .filter(element -> JavaModelUtils.isClassOrInterface(element) || JavaModelUtils.isEnum(element))
                    .filter(element -> element.getAnnotation(Generated.class) == null)
                    .map(modelUtils::classElementFor)
                    .filter(typeElement -> typeElement == null || (groovyObjectType == null || !typeUtils.isAssignable(typeElement.asType(), groovyObjectType)))
                    .forEach(typeElement -> {
                        String className = typeElement.getQualifiedName().toString();
                        List<LoadedVisitor> matchedVisitors = loadedVisitors.stream().filter(v -> v.matches(typeElement)).collect(Collectors.toList());
                        typeElement.accept(new ElementVisitor(typeElement, matchedVisitors), className);
                    });

            for (LoadedVisitor loadedVisitor : loadedVisitors) {
                try {
                    loadedVisitor.getVisitor().finish(javaVisitorContext);
                } catch (Throwable e) {
                    error("Error finalizing type visitor [%s]: %s", loadedVisitor.getVisitor(), e.getMessage());
                }
            }
        }

        return false;
    }

    /**
     * Discovers the {@link TypeElementVisitor} instances that are available.
     *
     * @return A collection of type element visitors.
     */
    protected @NonNull
    Collection<TypeElementVisitor> findTypeElementVisitors() {
        Map<String, TypeElementVisitor> typeElementVisitors = new HashMap<>(10);
        SoftServiceLoader<TypeElementVisitor> serviceLoader = SoftServiceLoader.load(TypeElementVisitor.class, getClass().getClassLoader());
        for (ServiceDefinition<TypeElementVisitor> definition : serviceLoader) {
            if (definition.isPresent()) {
                TypeElementVisitor visitor;
                try {
                    visitor = definition.load();
                } catch (Throwable e) {
                    warning("TypeElementVisitor [" + definition.getName() + "] will be ignored due to loading error: " + e.getMessage());
                    continue;
                }
                if (visitor == null) {
                    continue;
                }

                final Requires requires = visitor.getClass().getAnnotation(Requires.class);
                if (requires != null) {
                    final Requires.Sdk sdk = requires.sdk();
                    if (sdk == Requires.Sdk.MICRONAUT) {
                        final String version = requires.version();
                        if (StringUtils.isNotEmpty(version) && !VersionUtils.isAtLeastMicronautVersion(version)) {
                            try {
                                warning("TypeElementVisitor [" + definition.getName() + "] will be ignored because Micronaut version [" + VersionUtils.MICRONAUT_VERSION + "] must be at least " + version);
                                continue;
                            } catch (IllegalArgumentException e) {
                                // shouldn't happen, thrown when invalid version encountered
                            }
                        }
                    }
                }

                typeElementVisitors.put(definition.getName(), visitor);
            }
        }
        return typeElementVisitors.values();
    }


    /**
     * The class to visit the type elements.
     */
    private class ElementVisitor extends ElementScanner8<Object, Object> {

        private final TypeElement concreteClass;
        private final List<LoadedVisitor> visitors;

        ElementVisitor(TypeElement concreteClass, List<LoadedVisitor> visitors) {
            this.concreteClass = concreteClass;
            this.visitors = visitors;
        }

        @Override
        public Object visitType(TypeElement classElement, Object o) {

            AnnotationMetadata typeAnnotationMetadata = annotationUtils.getAnnotationMetadata(classElement);
            for (LoadedVisitor visitor : visitors) {
                final io.micronaut.inject.ast.Element resultingElement = visitor.visit(classElement, typeAnnotationMetadata);
                if (resultingElement != null) {
                    typeAnnotationMetadata = resultingElement.getAnnotationMetadata();
                }
            }

            Element enclosingElement = classElement.getEnclosingElement();
            // don't process inner class unless this is the visitor for it
            boolean shouldVisit = !JavaModelUtils.isClass(enclosingElement) ||
                    concreteClass.getQualifiedName().equals(classElement.getQualifiedName());
            if (shouldVisit) {
                if (typeAnnotationMetadata.hasStereotype(Introduction.class) || (typeAnnotationMetadata.hasStereotype(Introspected.class) && modelUtils.isAbstract(classElement))) {
                    classElement.asType().accept(new PublicAbstractMethodVisitor<Object, Object>(classElement, modelUtils, elementUtils) {
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
                    for (Element element: elements) {
                        value = scan(element, o);
                        if (element instanceof TypeElement) {
                            TypeElement typeElement = (TypeElement) element;
                            for (LoadedVisitor visitor : visitors) {
                                if (visitor.matches(typeElement)) {
                                    value = scan(enclosedElements(typeElement), o);
                                }
                            }
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
                for (Element elt1: elements) {
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
            for (Element elt2: enclosedElements) {
                if (elt1.equals(elt2) || ! (elt2 instanceof VariableElement)) {
                    continue;
                }
                if (elementUtils.hides(elt2, elt1)) {
                    hides = true;
                    break;
                }
            }
            if (! hides) {
                enclosedElements.add(elt1);
            }
        }

        private void checkMethodOverride(List<Element> enclosedElements, Element elt1) {
            boolean overrides = false;
            for (Element elt2: enclosedElements) {
                if (elt1.equals(elt2) || ! (elt2 instanceof ExecutableElement)) {
                    continue;
                }
                if (elementUtils.overrides((ExecutableElement) elt2, (ExecutableElement) elt1,  modelUtils.classElementFor(elt2))) {
                    overrides = true;
                    break;
                }
            }
            if (! overrides) {
                enclosedElements.add(elt1);
            }
        }

        @Override
        public Object visitExecutable(ExecutableElement executableElement, Object o) {
            AnnotationMetadata methodAnnotationMetadata = new AnnotationMetadataHierarchy(
                    annotationUtils.getAnnotationMetadata(executableElement.getEnclosingElement()),
                    annotationUtils.getAnnotationMetadata(executableElement)
            );
            if (executableElement.getSimpleName().toString().equals("<init>")) {
                for (LoadedVisitor visitor : visitors) {
                    final io.micronaut.inject.ast.Element resultingElement = visitor.visit(executableElement, methodAnnotationMetadata);
                    if (resultingElement != null) {
                        methodAnnotationMetadata = resultingElement.getAnnotationMetadata();
                    }
                }
            } else {

                for (LoadedVisitor visitor : visitors) {
                    if (visitor.matches(methodAnnotationMetadata)) {
                        final io.micronaut.inject.ast.Element resultingElement = visitor.visit(executableElement, methodAnnotationMetadata);
                        if (resultingElement != null) {
                            methodAnnotationMetadata = resultingElement.getAnnotationMetadata();
                        }
                    }
                }
            }


            return null;
        }

        @Override
        public Object visitVariable(VariableElement variable, Object o) {
            // assuming just fields, visitExecutable should be handling params for method calls
            if (variable.getKind() != FIELD) {
                return null;
            }

            AnnotationMetadata fieldAnnotationMetadata = annotationUtils.getAnnotationMetadata(variable);

            for (LoadedVisitor visitor : visitors) {
                if (visitor.matches(fieldAnnotationMetadata)) {
                    final io.micronaut.inject.ast.Element resultingElement = visitor.visit(variable, fieldAnnotationMetadata);
                    if (resultingElement != null) {
                        fieldAnnotationMetadata = resultingElement.getAnnotationMetadata();
                    }
                }
            }

            return null;
        }
    }
}
