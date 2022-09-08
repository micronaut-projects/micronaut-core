/*
 * Copyright 2017-2021 original authors
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

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.ConfigurationReader;
import io.micronaut.context.annotation.EachProperty;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.inject.configuration.ConfigurationMetadataBuilder;
import io.micronaut.inject.configuration.ConfigurationMetadataWriter;
import io.micronaut.inject.visitor.TypeElementVisitor;

import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.TypeElement;
import java.io.IOException;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.Set;

/**
 * Handles Configuration metadata generation.
 *
 * @author graemerocher
 * @since 3.5.1
 */
public class ConfigurationMetadataProcessor extends AbstractInjectAnnotationProcessor {

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return CollectionUtils.setOf(
                ConfigurationReader.class.getName(),
                ConfigurationProperties.class.getName(),
                EachProperty.class.getName()
        );
    }

    @Override
    protected TypeElementVisitor.VisitorKind getVisitorKind() {
        return TypeElementVisitor.VisitorKind.AGGREGATING;
    }

    @Override
    protected String getIncrementalProcessorType() {
        return GRADLE_PROCESSING_AGGREGATING;
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (roundEnv.processingOver()) {
            writeConfigurationMetadata();
        }
        return false;
    }

    private void writeConfigurationMetadata() {
        try {
            ConfigurationMetadataBuilder builder = ConfigurationMetadataBuilder.INSTANCE;
            if (builder.hasMetadata()) {
                ServiceLoader<ConfigurationMetadataWriter> writers = ServiceLoader.load(ConfigurationMetadataWriter.class, getClass().getClassLoader());

                try {
                    for (ConfigurationMetadataWriter writer : writers) {
                        writeConfigurationMetadata(builder, writer);
                    }
                } catch (ServiceConfigurationError e) {
                    warning("Unable to load ConfigurationMetadataWriter due to : %s", e.getMessage());
                }
            }
        } finally {
            ConfigurationMetadataBuilder.INSTANCE = new ConfigurationMetadataBuilder();
        }

    }

    private void writeConfigurationMetadata(ConfigurationMetadataBuilder metadataBuilder, ConfigurationMetadataWriter writer) {
        try {
            writer.write(metadataBuilder, classWriterOutputVisitor);
        } catch (IOException e) {
            warning("Error occurred writing configuration metadata: %s", e.getMessage());
        }
    }
}
