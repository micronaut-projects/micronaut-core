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
package io.micronaut.context.python.runtime;

import io.micronaut.context.python.runtime.codec.PythonMetadataCodec;
import io.micronaut.core.annotation.Internal;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * The discovery catalogs of the runtime-generated classes visible to a class loader: one text resource per
 * compilation unit, listing each class, the identity of its saved model and which artifacts it contributes. Reading
 * a catalog loads no class, reads no model and starts no Python context. Catalogs from several sources are merged in
 * class-path order; a class listed twice with the same model identity is one class, listed twice with different
 * identities it is a conflict that is reported rather than resolved by order.
 *
 * @since 5.3.0
 */
@Internal
public final class PythonMetadataCatalog {

    /**
     * The catalog resource name.
     */
    public static final String RESOURCE = PythonMetadataCodec.RESOURCE_PATH + "catalog";

    /**
     * The first line of a catalog.
     */
    public static final String HEADER = "# micronaut-python-runtime-catalog 1";

    private static final Map<ClassLoader, PythonMetadataCatalog> CATALOGS = Collections.synchronizedMap(new WeakHashMap<>());

    private final List<Entry> entries;

    private PythonMetadataCatalog(List<Entry> entries) {
        this.entries = entries;
    }

    /**
     * The merged catalog of a class loader, read once per loader.
     *
     * @param classLoader The class loader
     * @return The catalog
     */
    public static PythonMetadataCatalog of(ClassLoader classLoader) {
        return CATALOGS.computeIfAbsent(classLoader, PythonMetadataCatalog::read);
    }

    /**
     * Formats a catalog.
     *
     * @param entries The entries
     * @return The catalog text
     */
    public static String format(List<Entry> entries) {
        StringBuilder text = new StringBuilder(HEADER).append('\n');
        for (Entry entry : entries) {
            text.append(entry.className()).append('\t').append(entry.identity()).append('\t')
                .append(entry.bean() ? "B" : "").append(entry.introspection() ? "I" : "").append('\n');
        }
        return text.toString();
    }

    private static PythonMetadataCatalog read(ClassLoader classLoader) {
        Map<String, Entry> merged = new LinkedHashMap<>();
        try {
            Enumeration<URL> resources = classLoader.getResources(RESOURCE);
            while (resources.hasMoreElements()) {
                URL url = resources.nextElement();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream(), StandardCharsets.UTF_8))) {
                    for (Entry entry : parse(reader, url.toString())) {
                        Entry existing = merged.putIfAbsent(entry.className(), entry);
                        if (existing != null && !existing.identity().equals(entry.identity())) {
                            throw new IllegalStateException("Conflicting Python runtime models of " + entry.className() + ": "
                                + existing.source() + " (" + existing.identity() + ") and " + url + " (" + entry.identity()
                                + "); remove the stale compilation output from the class path");
                        }
                    }
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read the Python runtime catalogs of " + classLoader, e);
        }
        return new PythonMetadataCatalog(List.copyOf(new ArrayList<>(merged.values())));
    }

    /**
     * Parses a catalog.
     *
     * @param text   The catalog text
     * @param source Where it was read from, for the diagnostics
     * @return The entries, in the order they are listed
     */
    public static List<Entry> parse(String text, String source) {
        try (BufferedReader reader = new BufferedReader(new java.io.StringReader(text))) {
            return parse(reader, source);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read the Python runtime catalog " + source, e);
        }
    }

    private static List<Entry> parse(BufferedReader reader, String source) throws IOException {
        String header = reader.readLine();
        if (!HEADER.equals(header)) {
            throw new IllegalStateException("Unsupported Python runtime catalog " + source + ": " + header);
        }
        List<Entry> entries = new ArrayList<>();
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            String[] parts = line.split("\t");
            if (parts.length != 3) {
                throw new IllegalStateException("Malformed Python runtime catalog line in " + source + ": " + line);
            }
            entries.add(new Entry(parts[0], parts[1], parts[2].contains("B"), parts[2].contains("I"), source));
        }
        return entries;
    }

    /**
     * @return The entries in class-path order
     */
    public List<Entry> entries() {
        return entries;
    }

    /**
     * A catalog entry.
     *
     * @param className     The wrapper class name
     * @param identity      The saved model identity
     * @param bean          Whether the class contributes a bean definition
     * @param introspection Whether the class contributes an introspection
     * @param source        The catalog the entry was read from
     */
    public record Entry(String className, String identity, boolean bean, boolean introspection, String source) {
    }
}
