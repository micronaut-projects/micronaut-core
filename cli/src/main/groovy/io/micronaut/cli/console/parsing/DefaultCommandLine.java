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

import java.util.*;

/**
 * Implementation of the {@link CommandLine} interface.
 *
 * @author Graeme Rocher
 * @since 2.0
 */
public class DefaultCommandLine implements CommandLine {

    Properties systemProperties = new Properties();
    LinkedHashMap<String, Object> undeclaredOptions = new LinkedHashMap<>();
    LinkedHashMap<String, SpecifiedOption> declaredOptions = new LinkedHashMap<String, SpecifiedOption>();
    List<String> remainingArgs = new ArrayList<String>();
    private String commandName;
    private String[] rawArguments;

    public void addDeclaredOption(String name, Option option) {
        addDeclaredOption(name, option, Boolean.TRUE);
    }

    public void addUndeclaredOption(String option) {
        undeclaredOptions.put(option, Boolean.TRUE);
    }

    public void addUndeclaredOption(String option, Object value) {
        undeclaredOptions.put(option, value);
    }

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

    public void setCommand(String name) {
        commandName = name;
    }

    public void setCommandName(String cmd) {
        if (REFRESH_DEPENDENCIES_ARGUMENT.equals(cmd)) {
            addUndeclaredOption(REFRESH_DEPENDENCIES_ARGUMENT);
        }
        commandName = cmd;
    }

    public String getCommandName() {
        return commandName;
    }

    public void addRemainingArg(String arg) {
        remainingArgs.add(arg);
    }

    public List<String> getRemainingArgs() {
        return remainingArgs;
    }

    public String[] getRemainingArgsArray() {
        return remainingArgs.toArray(new String[remainingArgs.size()]);
    }

    public Properties getSystemProperties() {
        return systemProperties;
    }

    public boolean hasOption(String name) {
        return declaredOptions.containsKey(name) || undeclaredOptions.containsKey(name);
    }

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
            if(!i.hasNext()) {
                return next;
            }
        }
        return null;
    }

    public String getRemainingArgsString() {
        return remainingArgsToString(" ", false);
    }

    @Override
    public String getRemainingArgsWithOptionsString() {
        return remainingArgsToString(" ", true);
    }

    public String getRemainingArgsLineSeparated() {
        return remainingArgsToString("\n", false);
    }

    private String remainingArgsToString(String separator, boolean includeOptions) {
        StringBuilder sb = new StringBuilder();
        String sep = "";
        List<String> args = new ArrayList<String>(remainingArgs);
        if(includeOptions) {
            for (Map.Entry<String, Object> entry : undeclaredOptions.entrySet()) {
                if (entry.getValue() instanceof Boolean && ((Boolean)entry.getValue())) {
                    args.add('-' + entry.getKey());
                }
                else {
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

    public Map<String, Object> getUndeclaredOptions() {
        return undeclaredOptions;
    }

    public void addSystemProperty(String name, String value) {
        systemProperties.put(name, value);
    }

    public void setRawArguments(String[] args) {
        this.rawArguments = args;
    }

    @Override
    public String[] getRawArguments() {
        return rawArguments;
    }

    public static class SpecifiedOption {
        private Option option;
        private Object value;

        public Option getOption() {
            return option;
        }

        public Object getValue() {
            return value;
        }
    }
}
