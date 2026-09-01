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
package io.micronaut.inject.writer;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.io.service.ServiceAggregator;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Build-time entry point that collects the module {@link ServiceAggregator}s from an application's
 * classpath and writes the single {@link io.micronaut.core.io.service.ServiceAggregatorRoot} for it.
 *
 * <p>Intended to be called from a Gradle or Maven plugin after compilation, which is the only place
 * the complete classpath is known. It reads nothing but the compiled output, so it does not affect
 * incremental compilation of the modules themselves.</p>
 *
 * @author Denis Stepanov
 * @since 5.2.0
 */
@Experimental
public final class ServiceAggregatorRootBuilder {

    private static final String AGGREGATOR_RESOURCE = "META-INF/services/" + ServiceAggregator.SERVICE_NAME;

    private ServiceAggregatorRootBuilder() {
    }

    /**
     * Finds every module aggregator advertised on the given classpath.
     *
     * @param classpath The jars and class directories of the application
     * @return The aggregator class names, in classpath order
     * @throws IOException If an entry cannot be read
     */
    public static List<String> findAggregators(List<Path> classpath) throws IOException {
        Set<String> names = new LinkedHashSet<>();
        for (Path entry : classpath) {
            if (Files.isDirectory(entry)) {
                Path resource = entry.resolve(AGGREGATOR_RESOURCE);
                if (Files.isRegularFile(resource)) {
                    try (InputStream in = Files.newInputStream(resource)) {
                        read(in, names);
                    }
                }
            } else if (Files.isRegularFile(entry)) {
                try (ZipFile zip = new ZipFile(entry.toFile())) {
                    ZipEntry resource = zip.getEntry(AGGREGATOR_RESOURCE);
                    if (resource != null) {
                        try (InputStream in = zip.getInputStream(resource)) {
                            read(in, names);
                        }
                    }
                }
            }
        }
        return new ArrayList<>(names);
    }

    /**
     * Writes the root class and its {@code META-INF/services} entry into the given output directory.
     *
     * @param outputDirectory      The directory to write into, typically the application's classes
     *                             output
     * @param rootClassName        The fully qualified name to give the generated root
     * @param aggregatorClassNames The module aggregators found by {@link #findAggregators}
     * @throws IOException If the output cannot be written
     */
    public static void write(Path outputDirectory,
                             String rootClassName,
                             List<String> aggregatorClassNames) throws IOException {
        var writer = new ServiceAggregatorRootWriter(rootClassName, aggregatorClassNames, null);
        Path classFile = outputDirectory.resolve(rootClassName.replace('.', '/') + ".class");
        Files.createDirectories(classFile.getParent());
        Files.write(classFile, writer.generateClassBytes());

        Path serviceFile = outputDirectory.resolve(ServiceAggregatorRootWriter.serviceResourcePath());
        Files.createDirectories(serviceFile.getParent());
        Files.writeString(serviceFile, writer.serviceEntry() + System.lineSeparator(), StandardCharsets.UTF_8);
    }

    private static void read(InputStream in, Set<String> names) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line = reader.readLine();
            while (line != null) {
                int comment = line.indexOf('#');
                if (comment > -1) {
                    line = line.substring(0, comment);
                }
                line = line.trim();
                if (!line.isEmpty()) {
                    names.add(line);
                }
                line = reader.readLine();
            }
        }
    }

}
