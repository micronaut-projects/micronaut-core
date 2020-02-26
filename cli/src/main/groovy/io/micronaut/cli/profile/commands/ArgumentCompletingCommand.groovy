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
import io.micronaut.cli.profile.Command
import io.micronaut.cli.profile.ResetableCommand
import jline.console.completer.ArgumentCompleter
import jline.console.completer.Completer
import picocli.AutoComplete
import picocli.CommandLine
import picocli.CommandLine.Spec
import picocli.CommandLine.Model.CommandSpec

/**
 * Base class for commands that generates JLine completion candidates based on the
 * {@link picocli.CommandLine.Model.ArgSpec#completionCandidates completionCandidates} defined
 * on the options and positional parameters (or the enum values of the type if the ArgSpec has an enum type).
 *
 * @author Remko Popma
 * @since 1.0
 */
@CompileStatic
abstract class ArgumentCompletingCommand implements Command, Completer {

    CommandSpec commandSpec

    @Spec
    void setCommandSpec(CommandSpec commandSpec) {
        this.commandSpec = commandSpec
    }

    @Override
    CommandSpec getCommandSpec() {
        commandSpec
    }

    @Override
    int complete(String buffer, int cursor, List<CharSequence> candidates) {
        return new PicocliCompleter(commandSpec).complete(buffer, cursor, candidates)
    }

}
