/*
 * Copyright 2017-2018 original authors
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

import io.micronaut.cli.console.parsing.CommandLine
import io.micronaut.cli.console.parsing.CommandLineParser
import io.micronaut.cli.profile.Command
import io.micronaut.cli.profile.CommandDescription
import io.micronaut.cli.profile.ExecutionContext
import io.micronaut.cli.profile.Profile
import io.micronaut.cli.profile.ProfileCommand
import io.micronaut.cli.profile.ProfileRepository
import io.micronaut.cli.profile.ProfileRepositoryAware
import io.micronaut.cli.profile.ProjectCommand
import io.micronaut.cli.profile.ProjectContext
import io.micronaut.cli.profile.ProjectContextAware
import jline.console.completer.Completer

/**
 * @author Graeme Rocher
 */
class HelpCommand implements ProfileCommand, Completer, ProjectContextAware, ProfileRepositoryAware {

    public static final String NAME = "help"

    final CommandDescription description = new CommandDescription(NAME, "Prints help information for a specific command", "help [COMMAND NAME]")

    Profile profile
    ProfileRepository profileRepository
    ProjectContext projectContext

    CommandLineParser cliParser = new CommandLineParser()

    @Override
    String getName() {
        return NAME
    }


    @Override
    boolean handle(ExecutionContext executionContext) {
        def console = executionContext.console
        def commandLine = executionContext.commandLine
        Collection<CommandDescription> allCommands = findAllCommands()
        String remainingArgs = commandLine.getRemainingArgsString()
        if (remainingArgs?.trim()) {
            CommandLine remainingArgsCommand = cliParser.parseString(remainingArgs)
            String helpCommandName = remainingArgsCommand.getCommandName()
            for (CommandDescription desc : allCommands) {
                if (desc.name == helpCommandName) {
                    console.addStatus("Command: $desc.name")
                    console.addStatus("Description:")
                    console.println "${desc.description ?: ''}"
                    if (desc.usage) {
                        console.println()
                        console.addStatus("Usage:")
                        console.println "${desc.usage}"
                    }
                    if (desc.arguments) {
                        console.println()
                        console.addStatus("Arguments:")
                        for (arg in desc.arguments) {
                            console.println "* ${arg.name} - ${arg.description ?: ''} (${arg.required ? 'REQUIRED' : 'OPTIONAL'})"
                        }
                    }
                    if (desc.flags) {
                        console.println()
                        console.addStatus("Flags:")
                        for (arg in desc.flags) {
                            console.println "* ${arg.name} - ${arg.description ?: ''}"
                        }
                    }
                    return true
                }
            }
            console.error "Help for command $helpCommandName not found"
            return false
        } else {
            console.log '''
Usage (optionals marked with *):'
mn [target] [arguments]*'

'''
            console.addStatus("Examples:")
            console.log('$ mn create-app my-app')
            console.log ''
            console.addStatus("Language support for Groovy and/or Kotlin can be enabled with the corresponding feature::")
            console.log('$ mn create-app my-groovy-app -features=groovy')
            console.log('$ mn create-app my-kotlin-app -features=kotlin')
            console.log ''
            console.addStatus('Available Commands (type mn help \'command-name\' for more info):')
            console.addStatus("${'Command Name'.padRight(37)} Command Description")
            console.println('-' * 100)
            for (CommandDescription desc : allCommands) {
                console.println "${desc.name.padRight(40)}${desc.description}"
            }
            console.println()
            console.addStatus("Detailed usage with help [command]")
            return true
        }

    }

    @Override
    int complete(String buffer, int cursor, List<CharSequence> candidates) {
        def allCommands = findAllCommands().collect() { CommandDescription desc -> desc.name }

        for (cmd in allCommands) {
            if (buffer) {
                if (cmd.startsWith(buffer)) {
                    candidates << cmd.substring(buffer.size())
                }
            } else {
                candidates << cmd
            }
        }
        return cursor
    }


    protected Collection<CommandDescription> findAllCommands() {
        Iterable<Command> commands
        if (profile) {
            commands = profile.getCommands(projectContext)
        } else {
            commands = CommandRegistry.findCommands(profileRepository).findAll() { Command cmd ->
                !(cmd instanceof ProjectCommand)
            }
        }
        return commands
            .collect() { Command cmd -> cmd.description }
            .unique() { CommandDescription cmd -> cmd.name }
            .sort(false) { CommandDescription itDesc -> itDesc.name }
    }


}
