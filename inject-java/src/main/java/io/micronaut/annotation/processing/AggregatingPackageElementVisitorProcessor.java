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

import io.micronaut.inject.visitor.PackageElementVisitor;

import javax.annotation.processing.SupportedOptions;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * <p>The aggregating {@link PackageElementVisitorProcessor}.</p>
 *
 * @author Denis Stepanov
 * @since 4.10
 */
@SupportedOptions({AbstractInjectAnnotationProcessor.MICRONAUT_PROCESSING_INCREMENTAL, AbstractInjectAnnotationProcessor.MICRONAUT_PROCESSING_ANNOTATIONS})
public non-sealed class AggregatingPackageElementVisitorProcessor extends PackageElementVisitorProcessor {

    @Override
    protected String getIncrementalProcessorType() {
        return GRADLE_PROCESSING_AGGREGATING;
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        if (!hasVisitors()) {
            return Collections.emptySet();
        }
        if (isIncremental(processingEnv)) {
            var annotationNames = new HashSet<String>();
            // try and narrow the annotations to only the ones interesting to the visitors
            // if a visitor is interested in Object than fall back to all
            for (PackageLoadedVisitor loadedVisitor : getPackageVisitors()) {
                PackageElementVisitor<?> visitor = loadedVisitor.visitor();
                Set<String> supportedAnnotationNames = visitor.getSupportedAnnotationNames();
                if (supportedAnnotationNames.contains("*")) {
                    return super.getSupportedAnnotationTypes();
                } else {
                    annotationNames.addAll(supportedAnnotationNames);
                }
            }
            return annotationNames;
        }
        return super.getSupportedAnnotationTypes();
    }
}
