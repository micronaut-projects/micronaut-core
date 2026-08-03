/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.context.python;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.io.service.SoftServiceLoader;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;

@Internal
final class GraalPyContextCustomizers {
    private GraalPyContextCustomizers() {
    }

    static List<GraalPyContextCustomizer> load(ClassLoader classLoader) {
        List<GraalPyContextCustomizer> customizers = SoftServiceLoader
            .load(GraalPyContextCustomizer.class, classLoader)
            .collectAll();
        customizers.sort(Comparator.comparingInt(GraalPyContextCustomizer::getOrder));
        return customizers;
    }

    static String[] languages(ClassLoader classLoader) {
        LinkedHashSet<String> languages = new LinkedHashSet<>();
        languages.add(GraalPyRuntimeUtil.PYTHON);
        for (GraalPyContextCustomizer customizer : load(classLoader)) {
            languages.addAll(customizer.getAdditionalLanguages());
        }
        return languages.toArray(String[]::new);
    }

    static ClassLoader currentClassLoader() {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        return classLoader == null ? GraalPyContextCustomizers.class.getClassLoader() : classLoader;
    }
}
