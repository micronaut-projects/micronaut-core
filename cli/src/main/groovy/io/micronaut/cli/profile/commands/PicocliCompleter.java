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
package io.micronaut.cli.profile.commands;

import jline.console.completer.ArgumentCompleter;
import jline.console.completer.Completer;
import picocli.AutoComplete;
import picocli.CommandLine.Model.CommandSpec;

import java.util.List;

/**
 * Generates JLine completion candidates based on the
 * {@link picocli.CommandLine.Model.ArgSpec#completionCandidates completionCandidates} defined
 * on the options and positional parameters of the command (or the enum values of the type if the ArgSpec has an enum type).
 *
 * @author Remko Popma
 * @since 1.0
 */
public class PicocliCompleter implements Completer {

    CommandSpec commandSpec;

    /**
     * Default constructor.
     *
     * @param spec The command spec
     */
    public PicocliCompleter(CommandSpec spec) {
        this.commandSpec = spec;
    }

    @Override
    public int complete(String buffer, int cursor, List<CharSequence> candidates) {
        // use the jline internal parser to split the line into tokens
        ArgumentCompleter.ArgumentList list =
                new ArgumentCompleter.WhitespaceArgumentDelimiter().delimit(buffer, cursor);

        // let picocli generate completion candidates for the token where the cursor is at
        return AutoComplete.complete(commandSpec,
                list.getArguments(),
                list.getCursorArgumentIndex(),
                list.getArgumentPosition(),
                cursor,
                candidates);
    }
}
