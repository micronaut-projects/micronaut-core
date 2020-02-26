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
 * Mixin that adds help and version options to a command. Example usage:
 * <pre>
 * &#064;Command(name = 'command')
 * class App {
 *     &#064;Mixin
 *     HelpOptionsMixin helpOptions // adds help and version options to the command
 *
 *     // ...
 * }
 * </pre>
 *
 * @author Remko Popma
 * @version 1.0
 */
@CompileStatic
@Command(versionProvider = MicronautCliVersionProvider) // individual commands can set a different versionProvider
class HelpOptionsMixin {

    @Option(names = ['-h', '--help'], usageHelp = true, description = ['Show this help message and exit.'])
    boolean helpRequested

    @Option(names = ['-V', '--version'], versionHelp = true, description = ['Print version information and exit.'])
    boolean versionRequested
}
