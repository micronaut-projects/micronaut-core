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
package io.micronaut.python.processing;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.PropertyElement;
import io.micronaut.inject.processing.ProcessingException;
import io.micronaut.inject.visitor.VisitorContext;
import org.jspecify.annotations.NullMarked;

import java.io.IOException;
import java.io.Writer;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Writes the deliberately restricted model used by the runtime-generation prototype.
 * This format is experimental and is not a replacement for general annotation metadata.
 */
@Internal
@NullMarked
public final class PythonRuntimeMetadataWriter {
    private static final String OPTION = "micronaut.python.runtimeMetadata";
    private static final String PATH = "micronaut/python/runtime/";
    private static final String SINGLETON = "jakarta.inject.Singleton";
    private static final Set<String> ANNOTATIONS = Set.of(SINGLETON, Introspected.class.getName());
    private static final Set<String> PROPERTY_TYPES = Set.of("java.lang.String", "int", "boolean");

    private PythonRuntimeMetadataWriter() {
    }

    /**
     * Reports whether the prototype was explicitly selected.
     *
     * @param context The visitor context
     * @return Whether the prototype was explicitly selected
     */
    public static boolean isEnabled(VisitorContext context) {
        return !selected(context).isEmpty();
    }

    /**
     * Reports whether generation is deferred for this type.
     *
     * @param element The Python class
     * @param context The visitor context
     * @return Whether generation is deferred for this type
     */
    public static boolean isSelected(ClassElement element, VisitorContext context) {
        return selected(context).contains(element.getName());
    }

    private static Set<String> selected(VisitorContext context) {
        String option = context.getOptions().getOrDefault(OPTION, "");
        if (option.isBlank()) {
            return Set.of();
        }
        Set<String> names = new LinkedHashSet<>();
        Arrays.stream(option.split(","))
            .map(String::trim).filter(name -> !name.isEmpty()).forEach(names::add);
        return names;
    }

    /**
     * Validates and writes the declared model of selected classes before type visitors run.
     *
     * @param environment The Python compilation
     */
    public static void write(PythonProcessingEnvironment environment) {
        VisitorContext context = environment.visitorContext();
        Set<String> remaining = selected(context);
        if (remaining.isEmpty()) {
            return;
        }
        var classes = environment.classes().values().stream()
            .filter(element -> remaining.contains(element.getName())).toList();
        for (ClassElement element : classes) {
            remaining.remove(element.getName());
            validate(element);
            var file = context.visitMetaInfFile(PATH + element.getName() + ".properties", element).orElseThrow();
            try (Writer writer = file.openWriter()) {
                writer.write("version=1\nbean-type=" + element.getName() + "\n");
                writer.write("singleton=" + element.hasDeclaredAnnotation(SINGLETON) + "\n");
                var properties = element.getBeanProperties();
                writer.write("property.count=" + properties.size() + "\n");
                for (int i = 0; i < properties.size(); i++) {
                    PropertyElement property = properties.get(i);
                    String prefix = "property." + i + ".";
                    writer.write(prefix + "name=" + property.getName() + "\n");
                    writer.write(prefix + "type=" + property.getType().getName() + "\n");
                    writer.write(prefix + "read=" + property.getReadMethod().orElseThrow().getName() + "\n");
                    writer.write(prefix + "write=" + property.getWriteMethod().orElseThrow().getName() + "\n");
                }
            } catch (IOException e) {
                throw new ProcessingException(element, "Cannot write runtime metadata: " + e.getMessage());
            }
        }
        if (!remaining.isEmpty()) {
            throw new IllegalArgumentException("Runtime metadata types not found: " + remaining);
        }
        var origins = classes.toArray(ClassElement[]::new);
        try (Writer writer = context.visitMetaInfFile(PATH + "index", origins).orElseThrow().openWriter()) {
            for (ClassElement element : classes) {
                writer.write(element.getName() + "\n");
            }
        } catch (IOException e) {
            throw new ProcessingException(classes.getFirst(), "Cannot write runtime metadata index: " + e.getMessage());
        }
    }

    private static void validate(ClassElement element) {
        if (!element.isPublic() || element.isAbstract() || element.isInner()
            || !element.getInterfaces().isEmpty()
            || element.getSuperType().filter(type -> !type.getName().equals("java.lang.Object")).isPresent()
            || !element.hasDeclaredAnnotation(Introspected.class)
            || !ANNOTATIONS.containsAll(element.getAnnotationMetadata().getAnnotationNames())) {
            unsupported(element, "only public, top-level @Introspected classes with optional @Singleton are supported");
        }
        MethodElement constructor = element.getPrimaryConstructor().orElseThrow(() ->
            new ProcessingException(element, "Runtime metadata prototype requires a public no-argument constructor"));
        if (!constructor.isPublic() || constructor.getParameters().length != 0 || constructor.isStatic()
            || !constructor.getDeclaredMethodAnnotationMetadata().isEmpty()) {
            unsupported(element, "a public no-argument constructor is required");
        }
        // PythonClassElement adds this exclusion to hide its interoperability helper property.
        var introspected = element.getAnnotationMetadata().getValues(Introspected.class.getName());
        if (introspected.keySet().stream().anyMatch(name -> !name.toString().equals("excludes"))
            || Arrays.stream(element.getAnnotationMetadata().stringValues(Introspected.class, "excludes"))
                .anyMatch(name -> !name.equals("memberKeys"))) {
            unsupported(element, "custom @Introspected options are not supported");
        }
        for (MethodElement method : element.getEnclosedElements(ElementQuery.ALL_METHODS.onlyDeclared())) {
            if (!method.getDeclaredMethodAnnotationMetadata().isEmpty()) {
                unsupported(element, "annotated methods (including injection, lifecycle and AOP) are not supported");
            }
        }
        for (PropertyElement property : element.getBeanProperties()) {
            if (!PROPERTY_TYPES.contains(property.getType().getName()) || property.isReadOnly()
                || property.getReadMethod().isEmpty() || property.getWriteMethod().isEmpty()
                || !property.getAnnotationMetadata().isEmpty()) {
                unsupported(element, "only unannotated, readable and writable String/int/boolean properties are supported");
            }
        }
    }

    private static void unsupported(ClassElement element, String reason) {
        throw new ProcessingException(element, "Runtime metadata prototype: " + reason);
    }
}
