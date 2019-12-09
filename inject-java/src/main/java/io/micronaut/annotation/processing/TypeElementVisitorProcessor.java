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
package io.micronaut.annotation.processing;

import io.micronaut.annotation.processing.visitor.LoadedVisitor;
import io.micronaut.aop.Introduction;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.reflect.InstantiationUtils;
import io.micronaut.inject.annotation.AnnotationMetadataHierarchy;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.io.service.ServiceDefinition;
import io.micronaut.core.io.service.SoftServiceLoader;
import io.micronaut.core.order.OrderUtil;
import io.micronaut.core.util.StringUtils;
import io.micronaut.core.version.VersionUtils;
import io.micronaut.inject.beans.visitor.IntrospectedTypeElementVisitor;
import io.micronaut.inject.processing.JavaModelUtils;
import io.micronaut.inject.visitor.TypeElementVisitor;

import javax.annotation.Nonnull;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementScanner8;
import java.util.*;
import java.util.stream.Collectors;

import static javax.lang.model.element.ElementKind.FIELD;

/**
 * <p>The annotation processed used to execute type element visitors.</p>
 *
 * @author James Kleeh
 * @author graemerocher
 * @since 1.0
 */
@SupportedAnnotationTypes("*")
public class TypeElementVisitorProcessor extends AbstractInjectAnnotationProcessor {

    private boolean executed = false;

    @Override
    public Set<String> getSupportedOptions() {
        return Collections.singleton("org.gradle.annotation.processing.aggregating");
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        //Only run on the first pass
        if (executed) {
            return false;
        }


        Collection<TypeElementVisitor> typeElementVisitors = findTypeElementVisitors();
        List<LoadedVisitor> loadedVisitors = new ArrayList<>(typeElementVisitors.size());
        for (TypeElementVisitor visitor : typeElementVisitors) {
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
        OrderUtil.reverseSort(loadedVisitors);
        for (LoadedVisitor loadedVisitor : loadedVisitors) {
            try {
                loadedVisitor.getVisitor().start(javaVisitorContext);
            } catch (Throwable e) {
                error("Error initializing type visitor [%s]: %s", loadedVisitor.getVisitor(), e.getMessage());
            }
        }

        TypeElement groovyObjectTypeElement = elementUtils.getTypeElement("groovy.lang.GroovyObject");
        TypeMirror groovyObjectType = groovyObjectTypeElement != null ? groovyObjectTypeElement.asType() : null;

        roundEnv.getRootElements()
                .stream()
                .filter(element -> JavaModelUtils.isClassOrInterface(element) || JavaModelUtils.isEnum(element))
                .map(modelUtils::classElementFor)
                .filter(typeElement -> typeElement == null || (groovyObjectType == null || !typeUtils.isAssignable(typeElement.asType(), groovyObjectType)))
                .forEach((typeElement) -> {
                    String className = typeElement.getQualifiedName().toString();
                    List<LoadedVisitor> matchedVisitors = loadedVisitors.stream().filter((v) -> v.matches(typeElement)).collect(Collectors.toList());
                    typeElement.accept(new ElementVisitor(typeElement, matchedVisitors), className);
                });

        for (LoadedVisitor loadedVisitor : loadedVisitors) {
            try {
                loadedVisitor.getVisitor().finish(javaVisitorContext);
            } catch (Throwable e) {
                error("Error finalizing type visitor [%s]: %s", loadedVisitor.getVisitor(), e.getMessage());
            }
        }

        javaVisitorContext.finish();
        executed = true;
        return false;
    }

    /**
     * Discovers the {@link TypeElementVisitor} instances that are available.
     *
     * @return A collection of type element visitors.
     */
    protected @Nonnull Collection<TypeElementVisitor> findTypeElementVisitors() {
        Map<String, TypeElementVisitor> typeElementVisitors = new HashMap<>(10);
        SoftServiceLoader<TypeElementVisitor> serviceLoader = SoftServiceLoader.load(TypeElementVisitor.class, getClass().getClassLoader());
        try {
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
                            if (StringUtils.isNotEmpty(version)) {
                                if (!VersionUtils.isAtLeastMicronautVersion(version)) {
                                    try {
                                        warning("TypeElementVisitor [" + definition.getName() + "] will be ignored because Micronaut version [" + VersionUtils.MICRONAUT_VERSION + "] must be at least " + version);
                                        continue;
                                    } catch (IllegalArgumentException e) {
                                        // shouldn't happen, thrown when invalid version encountered
                                    }
                                }
                            }
                        }
                    }

                    typeElementVisitors.put(definition.getName(), visitor);
                }
            }
        } catch (ServiceConfigurationError e) {
            System.err.println("Failed to configure default TypeElementVisitors. Using fallback behaviour: " + e.getMessage());
            typeElementVisitors.put(IntrospectedTypeElementVisitor.class.getName(), new IntrospectedTypeElementVisitor());
            try {
                final TypeElementVisitor graalVisitor =
                        InstantiationUtils.instantiate("io.micronaut.graal.reflect.GraalTypeElementVisitor", TypeElementVisitor.class);
                typeElementVisitors.put(graalVisitor.getClass().getName(), graalVisitor);
            } catch (Exception ex) {
                // ignore
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
        private AnnotationMetadata typeAnnotationMetadata;

        ElementVisitor(TypeElement concreteClass, List<LoadedVisitor> visitors) {
            this.concreteClass = concreteClass;
            this.visitors = visitors;
            this.typeAnnotationMetadata = annotationUtils.getAnnotationMetadata(concreteClass);
        }

        @Override
        public Object visitType(TypeElement classElement, Object o) {


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
                } else {
                    TypeElement superClass = modelUtils.superClassFor(classElement);
                    if (superClass != null && !modelUtils.isObjectClass(superClass)) {
                        superClass.accept(this, o);
                    }

                    return scan(classElement.getEnclosedElements(), o);
                }
            } else {
                return null;
            }
        }

        @Override
        public Object visitExecutable(ExecutableElement executableElement, Object o) {
            AnnotationMetadata methodAnnotationMetadata = new AnnotationMetadataHierarchy(
                    typeAnnotationMetadata,
                    annotationUtils.getAnnotationMetadata(executableElement)
            );
            if (executableElement.getSimpleName().toString().equals("<init>")) {
                for (LoadedVisitor visitor : visitors) {
                    final io.micronaut.inject.ast.Element resultingElement = visitor.visit(executableElement, methodAnnotationMetadata);
                    if (resultingElement != null) {
                        methodAnnotationMetadata = resultingElement.getAnnotationMetadata();
                    }
                }
                return null;
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
