package io.micronaut.kotlin.processing.beans.executable

import io.micronaut.context.annotation.Executable

@Repeatable
@Executable(processOnStartup = true)
annotation class RepeatableExecutable(val value: String)
