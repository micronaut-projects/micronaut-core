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

import io.micronaut.context.python.runtime.PythonMetadataCatalog;
import io.micronaut.context.python.runtime.PythonRuntimeMetadata;
import io.micronaut.context.python.runtime.codec.PythonMetadataCodec;
import io.micronaut.context.python.runtime.model.BeanDefinitionModel;
import io.micronaut.context.python.runtime.model.ClassModel;
import io.micronaut.context.python.runtime.model.PythonMetadataModel;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.beans.BeanIntrospectionReference;
import io.micronaut.inject.BeanDefinitionReference;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.processing.ProcessingException;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.inject.writer.GeneratedFile;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Emits the outputs of the model backends: the saved model of every selected class, the class files of the
 * build-time backend and the discovery catalog of the runtime backend.
 *
 * @since 5.3.0
 */
@Internal
public final class PythonMetadataOutputs {

    private final PythonMetadataBackend backend;
    private final boolean incremental;
    private final List<PythonMetadataCatalog.Entry> catalog = new ArrayList<>();
    private final List<ClassElement> origins = new ArrayList<>();

    /**
     * @param backend     The backend
     * @param incremental Whether only the affected classes of the compilation are processed
     */
    public PythonMetadataOutputs(PythonMetadataBackend backend, boolean incremental) {
        this.backend = backend;
        this.incremental = incremental;
    }

    /**
     * Writes the model of a class, and its classes or catalog entry.
     *
     * @param classElement   The class
     * @param model          The model
     * @param visitorContext The visitor context
     */
    public void write(ClassElement classElement, PythonMetadataModel model, VisitorContext visitorContext) {
        ClassModel classModel = model.classModel();
        byte[] bytes = PythonMetadataCodec.encode(model);
        String identity = PythonMetadataCodec.identity(bytes);
        GeneratedFile file = visitorContext.visitMetaInfFile(PythonMetadataCodec.modelResource(classModel.className()).substring("META-INF/".length()), classElement)
            .orElseThrow(() -> new ProcessingException(classElement, "Cannot write the metadata model: no output"));
        try (OutputStream out = file.openOutputStream()) {
            out.write(bytes);
        } catch (IOException e) {
            throw new ProcessingException(classElement, "Cannot write the metadata model: " + e.getMessage(), e);
        }
        origins.add(classElement);
        checkNoStaleClasses(classElement, classModel, visitorContext);
        if (backend == PythonMetadataBackend.MODEL_BUILD_TIME) {
            for (BeanDefinitionModel definition : classModel.beanDefinitions()) {
                if (!definition.executableMethods().isEmpty()) {
                    writeClass(classElement, definition.definitionClassName() + "$Exec", PythonRuntimeMetadata.executableMethodsBytes(classModel, definition),
                        null, visitorContext);
                }
                writeClass(classElement, definition.definitionClassName(), PythonRuntimeMetadata.definitionBytes(classModel, definition),
                    BeanDefinitionReference.class, visitorContext);
            }
            if (classModel.introspection() != null) {
                writeClass(classElement, classModel.introspection().introspectionClassName(), PythonRuntimeMetadata.introspectionBytes(classModel),
                    BeanIntrospectionReference.class, visitorContext);
            }
        } else {
            catalog.add(new PythonMetadataCatalog.Entry(classModel.className(), identity, !classModel.beanDefinitions().isEmpty(),
                classModel.introspection() != null, ""));
        }
    }

