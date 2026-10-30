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
package io.micronaut.dev;

import io.micronaut.dev.loader.GenerationClassLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;

/**
 * The class files the fast path writes into a running generation's snapshot ahead of a redefinition, kept with the
 * versions they replace, so that a redefinition the JVM refuses leaves the snapshot as it was before the restart
 * that follows.
 *
 * <p>The replacement holds the generation's {@link GenerationClassLoader#classFileLock() class file lock} from its
 * construction until it is {@link #close() closed}, so that the files written, the redefinition and the rollback form
 * one change: no thread loads a class through the generation in between, which could otherwise define a changed class
 * from a file the rollback then restores, leaving the generation with that class new and the others old.</p>
 */
final class ClassFileReplacement implements AutoCloseable {

    // the runtime's logger: this is part of the runtime's reloads
    private static final Logger LOG = LoggerFactory.getLogger(DevRuntime.class);

    private final GenerationClassLoader generation;
    private final Deque<Map.Entry<String, byte[]>> replaced = new ArrayDeque<>();

    /**
     * Takes the generation's class file lock, which {@link #close()} releases.
     *
     * @param generation The generation whose snapshot is changed
     */
    ClassFileReplacement(GenerationClassLoader generation) {
        this.generation = generation;
        generation.classFileLock().lock();
    }

    /**
     * Writes the new version of a class file into the generation's snapshot.
     *
     * @param name The binary name
     * @param before The version the snapshot holds, which {@link #rollBack()} restores
     * @param after The new version
     * @throws IOException if the file cannot be written, or the snapshot has no class file of that name
     */
    void replace(String name, byte[] before, byte[] after) throws IOException {
        if (!generation.replaceClassFile(name, after)) {
            throw new IOException("Generation " + generation.generation() + " has no class file for " + name);
        }
        replaced.push(Map.entry(name, before));
    }

    /**
     * Restores the versions the replaced class files had, in the reverse order. A file that cannot be restored is
     * logged: the restart that follows a refused redefinition builds a new generation anyway.
     */
    void rollBack() {
        while (!replaced.isEmpty()) {
            Map.Entry<String, byte[]> entry = replaced.pop();
            try {
                generation.replaceClassFile(entry.getKey(), entry.getValue());
            } catch (IOException e) {
                LOG.debug("Cannot restore the class file of {} in generation {}", entry.getKey(), generation.generation(), e);
            }
        }
    }

    /**
     * Releases the generation's class file lock: a class loaded from now on is defined from the files as the
     * replacement, or its rollback, left them.
     */
    @Override
    public void close() {
        generation.classFileLock().unlock();
    }
}
