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
package io.micronaut.inject.configuration;

import io.micronaut.inject.utils.JsonWriter;
import io.micronaut.inject.writer.ClassWriterOutputVisitor;
import io.micronaut.inject.writer.GeneratedFile;

import java.io.IOException;
import java.io.Writer;
import java.util.List;
import java.util.Optional;

/**
 * A {@link ConfigurationMetadataWriter} that writes out metadata in the format defined by
 * spring-configuration-metadata.json.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class JsonConfigurationMetadataWriter implements ConfigurationMetadataWriter {

    @Override
    public void write(ConfigurationMetadataBuilder metadataBuilder, ClassWriterOutputVisitor classWriterOutputVisitor) throws IOException {
        Optional<GeneratedFile> opt = classWriterOutputVisitor.visitMetaInfFile(getFileName(), metadataBuilder.getOriginatingElements());
        if (opt.isPresent()) {
            GeneratedFile file = opt.get();
            List<ConfigurationMetadata> configurations = metadataBuilder.getConfigurations();
            List<PropertyMetadata> properties = metadataBuilder.getProperties();
            JsonWriter json = new JsonWriter().beginObject();
            if (!configurations.isEmpty()) {
                json.name("groups").beginArray();
                configurations.forEach(configuration -> configuration.writeTo(json));
                json.endArray();
            }
            if (!properties.isEmpty()) {
                json.name("properties").beginArray();
                properties.forEach(property -> property.writeTo(json));
                json.endArray();
            }
            json.endObject();
            try (Writer writer = file.openWriter()) {
                json.writeTo(writer);
            }
        }
    }

    /**
     * @return The file name
     */
    protected String getFileName() {
        return "spring-configuration-metadata.json";
    }
}
