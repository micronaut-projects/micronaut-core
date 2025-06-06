/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.http.netty;

import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.annotate.TargetElement;

import java.util.function.BooleanSupplier;

// Netty CleanerJava24 references the memory segment API, and when that's reachable, there's a
// build error with native image. These substitutions fix the build and disable the cleaner.

@TargetClass(className = "io.netty.util.internal.CleanerJava24", onlyWith = Jdk19OrLater.class)
final class CleanerJava24 {
    @Substitute
    @TargetElement(name = "isSupported")
    static boolean isSupported() {
        return false;
    }
}

@TargetClass(className = "jdk.internal.foreign.MemorySessionImpl", onlyWith = Jdk19OrLater.class)
final class MemorySessionImpl {
}

@TargetClass(className = "jdk.internal.misc.ScopedMemoryAccess", onlyWith = Jdk19OrLater.class)
final class ScopedMemoryAccess {
    @Substitute
    @TargetElement(name = "closeScope0")
    boolean closeScope0Unsupported(MemorySessionImpl memorySessionImpl) {
        throw new UnsupportedOperationException();
    }
}

final class Jdk19OrLater implements BooleanSupplier {
    @Override
    public boolean getAsBoolean() {
        int v = Integer.getInteger("java.specification.version", 17);
        return v >= 19 && v < 25; // fixed in 25 according to graal team
    }
}
