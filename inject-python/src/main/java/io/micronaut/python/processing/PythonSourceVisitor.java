/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
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
