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
package io.micronaut.python.processing.metadata;

import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.processing.ProcessingException;
import io.micronaut.inject.visitor.VisitorContext;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Which backend generates the bean definitions and introspections of the Python classes of a compilation.
 *
 * <p>Selected through the annotation processor option {@value #BACKEND_OPTION}, with {@value #TYPES_OPTION}
 * restricting the model backends to the listed classes ({@code app.Bean}) or packages ({@code app.*}); all Python
 * classes of the compilation are selected when the option is absent. The prototype's option
 * {@value #LEGACY_OPTION} still selects the runtime backend for the listed classes.</p>
 *
 * @since 5.3.0
 */
@Internal
public enum PythonMetadataBackend {

    /**
     * The existing writers of the compiler emit class files. The default.
     */
    COMPILER("compiler"),

    /**
     * The resolved model is captured and the shared generator emits the class files at build time, next to the
     * saved model. The output is deployable like the compiler's, including in native images.
     */
    MODEL_BUILD_TIME("model-build-time"),

    /**
     * The resolved model is saved with a discovery catalog; the shared generator emits the classes at runtime.
     */
    MODEL_RUNTIME("model-runtime");

    /**
     * The backend option.
     */
    public static final String BACKEND_OPTION = "micronaut.python.metadata.backend";

    /**
     * The selection option.
     */
    public static final String TYPES_OPTION = "micronaut.python.metadata.types";

    /**
     * The prototype's option.
     */
    public static final String LEGACY_OPTION = "micronaut.python.runtimeMetadata";

    private final String optionValue;

    PythonMetadataBackend(String optionValue) {
        this.optionValue = optionValue;
    }

    /**
     * @return The option value selecting this backend
     */
    public String optionValue() {
        return optionValue;
    }

    /**
     * @return Whether the backend captures the resolved model
     */
    public boolean isModel() {
        return this != COMPILER;
    }

    /**
     * The backend of a compilation.
     *
     * @param context The visitor context
     * @return The backend
     */
    public static PythonMetadataBackend of(VisitorContext context) {
        Map<String, String> options = context.getOptions();
        String value = options.get(BACKEND_OPTION);
        if (value == null || value.isBlank()) {
            String legacy = options.get(LEGACY_OPTION);
            return legacy == null || legacy.isBlank() ? COMPILER : MODEL_RUNTIME;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (PythonMetadataBackend backend : values()) {
            if (backend.optionValue.equals(normalized)) {
                return backend;
            }
        }
        throw new ProcessingException(null, "Unknown value of " + BACKEND_OPTION + ": " + value + " (expected compiler, model-build-time or model-runtime)");
    }

    /**
     * Whether a model backend generates the metadata of a class.
     *
     * @param element The class
     * @param context The visitor context
     * @return Whether the class is selected for a model backend
     */
    public static boolean isSelected(ClassElement element, VisitorContext context) {
        PythonMetadataBackend backend = of(context);
        if (!backend.isModel()) {
            return false;
        }
        Set<String> selection = selection(context);
        if (selection.isEmpty()) {
            return true;
        }
        String name = element.getName();
        for (String selected : selection) {
            if (selected.endsWith(".*")) {
                if (name.startsWith(selected.substring(0, selected.length() - 1))) {
                    return true;
                }
            } else if (selected.equals(name)) {
                return true;
            }
        }
        return false;
    }

    private static Set<String> selection(VisitorContext context) {
        Map<String, String> options = context.getOptions();
        String value = options.get(TYPES_OPTION);
        if (value == null || value.isBlank()) {
            value = options.getOrDefault(LEGACY_OPTION, "");
        }
        Set<String> names = new LinkedHashSet<>();
        Arrays.stream(value.split(",")).map(String::trim).filter(name -> !name.isEmpty()).forEach(names::add);
        return names;
    }
}
