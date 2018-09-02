package io.micronaut.build.asciidoc

import io.micronaut.inject.configuration.ConfigurationMetadata
import io.micronaut.inject.configuration.ConfigurationMetadataBuilder
import io.micronaut.inject.configuration.ConfigurationMetadataWriter
import io.micronaut.inject.configuration.PropertyMetadata
import io.micronaut.inject.writer.ClassWriterOutputVisitor
import io.micronaut.inject.writer.GeneratedFile

class AsciiDocPropertyReferenceWriter implements ConfigurationMetadataWriter {
    @Override
    void write(ConfigurationMetadataBuilder<?> metadataBuilder, ClassWriterOutputVisitor classWriterOutputVisitor) throws IOException {

        List<PropertyMetadata> props = new ArrayList<>(metadataBuilder.properties).unique { it.path }

        List<ConfigurationMetadata> configs = new ArrayList(metadataBuilder.configurations).sort({
            it.name.length()
        }).reverse()

        Map<ConfigurationMetadata, List<PropertyMetadata>> map = props.groupBy { PropertyMetadata pm ->
            configs.find() { ConfigurationMetadata cm -> pm.path.startsWith(cm.name) }
        }
        if (map) {

            Optional<GeneratedFile> file = classWriterOutputVisitor.visitMetaInfFile("config-properties.adoc")

            if (file.isPresent()) {
                BufferedWriter w = new BufferedWriter(file.get().openWriter())

                try {
                    for (entry in map) {
                        ConfigurationMetadata cm = entry.key

                        if (cm == null) continue

                        if (entry.value) {
                            w << """
.Configuration Properties for api:$cm.type[]
|===
|Property |Type |Description

"""

                        for (PropertyMetadata pm in entry.value) {
                            String description = pm.description ?: ''.trim()
                            if (description.startsWith("@param")) {
                                def match = description =~ /@param\s*\w+\s*(.+)/

                                if (match.find()) {
                                    description = match.group(1)
                                }
                            } else if (description.contains("@param")) {
                                description = description.substring(0, description.indexOf("@param")).trim()
                            }

                            String type = pm.type

                            if (type.startsWith("io.micronaut")) {
                                type = "api:$type[]"
                            }

                            w << """
| `$pm.path`
| $type
| ${description}
"""
                        }
                        w << """
|===
"""
                        }

                    }
                } finally {
                    w.close()
                }

            }


        }


    }
}
