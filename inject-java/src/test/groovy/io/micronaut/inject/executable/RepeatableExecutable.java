package io.micronaut.inject.executable;

import io.micronaut.context.annotation.Executable;

import java.lang.annotation.Repeatable;

@Repeatable(RepeatableExecutables.class)
@Executable(processOnStartup = true)
public @interface RepeatableExecutable {

    String value();
}
