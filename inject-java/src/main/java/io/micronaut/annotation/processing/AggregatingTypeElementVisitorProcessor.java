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

import io.micronaut.annotation.processing.visitor.LoadedVisitor;
import io.micronaut.inject.visitor.TypeElementVisitor;

import javax.annotation.processing.SupportedOptions;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * <p>The annotation processed used to execute type element visitors.</p>
 *
 * @author graemerocher
 * @since 2.0
 */
@SupportedOptions({AbstractInjectAnnotationProcessor.MICRONAUT_PROCESSING_INCREMENTAL, AbstractInjectAnnotationProcessor.MICRONAUT_PROCESSING_ANNOTATIONS})
public class AggregatingTypeElementVisitorProcessor extends TypeElementVisitorProcessor {

    @Override
    protected String getIncrementalProcessorType() {
        return GRADLE_PROCESSING_AGGREGATING;
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        if (isIncremental(processingEnv)) {
            List<LoadedVisitor> loadedVisitors = getLoadedVisitors();
            Set<String> annotationNames = new HashSet<>();
            // try and narrow the annotations to only the ones interesting to the visitors
            // if a visitor is interested in Object than fall back to all
            for (LoadedVisitor loadedVisitor : loadedVisitors) {
                TypeElementVisitor<?, ?> visitor = loadedVisitor.getVisitor();
                Set<String> supportedAnnotationNames = visitor.getSupportedAnnotationNames();
                if (supportedAnnotationNames.contains("*")) {
                    return super.getSupportedAnnotationTypes();
                } else {
                    annotationNames.addAll(supportedAnnotationNames);
                }
            }
            return annotationNames;
        } else {
            return super.getSupportedAnnotationTypes();
        }
    }
}
