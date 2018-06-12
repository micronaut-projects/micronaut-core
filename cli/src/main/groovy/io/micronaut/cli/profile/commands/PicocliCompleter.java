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
