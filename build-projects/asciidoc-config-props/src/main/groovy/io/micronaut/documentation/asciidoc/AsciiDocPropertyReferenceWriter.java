package io.micronaut.documentation.asciidoc;

import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.inject.configuration.ConfigurationMetadata;
import io.micronaut.inject.configuration.ConfigurationMetadataBuilder;
import io.micronaut.inject.configuration.ConfigurationMetadataWriter;
import io.micronaut.inject.configuration.PropertyMetadata;
import io.micronaut.inject.writer.ClassWriterOutputVisitor;
import io.micronaut.inject.writer.GeneratedFile;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Writes out the asciidoc configuration property reference.
 *
 * @author graemerocher
 * @since 1.0
 */
public class AsciiDocPropertyReferenceWriter implements ConfigurationMetadataWriter {

    private static final Pattern PARAM_PATTERN = Pattern.compile("@param\\s*\\w+\\s*(.+)");
    private static final ConfigurationMetadata EMPTY = new ConfigurationMetadata() {
        @Override
        public String getName() {
            return "EMPTY";
        }
    };

    @Override
    public void write(ConfigurationMetadataBuilder<?> metadataBuilder, ClassWriterOutputVisitor classWriterOutputVisitor) throws IOException {

        List<PropertyMetadata> props = new ArrayList<>(metadataBuilder.getProperties())
                .stream()
                .filter(distinctByKey(PropertyMetadata::getPath)).collect(Collectors.toList());

        List<ConfigurationMetadata> configs = new ArrayList<>(metadataBuilder.getConfigurations())
                .stream()
                .sorted(Comparator.comparing(ConfigurationMetadata::getName))
                .collect(Collectors.toList());

        Collections.reverse(configs);

        Map<ConfigurationMetadata, List<PropertyMetadata>> map = props.stream().collect(Collectors.groupingBy(propertyMetadata ->
                configs.stream().filter(cm -> propertyMetadata.getPath().startsWith(cm.getName())).findFirst().orElseGet(() -> {
//                    System.err.println("WARNING: No configuration found for property " + propertyMetadata.getPath());
                    return EMPTY;
                }))
        );

        if (CollectionUtils.isNotEmpty(map)) {

            Optional<GeneratedFile> file = classWriterOutputVisitor.visitMetaInfFile("config-properties.adoc");

            if (file.isPresent()) {

                try (BufferedWriter w = new BufferedWriter(file.get().openWriter())) {

                    for (Map.Entry<ConfigurationMetadata, List<PropertyMetadata>> entry : map.entrySet()) {
                        ConfigurationMetadata cm = entry.getKey();

                        if (cm == null || cm == EMPTY) continue;

                        if (entry.getValue() != null) {
                            writeFragmentLink(w, cm.getType());
                            w.newLine();
                            w.append(".Configuration Properties for api:").append(cm.getType()).append("[]");
                            w.newLine();
                            w.append("|===");
                            w.newLine();
                            w.append("|Property |Type |Description");
                            w.newLine();

                            for (PropertyMetadata pm : entry.getValue()) {
                                //ignore setters of configuration properties classes
                                if (pm.getType().equals(cm.getType())) {
                                    continue;
                                }

                                String path = pm.getPath();
                                String description = pm.getDescription();

                                if (path.contains("..")) continue;
                                if (StringUtils.isEmpty(description)) description = "";

                                description = description.trim();

                                if (description.startsWith("@param")) {
                                    Matcher match = PARAM_PATTERN.matcher(description);
                                    if (match.find()) {
                                        description = match.group(1);
                                    }
                                } else if (description.contains("@param")) {
                                    description = description.substring(0, description.indexOf("@param")).trim();
                                }

                                String type = pm.getType();

                                if (type.startsWith("io.micronaut")) {
                                    type = "api:" + type + "[]";
                                }

                                w.newLine();
                                w.append("| `").append(path).append('`');
                                w.newLine();
                                w.append("|").append(type);
                                w.newLine();
                                w.append("|").append(description);
                                w.newLine();
                                w.newLine();
                            }

                            w.newLine();
                            w.append("|===");
                            w.newLine();
                            w.append("<<<");
                        }

                    }
                }
            }
        }
    }

    private void writeFragmentLink(BufferedWriter w, String type) throws IOException {
        w.newLine();
        w.append("++++");
        w.newLine();
        w.append("<a id=\"");
        w.append(type);
        w.append("\" href=\"#");
        w.append(type);
        w.append("\">&#128279;</a>");
        w.newLine();
        w.append("++++");
    }

    private <T> Predicate<T> distinctByKey(
            Function<? super T, ?> ke) {
        Map<Object, Boolean> seen = new ConcurrentHashMap<>();
        return t -> seen.putIfAbsent(ke.apply(t), Boolean.TRUE) == null;
    }
}

