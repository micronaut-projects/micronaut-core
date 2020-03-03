/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.cli.profile.commands

import io.micronaut.cli.console.logging.MicronautConsole
import io.micronaut.cli.profile.ExecutionContext
import io.micronaut.cli.profile.Profile
import io.micronaut.cli.profile.ProfileCommand
import io.micronaut.cli.profile.ProfileRepository
import io.micronaut.cli.profile.ProfileRepositoryAware
import io.micronaut.cli.profile.ProjectContext
import io.micronaut.cli.profile.ProjectContextAware
import io.micronaut.cli.profile.ResetableCommand
import picocli.CommandLine
import picocli.CommandLine.Model.CommandSpec

/**
 * @author Graeme Rocher
 */
@CommandLine.Command(name = 'help', description = 'Prints help information for a specific command',
        helpCommand = true,
        parameterListHeading = 'Arguments:%n',
        optionListHeading = 'Flags:%n',
        footer = ['Examples:',
        '$ mn create-app my-app',
        '',
        'Language support for Groovy and/or Kotlin can be enabled with the corresponding feature::',
        '$ mn create-app my-groovy-app -features=groovy',
        '$ mn create-app my-kotlin-app -features=kotlin',
        '',
        '(type mn help \'command-name\' for more info)'])
class HelpCommand implements ProfileCommand, ProjectContextAware, ProfileRepositoryAware, CommandLine.IHelpCommandInitializable, Runnable {

    public static final String NAME = "help"

    @CommandLine.Option(names = ["-h", "--help"], usageHelp = true,
            description = "Show usage help for the help command and exit.")
    private boolean helpRequested

    @CommandLine.Parameters(paramLabel = "COMMAND",
            description = "The COMMAND to display the usage help message for.")
    private String[] commands = new String[0]

    private CommandLine self
    private PrintStream out
    private PrintStream err
    private CommandLine.Help.Ansi ansi

    Profile profile
    ProfileRepository profileRepository
    ProjectContext projectContext

    @CommandLine.Spec
    CommandSpec commandSpec;

    @Override
    String getName() {
        return NAME
    }

    CommandLine.Help.Ansi getAnsi() {
        if (this.@ansi) {
            this.@ansi
        } else {
            MicronautConsole.instance.ansiEnabled ? CommandLine.Help.Ansi.ON : CommandLine.Help.Ansi.OFF
        }
    }

    PrintStream getOut() {
        this.@out ?: MicronautConsole.instance.out
    }

    // invoked when a command is specified with -h, -help or --help
    void init(CommandLine helpCommandLine, CommandLine.Help.Ansi ansi, PrintStream out, PrintStream err) {
        this.self = helpCommandLine
        this.ansi = ansi
        this.out = out
        this.err = err
    }

    void run() {
        CommandLine me = self ?: (commandSpec ? commandSpec.commandLine() : null)
        CommandLine parent = me == null ? null : me.getParent();
        if (parent != null) {
            if (this.commands.length > 0) {
                CommandLine subcommand = parent.getSubcommands().get(this.commands[0]);
                if (subcommand == null) {
                    throw new CommandLine.ParameterException(parent, "Unknown subcommand '" + this.commands[0] + "'.");
                }
                subcommand.usage(getOut(), getAnsi());
            } else {
                parent.usage(getOut(), getAnsi());
            }
        }
    }

    @Override
    boolean handle(ExecutionContext executionContext) {
        run()
    }

}
