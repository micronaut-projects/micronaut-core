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
import java.util.ArrayList;
import java.util.List;

/**
 * Emits the outputs of the model backends: the saved model of every selected class, the class files of the
 * build-time backend and the discovery catalog of the runtime backend.
 *
 * @since 5.3.0
 */
@Internal
public final class PythonMetadataOutputs {

    private final PythonMetadataBackend backend;
    private final List<PythonMetadataCatalog.Entry> catalog = new ArrayList<>();
    private final List<ClassElement> origins = new ArrayList<>();

    /**
     * @param backend The backend
     */
    public PythonMetadataOutputs(PythonMetadataBackend backend) {
        this.backend = backend;
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
        if (backend == PythonMetadataBackend.MODEL_BUILD_TIME) {
            if (classModel.beanDefinition() != null) {
                if (!classModel.beanDefinition().executableMethods().isEmpty()) {
                    writeClass(classElement, classModel.beanDefinition().definitionClassName() + "$Exec", PythonRuntimeMetadata.executableMethodsBytes(classModel),
                        null, visitorContext);
                }
                writeClass(classElement, classModel.beanDefinition().definitionClassName(), PythonRuntimeMetadata.definitionBytes(classModel),
                    BeanDefinitionReference.class, visitorContext);
            }
            if (classModel.introspection() != null) {
                writeClass(classElement, classModel.introspection().introspectionClassName(), PythonRuntimeMetadata.introspectionBytes(classModel),
                    BeanIntrospectionReference.class, visitorContext);
            }
        } else {
            catalog.add(new PythonMetadataCatalog.Entry(classModel.className(), identity, classModel.beanDefinition() != null,
                classModel.introspection() != null, ""));
        }
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
        ClassElement[] originatingElements = origins.toArray(ClassElement[]::new);
        GeneratedFile file = visitorContext.visitMetaInfFile(PythonMetadataCatalog.RESOURCE.substring("META-INF/".length()), originatingElements)
            .orElseThrow(() -> new ProcessingException(originatingElements[0], "Cannot write the metadata catalog: no output"));
        try (Writer writer = file.openWriter()) {
            writer.write(PythonMetadataCatalog.format(catalog));
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
