/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.core.cli;

import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Represents the parsed command line options.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface CommandLine {

    /**
     * @return The remaining arguments after the command name
     */
    List<String> getRemainingArgs();

    /**
     * @return The system properties specified
     */
    Properties getSystemProperties();

    /**
     * @return The declared option values
     */
    Map<Option, Object> getOptions();

    /**
     * @param name The name of the option
     * @return Whether the given option is specified
     */
    boolean hasOption(String name);

    /**
     * The value of an option.
     *
     * @param name The option
     * @return The value
     */
    Object optionValue(String name);

    /**
     * @return The last specified option
     */
    Map.Entry<String, Object> lastOption();

    /**
     * @return The remaining args as one big string
     */
    String getRemainingArgsString();

    /**
     * @return The remaining args as one big string without undeclared options
     */
    String getRemainingArgsWithOptionsString();

    /**
     * @return Any undeclared options
     */
    Map<String, Object> getUndeclaredOptions();

    /**
     * Parses a new {@link CommandLine} instance that combines this instance with the given arguments.
     *
     * @param args The arguments
     * @return A new {@link CommandLine} instance
     */
    CommandLine parseNew(String[] args);

    /**
     * @return The raw unparsed arguments
     */
    String[] getRawArguments();

    /**
     * Build and parse a new command line.
     *
     * @return The builder
     */
    static Builder build() {
        return new CommandLineParser();
    }

    /**
     * Parse a new command line with the default options.
     *
     * @param args The arguments
     * @return The command line
     */
    static CommandLine parse(String... args) {
        if (args == null || args.length == 0) {
            return new DefaultCommandLine();
        }
        return new CommandLineParser().parse(args);
    }

    /**
     * A build for constructing a command line parser.
     *
     * @param <T> The concrete type of the builder
     */
    interface Builder<T extends Builder> {

        /**
         * Add an option.
         *
         * @param name        The name
         * @param description The description
         * @return This builder
         */
        T addOption(String name, String description);

        /**
         * Parses a string of all the command line options converting them into an array of arguments to pass to #parse(String..args).
         *
         * @param string The string
         * @return The command line
         */
        CommandLine parseString(String string);

        /**
         * Parses the given list of command line arguments. Arguments starting with -D become system properties,
         * arguments starting with -- or - become either declared or undeclared options. All other arguments are
         * put into a list of remaining arguments
         *
         * @param args The arguments
         * @return The command line state
         */
        CommandLine parse(String... args);
    }

}
