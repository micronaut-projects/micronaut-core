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
package io.micronaut.python.cli;

import io.micronaut.python.compiler.PyronautCompiler;
import io.micronaut.runtime.Micronaut;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

@Command(name = "pyronaut", description = "The Pyronaut CLI", subcommands = {
    PyronautMainCommand.PyronautRunCommand.class})
public class PyronautMainCommand implements Callable<Integer> {

    @Override
    public Integer call() throws Exception {
        return 0;
    }

    @Command(name = "run", description = "Runs a Pyronaut application", mixinStandardHelpOptions = true)
    static class PyronautRunCommand implements Callable<Integer> {
        @Parameters(index = "0", description = "The source directory", defaultValue = ".")
        private File sourceDirectory;

        @Parameters(index = "1..*", description = "Application parameters")
        private String[] parameters;

        @Override
        public Integer call() throws Exception {
            var sourceDirectory = this.sourceDirectory == null ? new File(".") : this.sourceDirectory;
            var tmpDir = Path.of(System.getProperty("java.io.tmpdir")).resolve("pyronaut");
            var outputDirectory = tmpDir.resolve("classes");
            Files.createDirectories(outputDirectory);
            var code = compile(sourceDirectory, outputDirectory);
            if (code != 0) {
                return code;
            }
            return runApp(outputDirectory, sourceDirectory);
        }

        private int runApp(Path outputDirectory, File sourceDirectory)
            throws IOException {
            try (
                var cl = new URLClassLoader(buildUrls(outputDirectory, sourceDirectory.toPath()))) {
                var ctx = Micronaut.build(parameters)
                    .args(parameters)
                    .classLoader(cl)
                    .start();
                // The following line prevents the server from immediately
                // shutting down. Should be redundant but for some reason,
                // start() doesn't block
                Thread.currentThread().join();
            } catch (RuntimeException ex) {
                ex.printStackTrace(System.err);
                return -1;
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            return 0;
        }

        private static int compile(File sourceDirectory, Path outputDirectory) {
            var compiler = PyronautCompiler.builder()
                .pythonSrc(sourceDirectory.getAbsolutePath())
                .targetDir(outputDirectory.toFile())
                .build();
            try {
                compiler.compile();
            } catch (RuntimeException ex) {
                ex.printStackTrace(System.err);
                return -1;
            }
            return 0;
        }

    }

    private static URL[] buildUrls(Path... paths) throws MalformedURLException {
        var result = new URL[paths.length];
        for (int i = 0; i < paths.length; i++) {
            var path = paths[i];
            result[i] = path.toUri().toURL();
        }
        return result;
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new PyronautMainCommand())
            .execute(args);
        System.exit(exitCode);
    }
}
