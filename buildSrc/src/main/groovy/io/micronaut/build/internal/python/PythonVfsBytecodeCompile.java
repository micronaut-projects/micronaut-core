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
package io.micronaut.build.internal.python;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileSystemOperations;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.gradle.process.ExecOperations;

import javax.inject.Inject;

/**
 * Copies a GraalPy VFS resource tree and adds checked-hash Python bytecode caches to its file list.
 */
public abstract class PythonVfsBytecodeCompile extends DefaultTask {
    private static final String PYTHON_BYTECODE_COMPILER_MAIN_CLASS =
        "io.micronaut.python.compiler.PythonBytecodeCompiler";

    @InputDirectory
    public abstract DirectoryProperty getSourceDirectory();

    @OutputDirectory
    public abstract DirectoryProperty getDestinationDirectory();

    @Input
    public abstract Property<String> getFilesListPath();

    @Classpath
    public abstract ConfigurableFileCollection getCompilerClasspath();

    @Inject
    protected abstract ExecOperations getExecOperations();

    @Inject
    protected abstract FileSystemOperations getFileSystemOperations();

    @TaskAction
    void compile() {
        var sourceDirectory = getSourceDirectory().getAsFile().get();
        var destinationDirectory = getDestinationDirectory().getAsFile().get();
        getFileSystemOperations().sync(spec -> {
            spec.from(sourceDirectory);
            spec.into(destinationDirectory);
        });
        getExecOperations().javaexec(spec -> {
            spec.classpath(getCompilerClasspath());
            spec.getMainClass().set(PYTHON_BYTECODE_COMPILER_MAIN_CLASS);
            spec.args(destinationDirectory, getFilesListPath().get());
        }).rethrowFailure();
    }
}
