/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.micronaut.python.processing;

import io.micronaut.core.annotation.Experimental;

import java.util.List;

/**
 * Python source metadata made available during annotation processing.
 *
 * @param name source name
 * @param packageName source package name
 * @param calls calls discovered in the source
 */
@Experimental
public record PythonSource(String name, String packageName, List<PythonCall> calls) {
    public PythonSource {
        calls = List.copyOf(calls);
    }
}
