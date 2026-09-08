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
package io.micronaut.annotation.processing.test.jdt;

import javax.tools.JavaFileObject;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Loads classes produced by an in-memory compilation.
 *
 * @since 5.2.0
 */
public final class GeneratedClassLoader extends ClassLoader {

    private static final String CLASS_OUTPUT = "/CLASS_OUTPUT/";

    private final Collection<JavaFileObject> files = new ArrayList<>();

    /**
     * @param files The files produced by the compilation
     */
    public GeneratedClassLoader(Iterable<? extends JavaFileObject> files) {
        super(GeneratedClassLoader.class.getClassLoader());
        for (JavaFileObject file : files) {
            this.files.add(file);
        }
    }

    /**
     * @return The names of every class produced by the compilation, in the order they were written
     */
    public List<String> getGeneratedClassNames() {
        List<String> names = new ArrayList<>();
        for (JavaFileObject file : files) {
            String name = file.getName();
            int i = name.indexOf(CLASS_OUTPUT);
            if (i > -1 && name.endsWith(".class")) {
                names.add(name.substring(i + CLASS_OUTPUT.length(), name.length() - ".class".length())
                    .replace('/', '.'));
            }
        }
        return names;
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        String fileName = name.replace('.', '/') + ".class";
        JavaFileObject generated = files.stream()
            .filter(it -> it.getName().endsWith(fileName))
            .findFirst().orElse(null);
        if (generated != null) {
            try (InputStream io = generated.openInputStream()) {
                byte[] bytes = io.readAllBytes();
                return defineClass(name, bytes, 0, bytes.length);
            } catch (IOException e) {
                return super.findClass(name);
            }
        }
        return super.findClass(name);
    }
}
