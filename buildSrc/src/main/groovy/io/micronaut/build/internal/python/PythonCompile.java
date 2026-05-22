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
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileSystemOperations;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
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
import org.gradle.process.ExecOperations;

import javax.inject.Inject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

//@CacheableTask
// Currently NOT cacheable because generated Python code
// contains absolute paths
public abstract class PythonCompile extends DefaultTask {

    private static final String PYRONAUT_COMPILER_MAIN_CLASS =
        "io.micronaut.python.compiler.PyronautCompiler";

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getSource();

    @Input
    @Optional
    public abstract ListProperty<String> getJvmArgs();

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
    protected abstract ExecOperations getExecOperations();

    @Inject
    protected abstract FileSystemOperations getFileSystemOperations();

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
        getFileSystemOperations().delete(spec -> spec.delete(outputDir));
        Files.createDirectories(outputDir);
        for (var location : getSource().getElements().get()) {
            // Compiler currently accepts a single directory, but maybe it should
            // accept a list of .py files instead
            if (location.getAsFile().isDirectory()) {
                var compilerOutput = new ByteArrayOutputStream();
                var sourceDir = location.getAsFile().getAbsolutePath();
                var destDir = getDestinationDir().getAsFile().get().getAbsolutePath();
                var result = getExecOperations().javaexec(spec -> {
                    spec.classpath(getCompilerClasspath(), getClasspath());
                    spec.systemProperties(getMergedSystemProperties());
                    spec.environment(getEnvironmentVariables().getOrElse(Map.of()));
                    spec.jvmArgs(getMergedJvmArgs());
                    spec.getMainClass().set(PYRONAUT_COMPILER_MAIN_CLASS);
                    spec.setStandardOutput(compilerOutput);
                    spec.setErrorOutput(compilerOutput);
                    spec.setIgnoreExitValue(true);
                    spec.args(sourceDir, destDir);
                });
                var output = compilerOutput.toString(StandardCharsets.UTF_8);
                if (result.getExitValue() != 0) {
                    throw new GradleException("Python compilation failed for source directory [" +
                        sourceDir + "] with exit code " + result.getExitValue() + "." +
                        formatCompilerOutput(output));
                }
                if (!output.isBlank()) {
                    getLogger().lifecycle(output.stripTrailing());
                }
            }
        }

    }

    private static String formatCompilerOutput(String output) {
        if (output.isBlank()) {
            return "";
        }
        return System.lineSeparator() + "Compiler output:" +
            System.lineSeparator() + output.stripTrailing();
    }
}
