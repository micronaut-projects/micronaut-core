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

package io.micronaut.cli.console.parsing;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Implementation of the {@link CommandLine} interface.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class DefaultCommandLine implements CommandLine {

    Properties systemProperties = new Properties();
    LinkedHashMap<String, Object> undeclaredOptions = new LinkedHashMap<>();
    LinkedHashMap<String, SpecifiedOption> declaredOptions = new LinkedHashMap<String, SpecifiedOption>();
    List<String> remainingArgs = new ArrayList<String>();
    private String commandName;
    private String[] rawArguments;

    /**
     * Add a new declared option.
     *
     * @param name   The name
     * @param option The option
     */
    public void addDeclaredOption(String name, Option option) {
        addDeclaredOption(name, option, Boolean.TRUE);
    }

    /**
     * Add a new undeclared option.
     *
     * @param option The option
     */
    public void addUndeclaredOption(String option) {
        undeclaredOptions.put(option, Boolean.TRUE);
    }

    /**
     * Add a new undeclared option.
     *
     * @param option The option
     * @param value  The value
     */
    public void addUndeclaredOption(String option, Object value) {
        undeclaredOptions.put(option, value);
    }

    /**
     * Add a declared option.
     *
     * @param name   The name
     * @param option The option
     * @param value  The value
     */
    public void addDeclaredOption(String name, Option option, Object value) {
        SpecifiedOption so = new SpecifiedOption();
        so.option = option;
        so.value = value;

        declaredOptions.put(name, so);
    }

    @Override
    public CommandLine parseNew(String[] args) {
        DefaultCommandLine defaultCommandLine = new DefaultCommandLine();
        defaultCommandLine.systemProperties.putAll(systemProperties);
        defaultCommandLine.undeclaredOptions.putAll(undeclaredOptions);
        defaultCommandLine.declaredOptions.putAll(declaredOptions);
        CommandLineParser parser = new CommandLineParser();
        return parser.parse(defaultCommandLine, args);
    }

    /**
     * Set the command name.
     *
     * @param name The name
     */
    public void setCommand(String name) {
        commandName = name;
    }

    /**
     * Set the command.
     *
     * @param cmd The command
     */
    public void setCommandName(String cmd) {
        if (REFRESH_DEPENDENCIES_ARGUMENT.equals(cmd)) {
            addUndeclaredOption(REFRESH_DEPENDENCIES_ARGUMENT);
        }
        commandName = cmd;
    }

    /**
     * @return The command name
     */
    public String getCommandName() {
        return commandName;
    }

    /**
     * Add remaining argument.
     *
     * @param arg The argument
     */
    public void addRemainingArg(String arg) {
        remainingArgs.add(arg);
    }

    /**
     * @return The remaining arguments
     */
    public List<String> getRemainingArgs() {
        return remainingArgs;
    }

    /**
     * @return The remaining arguments
     */
    public String[] getRemainingArgsArray() {
        return remainingArgs.toArray(new String[remainingArgs.size()]);
    }

    /**
     * @return The system properties
     */
    public Properties getSystemProperties() {
        return systemProperties;
    }

    /**
     * @param name The name of the option
     * @return Whether the option is defined
     */
    public boolean hasOption(String name) {
        return declaredOptions.containsKey(name) || undeclaredOptions.containsKey(name);
    }

    /**
     * @param name The option
     * @return The option value
     */
    public Object optionValue(String name) {
        if (declaredOptions.containsKey(name)) {
            SpecifiedOption specifiedOption = declaredOptions.get(name);
            return specifiedOption.value;
        }
        if (undeclaredOptions.containsKey(name)) {
            return undeclaredOptions.get(name);
        }
        return null;
    }

    @Override
    public Map.Entry<String, Object> lastOption() {
        final Iterator<Map.Entry<String, Object>> i = undeclaredOptions.entrySet().iterator();
        while (i.hasNext()) {
            Map.Entry<String, Object> next = i.next();
            if (!i.hasNext()) {
                return next;
            }
        }
        return null;
    }

    /**
     * @return The remaining arguments
     */
    public String getRemainingArgsString() {
        return remainingArgsToString(" ", false);
    }

    @Override
    public String getRemainingArgsWithOptionsString() {
        return remainingArgsToString(" ", true);
    }

    /**
     * @return The remaining arguments separated by new lines
     */
    public String getRemainingArgsLineSeparated() {
        return remainingArgsToString("\n", false);
    }

    private String remainingArgsToString(String separator, boolean includeOptions) {
        StringBuilder sb = new StringBuilder();
        String sep = "";
        List<String> args = new ArrayList<String>(remainingArgs);
        if (includeOptions) {
            for (Map.Entry<String, Object> entry : undeclaredOptions.entrySet()) {
                if (entry.getValue() instanceof Boolean && ((Boolean) entry.getValue())) {
                    args.add('-' + entry.getKey());
                } else {
                    args.add('-' + entry.getKey() + '=' + entry.getValue());
                }
            }
        }
        for (String arg : args) {
            sb.append(sep).append(arg);
            sep = separator;
        }
        return sb.toString();
    }

    /**
     * @return The undeclared options
     */
    public Map<String, Object> getUndeclaredOptions() {
        return undeclaredOptions;
    }

    /**
     * Add a new system property.
     *
     * @param name  The name
     * @param value The value
     */
    public void addSystemProperty(String name, String value) {
        systemProperties.put(name, value);
    }

    /**
     * Set the raw arguments.
     *
     * @param args The arguments
     */
    public void setRawArguments(String[] args) {
        this.rawArguments = args;
    }

    @Override
    public String[] getRawArguments() {
        return rawArguments;
    }

    /**
     * A specified option.
     */
    public static class SpecifiedOption {
        private Option option;
        private Object value;

        /**
         * @return The option
         */
        public Option getOption() {
            return option;
        }

        /**
         * @return The value
         */
        public Object getValue() {
            return value;
        }
    }
}
