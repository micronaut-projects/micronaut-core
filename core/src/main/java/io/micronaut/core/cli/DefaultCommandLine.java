/*
 * Copyright 2017 original authors
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
package io.micronaut.core.cli;


import java.util.*;

/**
 * Implementation of the {@link CommandLine} interface.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
class DefaultCommandLine implements CommandLine {

    private Properties systemProperties = new Properties();
    private LinkedHashMap<String, Object> undeclaredOptions = new LinkedHashMap<>();
    private LinkedHashMap<Option, Object> declaredOptions = new LinkedHashMap<>();
    private List<String> remainingArgs = new ArrayList<String>();
    private String[] rawArguments = new String[0];

    @Override
    public CommandLine parseNew(String[] args) {
        DefaultCommandLine defaultCommandLine = new DefaultCommandLine();
        defaultCommandLine.systemProperties.putAll(systemProperties);
        defaultCommandLine.undeclaredOptions.putAll(undeclaredOptions);
        defaultCommandLine.declaredOptions.putAll(declaredOptions);
        CommandLineParser parser = new CommandLineParser();
        return parser.parse(defaultCommandLine, args);
    }

    @Override
    public Map<Option, Object> getOptions() {
        return declaredOptions;
    }

    @Override
    public List<String> getRemainingArgs() {
        return remainingArgs;
    }

    @Override
    public Properties getSystemProperties() {
        return systemProperties;
    }

    @Override
    public boolean hasOption(String name) {
        return declaredOptions.containsKey(new Option(name, null)) || undeclaredOptions.containsKey(name);
    }

    @Override
    public Object optionValue(String name) {
        Option opt = new Option(name, null);
        if (declaredOptions.containsKey(opt)) {
            return declaredOptions.get(opt);
        }
        if (undeclaredOptions.containsKey(name)) {
            return undeclaredOptions.get(name);
        }
        return null;
    }


    @Override
    public String getRemainingArgsString() {
        return remainingArgsToString(" ", false);
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

    @Override
    public String getRemainingArgsWithOptionsString() {
        return remainingArgsToString(" ", true);
    }

    @Override
    public Map<String, Object> getUndeclaredOptions() {
        return Collections.unmodifiableMap(undeclaredOptions);
    }

    @Override
    public String[] getRawArguments() {
        return rawArguments;
    }

    void addDeclaredOption(Option option) {
        addDeclaredOption(option, Boolean.TRUE);
    }

    void addUndeclaredOption(String option) {
        undeclaredOptions.put(option, Boolean.TRUE);
    }

    void addUndeclaredOption(String option, Object value) {
        undeclaredOptions.put(option, value);
    }

    void addDeclaredOption(Option option, Object value) {
        declaredOptions.put(option, value);
    }

    void addRemainingArg(String arg) {
        remainingArgs.add(arg);
    }

    void addSystemProperty(String name, String value) {
        systemProperties.put(name, value);
    }

    void setRawArguments(String[] args) {
        this.rawArguments = args;
    }

    private String remainingArgsToString(String separator, boolean includeOptions) {
        StringBuilder sb = new StringBuilder();
        String sep = "";
        List<String> args = new ArrayList<>(remainingArgs);
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

}
