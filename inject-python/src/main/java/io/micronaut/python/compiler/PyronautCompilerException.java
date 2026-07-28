/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.micronaut.python.compiler;

/** Exception intentionally propagated from a compilation visitor to its caller. */
public class PyronautCompilerException extends RuntimeException {
    /**
     * @param message exception message
     */
    public PyronautCompilerException(String message) {
        super(message);
    }
}
