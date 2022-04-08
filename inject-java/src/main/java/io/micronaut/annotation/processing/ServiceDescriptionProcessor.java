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
import io.micronaut.context.ApplicationContextConfigurer;
import io.micronaut.context.annotation.ContextConfigurer;
import io.micronaut.context.visitor.ContextConfigurerVisitor;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Generated;
import io.micronaut.core.util.StringUtils;
import io.micronaut.inject.configuration.ConfigurationMetadataBuilder;
import io.micronaut.inject.configuration.ConfigurationMetadataWriter;

import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedOptions;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.Set;

/**
 * A separate aggregating annotation processor responsible for creating META-INF/services entries.
 *
 * @author graemerocher
 * @since 2.0.0
 */
@SupportedOptions({
        AbstractInjectAnnotationProcessor.MICRONAUT_PROCESSING_INCREMENTAL,
        AbstractInjectAnnotationProcessor.MICRONAUT_PROCESSING_ANNOTATIONS
})
public class ServiceDescriptionProcessor extends AbstractInjectAnnotationProcessor {
    private static final Set<String> SUPPORTED_ANNOTATIONS;
    private static final Set<String> SUPPORTED_SERVICE_TYPES = Collections.singleton(
            ApplicationContextConfigurer.class.getName()
    );

    static {
        Set<String> annotations = new HashSet<>(2);
        annotations.add(Generated.class.getName());
        annotations.add(ContextConfigurer.class.getName());
        SUPPORTED_ANNOTATIONS = Collections.unmodifiableSet(annotations);
    }

    private final Map<String, Set<String>> serviceDescriptors = new HashMap<>();

    @Override
    protected String getIncrementalProcessorType() {
        return GRADLE_PROCESSING_AGGREGATING;
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return SUPPORTED_ANNOTATIONS;
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        List<io.micronaut.inject.ast.Element> originatingElements = new ArrayList<>();
        for (TypeElement annotation : annotations) {
            Set<? extends Element> elements = roundEnv.getElementsAnnotatedWith(annotation);
            for (Element element : elements) {
                if (element instanceof TypeElement) {
                    TypeElement typeElement = (TypeElement) element;
                    String name = typeElement.getQualifiedName().toString();
                    if (!processGeneratedAnnotation(originatingElements, element, typeElement, name)) {
                        processContextConfigurerAnnotation(originatingElements, element, typeElement);
                    }
                }
            }
        }
        if (roundEnv.processingOver() && !serviceDescriptors.isEmpty()) {
            classWriterOutputVisitor.writeServiceEntries(
                    serviceDescriptors,
                    originatingElements.toArray(io.micronaut.inject.ast.Element.EMPTY_ELEMENT_ARRAY)
            );

            writeConfigurationMetadata();
        }
        return true;
    }

    private void processContextConfigurerAnnotation(List<io.micronaut.inject.ast.Element> originatingElements, Element element, TypeElement typeElement) {
        AnnotationMetadata annotationMetadata = annotationUtils.getAnnotationMetadata(element);
        Optional<AnnotationValue<ContextConfigurer>> ann = annotationMetadata.findAnnotation(ContextConfigurer.class);
        if (ann.isPresent()) {
            JavaClassElement javaClassElement = javaVisitorContext.getElementFactory().newClassElement(typeElement, annotationMetadata);
            ContextConfigurerVisitor.assertNoConstructorForContextAnnotation(javaClassElement);
            List<? extends TypeMirror> interfaces = typeElement.getInterfaces();
            for (TypeMirror interfaceType : interfaces) {
                if (interfaceType instanceof DeclaredType) {
                    String serviceName = modelUtils.resolveTypeName(interfaceType);
                    String serviceImpl = modelUtils.resolveTypeName(element.asType());
                    if (SUPPORTED_SERVICE_TYPES.contains(serviceName)) {
                        serviceDescriptors.computeIfAbsent(serviceName, s1 -> new HashSet<>())
                                .add(serviceImpl);
                        originatingElements.add(new JavaClassElement(typeElement, AnnotationMetadata.EMPTY_METADATA, null));
                    }
                }
            }
        }
        AnnotationUtils.invalidateCache();
    }

    private boolean processGeneratedAnnotation(List<io.micronaut.inject.ast.Element> originatingElements, Element element, TypeElement typeElement, String name) {
        Generated generated = element.getAnnotation(Generated.class);
        if (generated != null) {
            String serviceName = generated.service();
            if (StringUtils.isNotEmpty(serviceName)) {
                serviceDescriptors.computeIfAbsent(serviceName, s1 -> new HashSet<>())
                        .add(name);
                originatingElements.add(new JavaClassElement(typeElement, AnnotationMetadata.EMPTY_METADATA, null));
            }
            return true;
        }
        return false;
    }

    private void writeConfigurationMetadata() {
        ConfigurationMetadataBuilder.getConfigurationMetadataBuilder().ifPresent(metadataBuilder -> {
            try {
                if (metadataBuilder.hasMetadata()) {
                    ServiceLoader<ConfigurationMetadataWriter> writers = ServiceLoader.load(ConfigurationMetadataWriter.class, getClass().getClassLoader());

                    try {
                        for (ConfigurationMetadataWriter writer : writers) {
                            try {
                                writer.write(metadataBuilder, classWriterOutputVisitor);
                            } catch (IOException e) {
                                warning("Error occurred writing configuration metadata: %s", e.getMessage());
                            }
                        }
                    } catch (ServiceConfigurationError e) {
                        warning("Unable to load ConfigurationMetadataWriter due to : %s", e.getMessage());
                    }
                }
            } finally {
                ConfigurationMetadataBuilder.setConfigurationMetadataBuilder(null);
            }
        });

    }
}
