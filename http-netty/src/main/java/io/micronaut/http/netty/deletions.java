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

import com.oracle.svm.core.annotate.Delete;
import com.oracle.svm.core.annotate.TargetClass;

import java.util.function.BooleanSupplier;

// Netty CleanerJava24 references these two classes, and they reference ScopedMemoryAccess, which
// is not implemented in native image. That leads to a link error even on JVMs where MemorySegment
// is still in preview. We need to delete these APIs entirely to make the build work, and suppress
// the build time error with --report-unsupported-elements-at-runtime

@TargetClass(className = "java.lang.foreign.MemorySegment", onlyWith = Jdk19OrLater.class)
@Delete
final class MemorySegmentDeletion {
}

@TargetClass(className = "java.lang.foreign.Arena", onlyWith = Jdk19OrLater.class)
@Delete
final class ArenaDeletion {
}

final class Jdk19OrLater implements BooleanSupplier {
    @Override
    public boolean getAsBoolean() {
        return Integer.getInteger("java.specification.version", 17) >= 19;
    }
}
