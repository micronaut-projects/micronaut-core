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
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.io.service.ServiceDefinition;
import io.micronaut.core.io.service.SoftServiceLoader;
import io.micronaut.core.util.StringUtils;
import io.micronaut.core.version.VersionUtils;
import io.micronaut.inject.processing.JavaModelUtils;
import io.micronaut.inject.visitor.TypeElementVisitor;

import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementScanner8;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static javax.lang.model.element.ElementKind.FIELD;

/**
 * <p>The annotation processed used to execute type element visitors.</p>
 *
 * @author James Kleeh
 * @since 1.0
 */
@SupportedAnnotationTypes("*")
public class TypeElementVisitorProcessor extends AbstractInjectAnnotationProcessor {

    private boolean executed = false;

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        //Only run on the first pass
        if (executed) {
            return false;
        }


        SoftServiceLoader<TypeElementVisitor> serviceLoader = SoftServiceLoader.load(TypeElementVisitor.class, getClass().getClassLoader());
        Map<String, LoadedVisitor> loadedVisitors = new HashMap<>();
        for (ServiceDefinition<TypeElementVisitor> definition : serviceLoader) {
            if (definition.isPresent()) {
                TypeElementVisitor visitor = definition.load();
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

                try {
                    loadedVisitors.put(definition.getName(), new LoadedVisitor(
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

        for (LoadedVisitor loadedVisitor : loadedVisitors.values()) {
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
                .filter(JavaModelUtils::isClassOrInterface)
                .map(modelUtils::classElementFor)
                .filter(typeElement -> groovyObjectType == null || !typeUtils.isAssignable(typeElement.asType(), groovyObjectType))
                .forEach((typeElement) -> {
                    String className = typeElement.getQualifiedName().toString();
                    List<LoadedVisitor> matchedVisitors = loadedVisitors.values().stream().filter((v) -> v.matches(typeElement)).collect(Collectors.toList());
                    typeElement.accept(new ElementVisitor(typeElement, matchedVisitors), className);
                });

        for (LoadedVisitor loadedVisitor : loadedVisitors.values()) {
            try {
                loadedVisitor.getVisitor().finish(javaVisitorContext);
            } catch (Throwable e) {
                error("Error finalizing type visitor [%s]: %s", loadedVisitor.getVisitor(), e.getMessage());
            }
        }

        executed = true;
        return false;
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

            visitors.forEach(v -> v.visit(classElement, typeAnnotationMetadata));

            Element enclosingElement = classElement.getEnclosingElement();
            // don't process inner class unless this is the visitor for it
            boolean shouldVisit = !JavaModelUtils.isClass(enclosingElement) ||
                    concreteClass.getQualifiedName().equals(classElement.getQualifiedName());

            if (shouldVisit) {
                TypeElement superClass = modelUtils.superClassFor(classElement);
                if (superClass != null && !modelUtils.isObjectClass(superClass)) {
                    superClass.accept(this, o);
                }

                return scan(classElement.getEnclosedElements(), o);
            } else {
                return null;
            }
        }

        @Override
        public Object visitExecutable(ExecutableElement executableElement, Object o) {
            AnnotationMetadata methodAnnotationMetadata = annotationUtils.getAnnotationMetadata(executableElement);
            if (executableElement.getSimpleName().toString().equals("<init>")) {
                visitors.forEach(v -> v.visit(executableElement, methodAnnotationMetadata));
                return null;
            } else {


                visitors.stream()
                        .filter(v -> v.matches(methodAnnotationMetadata))
                        .forEach(v -> v.visit(executableElement, methodAnnotationMetadata));
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

            visitors.stream()
                    .filter(v -> v.matches(fieldAnnotationMetadata))
                    .forEach(v -> v.visit(variable, fieldAnnotationMetadata));

            return null;
        }
    }
}
