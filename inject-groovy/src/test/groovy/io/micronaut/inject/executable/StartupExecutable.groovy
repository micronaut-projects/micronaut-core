package io.micronaut.inject.executable

import io.micronaut.context.annotation.Executable

@Executable(processOnStartup = true)
@interface StartupExecutable {

}
