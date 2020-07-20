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

import io.micronaut.core.annotation.Generated;
import io.micronaut.core.util.StringUtils;

import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedOptions;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import java.util.*;

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

    private final Map<String, Set<String>> serviceDescriptors = new HashMap<>();

    @Override
    protected String getIncrementalProcessorType() {
        return GRADLE_PROCESSING_AGGREGATING;
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Collections.singleton("io.micronaut.core.annotation.Generated");
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        for (TypeElement annotation : annotations) {
            Set<? extends Element> elements = roundEnv.getElementsAnnotatedWith(annotation);
            for (Element element : elements) {
                if (element instanceof TypeElement) {
                    String name = ((TypeElement) element).getQualifiedName().toString();
                    Generated generated = element.getAnnotation(Generated.class);
                    if (generated != null) {
                        String serviceName = generated.service();
                        if (StringUtils.isNotEmpty(serviceName)) {
                            serviceDescriptors.computeIfAbsent(serviceName, s1 -> new HashSet<>())
                                    .add(name);
                        }
                    }

                }

            }
        }
        if (roundEnv.processingOver() && !serviceDescriptors.isEmpty()) {
            classWriterOutputVisitor.writeServiceEntries(
                    serviceDescriptors
            );
        }
        return true;
    }
}
