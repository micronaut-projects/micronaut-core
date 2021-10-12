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

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.cli.exceptions.ParseException;
import io.micronaut.core.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;

/**
 * Command line parser that parses arguments to the command line. Written as a
 * replacement for Commons CLI because it doesn't support unknown arguments and
 * requires all arguments to be declared up front.
 * <p>
 * It also doesn't support command options with hyphens. This class gets around those problems.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
class CommandLineParser implements CommandLine.Builder<CommandLineParser> {

    private static final String DEFAULT_PADDING = "        ";

    private Map<String, Option> declaredOptions = new HashMap<>();
    private int longestOptionNameLength = 0;
    private String usageMessage;

    /**
     * Adds a declared option.
     *
     * @param name        The name of the option
     * @param description The description
     */
    @Override
    public CommandLineParser addOption(String name, String description) {
        int length = name.length();
        if (length > longestOptionNameLength) {
            longestOptionNameLength = length;
        }
        declaredOptions.put(name, new Option(name, description));
        return this;
    }

    @Override
    public CommandLine parseString(String string) {
        // Steal ants implementation for argument splitting. Handles quoted arguments with " or '.
        // Doesn't handle escape sequences with \
        return parse(translateCommandline(string));
    }

    @Override
    public CommandLine parse(String... args) {
        DefaultCommandLine cl = createCommandLine();
        return parse(cl, args);
    }

    /**
     * Parse the command line entry.
     * @param cl commandLine
     * @param args args passed in
     * @return commandLine
     */
    CommandLine parse(DefaultCommandLine cl, String[] args) {
        parseInternal(cl, args);
        return cl;
    }

    /**
     * Build the options message.
     * @return message
     */
    public String getOptionsHelpMessage() {
        String ls = System.getProperty("line.separator");
        usageMessage = "Available options:";
        StringBuilder sb = new StringBuilder(usageMessage);
        sb.append(ls);
        for (Option option : declaredOptions.values()) {
            String name = option.getName();
            int extraPadding = longestOptionNameLength - name.length();
            sb.append(" -").append(name);
            for (int i = 0; i < extraPadding; i++) {
                sb.append(' ');
            }
            sb.append(DEFAULT_PADDING).append(option.getDescription()).append(ls);
        }

        return sb.toString();
    }

    private void parseInternal(DefaultCommandLine cl, String[] args) {
        cl.setRawArguments(args);
        String lastOptionName = null;
        for (String arg : args) {
            if (arg == null) {
                continue;
            }
            String trimmed = arg.trim();
            if (StringUtils.isNotEmpty(trimmed)) {
                if (trimmed.charAt(0) == '"' && trimmed.charAt(trimmed.length() - 1) == '"') {
                    trimmed = trimmed.substring(1, trimmed.length() - 1);
                }
                if (trimmed.charAt(0) == '-') {
                    lastOptionName = processOption(cl, trimmed);
                } else {
                    if (lastOptionName != null) {
                        Option opt = declaredOptions.get(lastOptionName);
                        if (opt != null) {
                            cl.addDeclaredOption(opt, trimmed);
                        } else {
                            cl.addUndeclaredOption(lastOptionName, trimmed);
                        }
                        lastOptionName = null;
                    } else {
                        cl.addRemainingArg(trimmed);
                    }
                }
            }
        }
    }

    /**
     * Create a default command line.
     * @return commandLine
     */
    protected DefaultCommandLine createCommandLine() {
        return new DefaultCommandLine();
    }

    /**
     * Process the passed in options.
     * @param cl cl
     * @param arg arg
     * @return argument processed
     */
    protected String processOption(DefaultCommandLine cl, String arg) {
        if (arg.length() < 2) {
            return null;
        }

        if (arg.charAt(1) == 'D' && arg.contains("=")) {
            processSystemArg(cl, arg);
            return null;
        }

        arg = (arg.charAt(1) == '-' ? arg.substring(2, arg.length()) : arg.substring(1, arg.length())).trim();

        if (arg.contains("=")) {
            String[] split = arg.split("=");
            String name = split[0].trim();
            validateOptionName(name);
            String value = split.length > 1 ? split[1].trim() : "";
            if (declaredOptions.containsKey(name)) {
                cl.addDeclaredOption(declaredOptions.get(name), value);
            } else {
                cl.addUndeclaredOption(name, value);
            }
            return null;
        }

        validateOptionName(arg);
        if (declaredOptions.containsKey(arg)) {
            cl.addDeclaredOption(declaredOptions.get(arg));
        } else {
            cl.addUndeclaredOption(arg);
        }
        return arg;
    }

    /**
     * Process System property arg.
     * @param cl cl
     * @param arg system arg
     */
    protected void processSystemArg(DefaultCommandLine cl, String arg) {
        int i = arg.indexOf('=');
        String name = arg.substring(2, i);
        String value = arg.substring(i + 1, arg.length());
        cl.addSystemProperty(name, value);
    }

    private void validateOptionName(String name) {
        if (name.contains(" ")) {
            throw new ParseException("Invalid argument: " + name);
        }
    }

    /**
     * Crack a command line.
     *
     * @param toProcess the command line to process.
     * @return the command line broken into strings.
     * An empty or null toProcess parameter results in a zero sized array.
     */
    static String[] translateCommandline(String toProcess) {
        if (toProcess == null || toProcess.length() == 0) {
            //no command? no string
            return new String[0];
        }
        // parse with a simple finite state machine

        final int normal = 0;
        final int inQuote = 1;
        final int inDoubleQuote = 2;
        int state = normal;
        final StringTokenizer tok = new StringTokenizer(toProcess, "\"\' ", true);
        final ArrayList<String> result = new ArrayList<>();
        final StringBuilder current = new StringBuilder();
        boolean lastTokenHasBeenQuoted = false;

        while (tok.hasMoreTokens()) {
            String nextTok = tok.nextToken();
            switch (state) {
                case inQuote:
                    if ("\'".equals(nextTok)) {
                        lastTokenHasBeenQuoted = true;
                        state = normal;
                    } else {
                        current.append(nextTok);
                    }
                    break;
                case inDoubleQuote:
                    if ("\"".equals(nextTok)) {
                        lastTokenHasBeenQuoted = true;
                        state = normal;
                    } else {
                        current.append(nextTok);
                    }
                    break;
                default:
                    if ("\'".equals(nextTok)) {
                        state = inQuote;
                    } else if ("\"".equals(nextTok)) {
                        state = inDoubleQuote;
                    } else if (" ".equals(nextTok)) {
                        if (lastTokenHasBeenQuoted || current.length() != 0) {
                            result.add(current.toString());
                            current.setLength(0);
                        }
                    } else {
                        current.append(nextTok);
                    }
                    lastTokenHasBeenQuoted = false;
                    break;
            }
        }
        if (lastTokenHasBeenQuoted || current.length() != 0) {
            result.add(current.toString());
        }
        if (state == inQuote || state == inDoubleQuote) {
            throw new ParseException("unbalanced quotes in " + toProcess);
        }
        return result.toArray(new String[0]);
    }
}
