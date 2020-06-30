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

import io.micronaut.core.io.Writable;
import io.micronaut.inject.writer.ClassWriterOutputVisitor;
import io.micronaut.inject.writer.GeneratedFile;

import java.io.IOException;
import java.io.Writer;
import java.util.Iterator;
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
    public void write(ConfigurationMetadataBuilder<?> metadataBuilder, ClassWriterOutputVisitor classWriterOutputVisitor) throws IOException {
        Optional<GeneratedFile> opt = classWriterOutputVisitor.visitMetaInfFile(getFileName());
        if (opt.isPresent()) {
            GeneratedFile file = opt.get();
            List<ConfigurationMetadata> configurations = metadataBuilder.getConfigurations();
            List<PropertyMetadata> properties = metadataBuilder.getProperties();
            try (Writer writer = file.openWriter()) {
                writer.write('{');
                boolean hasGroups = !configurations.isEmpty();
                boolean hasProps = !properties.isEmpty();
                if (hasGroups) {
                    writeMetadata("groups", configurations, writer);
                    if (hasProps) {
                        writer.write(',');
                    }
                }
                if (hasProps) {
                    writeMetadata("properties", properties, writer);
                }
                writer.write('}');
            }
        }
    }

    /**
     * @return The file name
     */
    protected String getFileName() {
        return "spring-configuration-metadata.json";
    }

    private void writeMetadata(String attr, List<? extends Writable> configurations, Writer writer) throws IOException {
        writer.write('"');
        writer.write(attr);
        writer.write("\":[");
        Iterator<? extends Writable> i = configurations.iterator();
        while (i.hasNext()) {
            Writable metadata = i.next();
            metadata.writeTo(writer);
            if (i.hasNext()) {
                writer.write(',');
            }
        }
        writer.write(']');
    }
}
