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
package io.micronaut.inject.annotation;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

/**
 * A loader defining its own copy of a class of the test class path, written in Java so that no Groovy call site or
 * meta class refers to it or to the classes it defines, and a test can tell that it is collected.
 */
public final class OwnCopyLoader extends ClassLoader {

    /**
     * @param parent The parent loader
     */
    public OwnCopyLoader(ClassLoader parent) {
        super(parent);
    }

    /**
     * Defines this loader's own copy of a class.
     *
     * @param name The name of the class
     * @return The copy
     */
    public Class<?> define(String name) {
        try (InputStream in = OwnCopyLoader.class.getClassLoader().getResourceAsStream(name.replace('.', '/') + ".class")) {
            byte[] bytes = in.readAllBytes();
            return defineClass(name, bytes, 0, bytes.length);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