    /**
     * A class whose metadata the runtime backend generates must have no metadata class in the output: one left by an
     * earlier compilation with another backend would be discovered as well, and the two would claim the same bean.
     * The build cannot be trusted to have removed it, so the compilation stops and names the file.
     */
    private void checkNoStaleClasses(ClassElement classElement, ClassModel classModel, VisitorContext visitorContext) {
        if (backend != PythonMetadataBackend.MODEL_RUNTIME) {
            return;
        }
        Path classesOutput = visitorContext.getClassesOutputPath().orElse(null);
        if (classesOutput == null) {
            return;
        }
        List<String> names = new ArrayList<>();
        for (BeanDefinitionModel definition : classModel.beanDefinitions()) {
            names.add(definition.definitionClassName());
            names.add(definition.definitionClassName() + "$Exec");
        }
        if (classModel.introspection() != null) {
            names.add(classModel.introspection().introspectionClassName());
        }
        for (String name : names) {
            Path stale = classesOutput.resolve(name.replace('.', '/') + ".class");
            if (Files.isRegularFile(stale)) {
                throw new ProcessingException(classElement, "The output already holds " + stale
                    + ", generated for " + classModel.className() + " by another metadata backend. Clean the output before"
                    + " compiling with " + PythonMetadataBackend.BACKEND_OPTION + "=model-runtime");
            }
        }
    }

    /**
     * The catalog an incremental compilation writes: the entries of the classes it processed, and the entries the
     * previous compilation wrote for the classes it did not, whose models are still in the output. A compilation
     * that processes every class writes only what it processed, so an entry of a class that is gone goes with it.
     */
    private List<PythonMetadataCatalog.Entry> merged(VisitorContext visitorContext) {
        Path classesOutput = visitorContext.getClassesOutputPath().orElse(null);
        if (classesOutput == null) {
            return catalog;
        }
        Path existing = classesOutput.resolve(PythonMetadataCatalog.RESOURCE);
        if (!Files.isRegularFile(existing)) {
            return catalog;
        }
        Set<String> processed = new LinkedHashSet<>();
        catalog.forEach(entry -> processed.add(entry.className()));
        List<PythonMetadataCatalog.Entry> entries = new ArrayList<>(catalog);
        try {
            for (PythonMetadataCatalog.Entry entry : PythonMetadataCatalog.parse(Files.readString(existing, StandardCharsets.UTF_8), existing.toString())) {
                if (processed.contains(entry.className())
                    || !Files.isRegularFile(classesOutput.resolve(PythonMetadataCodec.modelResource(entry.className())))) {
                    continue;
                }
                entries.add(entry);
            }
        } catch (IOException e) {
            throw new ProcessingException(origins.get(0), "Cannot read the metadata catalog of the previous compilation: " + e.getMessage(), e);
        }
        entries.sort(Comparator.comparing(PythonMetadataCatalog.Entry::className));
        return entries;
    }

    /**
     * Writes the catalog of the runtime backend, once every class of the compilation was written.
     *
     * @param visitorContext The visitor context
     */
    public void finish(VisitorContext visitorContext) {
        if (backend != PythonMetadataBackend.MODEL_RUNTIME || catalog.isEmpty()) {
            return;
        }
        List<PythonMetadataCatalog.Entry> entries = incremental ? merged(visitorContext) : catalog;
        ClassElement[] originatingElements = origins.toArray(ClassElement[]::new);
        GeneratedFile file = visitorContext.visitMetaInfFile(PythonMetadataCatalog.RESOURCE.substring("META-INF/".length()), originatingElements)
            .orElseThrow(() -> new ProcessingException(originatingElements[0], "Cannot write the metadata catalog: no output"));
        try (Writer writer = file.openWriter()) {
            writer.write(PythonMetadataCatalog.format(entries));
        } catch (IOException e) {
            throw new ProcessingException(originatingElements[0], "Cannot write the metadata catalog: " + e.getMessage(), e);
        }
    }

    private static void writeClass(ClassElement classElement, String className, byte[] bytes, @Nullable Class<?> service, VisitorContext visitorContext) {
        if (service != null) {
            visitorContext.visitServiceDescriptor(service, className, classElement);
        }
        try (OutputStream out = visitorContext.visitClass(className, classElement)) {
            out.write(bytes);
        } catch (IOException e) {
            throw new ProcessingException(classElement, "Cannot write " + className + ": " + e.getMessage(), e);
        }
    }
}
