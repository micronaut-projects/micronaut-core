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
package io.micronaut.python.processing;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.inject.visitor.VisitorContext;

/** Visits Python source metadata supplied for an individual compilation. */
@Experimental
public interface PythonSourceVisitor {
    /**
     * Visits one parsed Python source file.
     *
     * @param source the source metadata
     * @param context the visitor context
     */
    void visit(PythonSource source, VisitorContext context);

    /**
     * Invoked after every Python source file has been visited.
     *
     * @param context the visitor context
     */
    default void finish(VisitorContext context) {
    }
}
