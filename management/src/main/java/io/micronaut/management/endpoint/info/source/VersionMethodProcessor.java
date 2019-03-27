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

package io.micronaut.management.endpoint.info.source;

import io.micronaut.context.processor.ExecutableMethodProcessor;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.version.annotation.Version;
import io.micronaut.http.annotation.Controller;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;

import javax.inject.Singleton;
import java.util.HashSet;
import java.util.Set;

/**
 * Visits every {@link Controller} method and extracts the value of the {@link io.micronaut.core.version.annotation.Version} annotation if present.
 *
 * @author Sergio del Amo
 * @since 1.1.0
 */
@Singleton
public class VersionMethodProcessor implements ExecutableMethodProcessor<Controller>, VersionCollector {

    private Set<String> versions = new HashSet<>();

    @Override
    public void process(BeanDefinition<?> beanDefinition, ExecutableMethod<?, ?> method) {
        AnnotationValue<Version> annotationValue = method.getAnnotation(Version.class);
        if (annotationValue != null) {
            annotationValue.getValue(String.class).map(version -> versions.add(version));
        }
    }

    @Override
    public Set<String> versions() {
        return this.versions;
    }
}

