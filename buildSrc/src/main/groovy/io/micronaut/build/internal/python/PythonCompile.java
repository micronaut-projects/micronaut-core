/*
 * Copyright 2003-2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
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
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.options.Option;
import org.gradle.workers.WorkerExecutor;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Compiles Python sources with the Pyronaut compiler.
 * <p>
 * The compiler is given each source directory relative to the project directory, so that the
 * {@code @PythonApplication(src = ...)} value it compiles into the output carries no absolute path.
 * The project directory itself reaches the annotation processor as a compiler option, because a
 * worker daemon does not run in the project directory. That keeps the outputs relocatable and
 * therefore cacheable.
 */
@CacheableTask
public abstract class PythonCompile extends DefaultTask {

    private static final String PYRONAUT_COMPILER_MAIN_CLASS =
        "io.micronaut.python.compiler.PyronautCompiler";

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getSource();

    @Input
    @Optional
    public abstract ListProperty<String> getJvmArgs();

    /**
     * The worker heap; Gradle's worker default of 512m is too small for the GraalPy processor.
     */
    @Input
    @Optional
    public abstract Property<String> getMaxHeapSize();

    @Input
    @Optional
    public abstract MapProperty<String, String> getSystemProperties();

    @Input
    @Optional
    public abstract MapProperty<String, String> getEnvironmentVariables();

    @Internal
    @Option(option = "debug-python-compiler", description = "Debug the Pyronaut compiler")
    public abstract Property<Boolean> getDebugCompiler();

    @Classpath
    public abstract ConfigurableFileCollection getCompilerClasspath();

    @Classpath
    public abstract ConfigurableFileCollection getClasspath();

    @OutputDirectory
    public abstract DirectoryProperty getDestinationDir();

    @Inject
    protected abstract WorkerExecutor getWorkerExecutor();

    @Inject
    protected abstract FileSystemOperations getFileSystemOperations();

    @Inject
    protected abstract ProjectLayout getLayout();

    private Map<String, String> getMergedSystemProperties() {
        var systemProperties = new LinkedHashMap<>(getSystemProperties().getOrElse(Map.of()));
        systemProperties.putAll(Map.of(
            "org.graalvm.python.vfs.allow_multiple", "true",
            "org.graalvm.python.vfs.multiple_vfs_checks_as_warning", "true"
        ));
        return Collections.unmodifiableMap(systemProperties);
    }

    private List<String> getMergedJvmArgs() {
        var jvmArgs = new ArrayList<>(getJvmArgs().getOrElse(List.of()));
        if (getDebugCompiler().getOrElse(false)) {
            jvmArgs.add("-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=*:5005");
        }
        return jvmArgs;
    }

    @TaskAction
    void compile() throws IOException {
        var outputDir = getDestinationDir().getAsFile().get().toPath();
        var projectDir = getLayout().getProjectDirectory().getAsFile().toPath().toAbsolutePath().normalize();
        getFileSystemOperations().delete(spec -> spec.delete(outputDir));
        Files.createDirectories(outputDir);
        // A process-isolated worker is reused for matching fork options within one build, so several
        // Python compile tasks share one JVM start; the compiler itself is rebuilt per submission.
        var queue = getWorkerExecutor().processIsolation(spec -> {
            spec.getClasspath().from(getCompilerClasspath(), getClasspath());
            spec.forkOptions(fork -> {
                fork.setMaxHeapSize(getMaxHeapSize().getOrElse("2g"));
                fork.systemProperties(getMergedSystemProperties());
                fork.environment(getEnvironmentVariables().getOrElse(Map.of()));
                fork.jvmArgs(getMergedJvmArgs());
            });
        });
        var destDir = getDestinationDir().getAsFile().get().getAbsolutePath();
        var sourceDirs = new ArrayList<String>();
        for (var location : getSource().getElements().get()) {
            // Compiler currently accepts a single directory, but maybe it should
            // accept a list of .py files instead
            if (location.getAsFile().isDirectory()) {
                sourceDirs.add(relocatableSourcePath(projectDir, location.getAsFile().toPath()));
            }
        }
        if (sourceDirs.isEmpty()) {
            return;
        }
        // one work item: the roots share the destination, so they must not compile concurrently
        queue.submit(PythonCompileWorkAction.class, parameters -> {
            parameters.getSourceDirs().set(sourceDirs);
            parameters.getSourceRoot().set(projectDir.toString());
            parameters.getDestinationDir().set(destDir);
            parameters.getClasspath().from(getCompilerClasspath(), getClasspath());
        });
        queue.await();
    }

    /**
     * Returns the source directory relative to the project directory when it is located inside it,
     * otherwise its absolute path. The annotation processor resolves relative paths against the project directory.
     */
    private static String relocatableSourcePath(Path projectDir, Path sourceDir) {
        var absoluteSource = sourceDir.toAbsolutePath().normalize();
        if (absoluteSource.startsWith(projectDir)) {
            return projectDir.relativize(absoluteSource).toString();
        }
        return absoluteSource.toString();
    }
}
