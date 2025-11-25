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

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.io.File;
import java.util.concurrent.Callable;

@Command(name = "pyronaut", description = "The Pyronaut CLI", subcommands = {
    PyronautMainCommand.PyronautCleanCommand.class,
    PyronautMainCommand.PyronautRunCommand.class
})
public class PyronautMainCommand implements Callable<Integer> {

    @Override
    public Integer call() throws Exception {
        return 0;
    }

    abstract static class BaseSourceCommand implements Callable<Integer> {
        @Parameters(index = "0", description = "The source directory", defaultValue = ".")
        protected File sourceDirectory;
    }

    @Command(name = "clean", description = "Deletes the temporary files", mixinStandardHelpOptions = true)
    static class PyronautCleanCommand extends BaseSourceCommand {
        @Override
        public Integer call() throws Exception {
            var sourceDirectory = (this.sourceDirectory == null ? new File(".") : this.sourceDirectory).getCanonicalFile().toPath();
            FileUtils.recurseDelete(
                FileUtils.resolveOutputDirectory(sourceDirectory)
            );
            return 0;
        }
    }

    @Command(name = "run", description = "Runs a Pyronaut application", mixinStandardHelpOptions = true)
    static class PyronautRunCommand extends BaseSourceCommand {
        @Parameters(index = "1..*", description = "Application parameters")
        private String[] parameters;

        @Override
        public Integer call() throws Exception {
            var sourceDirectory = (this.sourceDirectory == null ? new File(".") : this.sourceDirectory).getCanonicalFile().toPath();

            var watcher = new PyronautFileWatcher(sourceDirectory, parameters);
            var watcherThread = new Thread(watcher);
            watcherThread.start();

            // Handle shutdown gracefully
            Runtime.getRuntime().addShutdownHook(new Thread(watcher::stop));

            try {
                watcherThread.join();
            } catch (InterruptedException e) {
                watcher.stop();
                Thread.currentThread().interrupt();
            }

            return 0;
        }
    }

    public static void main(String[] args) {
        var exitCode = new CommandLine(new PyronautMainCommand())
            .execute(args);
        System.exit(exitCode);
    }
}
