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

import groovy.transform.CompileStatic
import picocli.CommandLine.Command
import picocli.CommandLine.Option

/**
 * Mixin that adds help, version and other common options to a command. Example usage:
 * <pre>
 * &#064;Command(name = 'command')
 * class App {
 *     &#064;Mixin
 *     CommonOptionsMixin commonOptions // adds common options to the command
 *
 *     // ...
 * }
 * </pre>
 *
 * @author Remko Popma
 * @version 1.0
 */
@CompileStatic
class CommonOptionsMixin extends HelpOptionsMixin {
    String DEBUG_FORK = "debug-fork";
    String OFFLINE_ARGUMENT = "offline";
    String AGENT_ARGUMENT = "reloading";

    String REFRESH_DEPENDENCIES_ARGUMENT = "refresh-dependencies";
    String NON_INTERACTIVE_ARGUMENT = "non-interactive";


    @Option(names = ['-n', '--plain-output'], description = ['Use plain text instead of ANSI colors and styles.'])
    boolean ansiEnabled = true // toggled to false if option is specified

    @Option(names = ['-x', '--stacktrace'], defaultValue = "false", description = ['Show full stack trace when exceptions occur.'])
    boolean showStacktrace

    @Option(names = ['-v', '--verbose'], defaultValue = "false", description = ['Create verbose output.'])
    boolean verbose

//    @Option(names = ['-debug-fork'], hidden = true)
//    boolean debugFork
//
//    @Option(names = ['-offline'], hidden = true)
//    boolean offline
//
//    @Option(names = ['-reloading'], hidden = true)
//    boolean reloading
//
//    @Option(names = ['-n', '-non-interactive'], description = ['Do not start interactive mode console.'])
//    boolean interactive = true // toggled to false if option is specified
//
//    @Option(names = ['-f', '-refresh-dependencies'], description = ['Refresh dependencies.'])
//    boolean refreshDependencies
}
