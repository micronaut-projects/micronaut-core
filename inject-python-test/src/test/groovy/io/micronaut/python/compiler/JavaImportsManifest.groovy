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
package io.micronaut.python.compiler

import io.micronaut.python.processing.PythonAnnotationProcessor

/**
 * Reads the manifests of the Java packages, types and annotations a compilation's Python sources import,
 * which the compiler writes next to the sources instead of generating Python modules for them.
 */
class JavaImportsManifest {

    /** The Python module names and their Java package names. */
    final Map<String, String> packages
    /** The Python module names of the Java types imported as modules and their binary names. */
    final Map<String, String> types
    /** By Python module name, the imported members: simple name to {@code [binary name, kind]}. */
    final Map<String, Map<String, List<String>>> members

    private JavaImportsManifest(Map<String, String> packages, Map<String, String> types, Map<String, Map<String, List<String>>> members) {
        this.packages = packages
        this.types = types
        this.members = members
    }

    /** The manifest files a compilation wrote to the given target directory. */
    static List<File> files(File targetDir) {
        def srcDir = new File(targetDir, "META-INF/${PythonAnnotationProcessor.APPLICATION_SRC_PATH}")
        (srcDir.listFiles() ?: new File[0])
            .findAll { it.name.startsWith(PythonAnnotationProcessor.JAVA_IMPORTS_MANIFEST_PREFIX) && it.name.endsWith('.py') }
            .sort { it.name }
    }

    /** The merged manifests of a target directory. */
    static JavaImportsManifest read(File targetDir) {
        merge(files(targetDir).collect { parseManifest(it.text) })
    }

    /** The merged manifests the class loader serves, found through the file lists of the virtual file system. */
    static JavaImportsManifest read(ClassLoader classLoader) {
        List<Map> manifests = []
        classLoader.getResources("META-INF/${PythonAnnotationProcessor.APPLICATION_PATH}fileslist.txt").each { url ->
            url.text.readLines().findAll { it.contains("/src/${PythonAnnotationProcessor.JAVA_IMPORTS_MANIFEST_PREFIX}") && it.endsWith('.py') }.each { entry ->
                def resource = classLoader.getResource(entry.startsWith('/') ? entry.substring(1) : entry)
                if (resource != null) {
                    manifests << parseManifest(resource.text)
                }
            }
        }
        merge(manifests)
    }

    private static JavaImportsManifest merge(List<Map> manifests) {
        Map<String, String> packages = [:]
        Map<String, String> types = [:]
        Map<String, Map<String, List<String>>> members = [:]
        manifests.each { manifest ->
            packages.putAll(manifest.packages as Map)
            types.putAll(manifest.types as Map)
            (manifest.members as Map).each { module, entries ->
                members.computeIfAbsent(module as String) { [:] }.putAll(entries as Map)
            }
        }
        new JavaImportsManifest(packages, types, members)
    }

    /**
     * Parses the manifest module: three dict literals ({@code PACKAGES}, {@code TYPES}, {@code MEMBERS}) in JSON
     * syntax, read without a JSON library on the test class path.
     */
    static Map parseManifest(String text) {
        Map manifest = [:]
        ['PACKAGES', 'TYPES', 'MEMBERS'].each { name ->
            int start = text.indexOf("\n${name} = ")
            assert start >= 0 : "no ${name} in the manifest"
            def parser = new JsonParser(text)
            parser.position = start + name.length() + 4
            manifest[name.toLowerCase()] = parser.value()
        }
        manifest
    }

    private static class JsonParser {
        final String text
        int position = 0

        JsonParser(String text) {
            this.text = text
        }

        void skipSpace() {
            while (position < text.length() && Character.isWhitespace(text.charAt(position))) {
                position++
            }
        }

        Object value() {
            skipSpace()
            char c = text.charAt(position)
            if (c == '{' as char) {
                position++
                Map<String, Object> map = new LinkedHashMap<>()
                skipSpace()
                if (text.charAt(position) == '}' as char) {
                    position++
                    return map
                }
                while (true) {
                    skipSpace()
                    String key = string()
                    skipSpace()
                    assert text.charAt(position) == ':' as char
                    position++
                    map[key] = value()
                    skipSpace()
                    char next = text.charAt(position++)
                    if (next == '}' as char) {
                        return map
                    }
                    assert next == ',' as char
                }
            }
            if (c == '[' as char) {
                position++
                List<Object> list = []
                skipSpace()
                if (text.charAt(position) == ']' as char) {
                    position++
                    return list
                }
                while (true) {
                    list << value()
                    skipSpace()
                    char next = text.charAt(position++)
                    if (next == ']' as char) {
                        return list
                    }
                    assert next == ',' as char
                }
            }
            if (c == '"' as char) {
                return string()
            }
            throw new IllegalArgumentException("unexpected character [${c}] in manifest at ${position}")
        }

        String string() {
            assert text.charAt(position) == '"' as char
            position++
            StringBuilder builder = new StringBuilder()
            while (true) {
                char c = text.charAt(position++)
                if (c == '"' as char) {
                    return builder.toString()
                }
                if (c == '\\' as char) {
                    c = text.charAt(position++)
                }
                builder.append(c)
            }
        }
    }

    /** The {@code [binary name, kind]} of a member, or null. */
    List<String> member(String module, String name) {
        members[module]?.get(name)
    }
}
