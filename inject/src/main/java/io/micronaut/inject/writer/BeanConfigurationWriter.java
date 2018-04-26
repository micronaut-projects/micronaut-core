/*
 * Copyright 2017-2018 original authors
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
package io.micronaut.inject.writer;

import io.micronaut.context.AbstractBeanConfiguration;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.io.service.ServiceDescriptorGenerator;
import io.micronaut.inject.BeanConfiguration;
import io.micronaut.inject.annotation.AnnotationMetadataWriter;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

/**
 * Writes configuration classes for configuration packages using ASM
 *
 * @author Graeme Rocher
 * @see BeanConfiguration
 * @see io.micronaut.context.annotation.Configuration
 * @since 1.0
 */
@Internal
public class BeanConfigurationWriter extends AbstractAnnotationMetadataWriter {

    /**
     * Suffix for generated configuration classes
     */
    public static final String CLASS_SUFFIX = "$BeanConfiguration";
    private final String packageName;
    private final String configurationClassName;
    private final String configurationClassInternalName;

    public BeanConfigurationWriter(String packageName, AnnotationMetadata annotationMetadata) {
        super(packageName + '.' + CLASS_SUFFIX, annotationMetadata);
        this.packageName = packageName;
        this.configurationClassName = targetClassType.getClassName();
        this.configurationClassInternalName = targetClassType.getInternalName();
    }

    @Override
    public void accept(ClassWriterOutputVisitor classWriterOutputVisitor) throws IOException {
        AnnotationMetadataWriter annotationMetadataWriter = getAnnotationMetadataWriter();
        if (annotationMetadataWriter != null) {
            annotationMetadataWriter.accept(classWriterOutputVisitor);
        }
        try (OutputStream outputStream = classWriterOutputVisitor.visitClass(configurationClassName)) {
            ClassWriter classWriter = generateClassBytes();
            outputStream.write(classWriter.toByteArray());
        }
        classWriterOutputVisitor.visitServiceDescriptor(BeanConfiguration.class, configurationClassName);
    }


    private ClassWriter generateClassBytes() {
        ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS);

        try {
            Class<AbstractBeanConfiguration> superType = AbstractBeanConfiguration.class;
            Type beanConfigurationType = Type.getType(superType);

            startClass(classWriter, configurationClassInternalName, beanConfigurationType);
            writeAnnotationMetadataStaticInitializer(classWriter);

            writeConstructor(classWriter);
            writeGetAnnotationMetadataMethod(classWriter);

        } catch (NoSuchMethodException e) {
            throw new ClassGenerationException("Error generating configuration class. Incompatible JVM or Micronaut version?: " + e.getMessage(), e);
        }

        return classWriter;
    }

    private void writeConstructor(ClassWriter classWriter) throws NoSuchMethodException {
        GeneratorAdapter cv = startConstructor(classWriter);

        // ALOAD 0
        cv.loadThis();
        // LDC "..package name.."
        cv.push(packageName);

        // INVOKESPECIAL AbstractBeanConfiguration.<init> (Ljava/lang/Package;)V
        invokeConstructor(cv, AbstractBeanConfiguration.class, String.class);

        // RETURN
        cv.visitInsn(RETURN);
        // MAXSTACK = 2
        // MAXLOCALS = 1
        cv.visitMaxs(2, 1);
    }
}
