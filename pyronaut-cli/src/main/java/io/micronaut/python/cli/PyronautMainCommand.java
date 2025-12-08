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

import io.micronaut.python.cli.commands.PyronautCleanCommand;
import io.micronaut.python.cli.commands.PyronautInstallCommand;
import io.micronaut.python.cli.commands.PyronautRunCommand;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

import java.util.concurrent.Callable;

@Command(name = "pyronaut", description = "The Pyronaut CLI", subcommands = {
    PyronautCleanCommand.class,
    PyronautRunCommand.class,
    PyronautInstallCommand.class
}, mixinStandardHelpOptions = true)
public class PyronautMainCommand implements Callable<Void> {
    @Spec
    CommandSpec spec;

    @Override
    public Void call() {
        spec.commandLine().usage(System.out);
        return null;
    }

    public static void main(String[] args) {
        var exitCode = new CommandLine(new PyronautMainCommand())
            .execute(args);
        System.exit(exitCode);
    }
}
