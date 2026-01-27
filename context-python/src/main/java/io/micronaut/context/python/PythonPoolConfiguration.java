/*
 * Copyright 2017-2026 original authors
 */
package io.micronaut.context.python;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.bind.annotation.Bindable;

/**
 * Configuration for the PythonPool.
 */
@ConfigurationProperties("micronaut.python.pool")
public record PythonPoolConfiguration(
    @Bindable(defaultValue = "0") int size,
    @Bindable(defaultValue = "false") boolean syncInit
) {}
