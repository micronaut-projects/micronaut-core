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
package io.micronaut.dev.python;

import org.graalvm.polyglot.Engine;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * What the tests a test mode fixture compiles saw, recorded in the parent tier, which the generations delegate to.
 * The engines are held weakly, so that the records keep no generation reachable.
 */
public final class WarmEngineRecords {

    static final List<Entry> ENTRIES = new CopyOnWriteArrayList<>();

    private WarmEngineRecords() {
    }

    /**
     * Records what a test saw.
     *
     * @param test The test
     * @param engine The GraalPy engine of its application context
     * @param greeting What the Python bean returned
     * @param startMillis How long its application context took to start
     */
    public static void record(String test, Engine engine, String greeting, long startMillis) {
        ENTRIES.add(new Entry(test, new WeakReference<>(engine), System.identityHashCode(engine), greeting, startMillis));
    }

    record Entry(String test, WeakReference<Engine> engine, int engineId, String greeting, long startMillis) {
    }
}
