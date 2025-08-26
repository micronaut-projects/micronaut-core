/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.context.env;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

public class DotEnvPropertySourceLoader extends AbstractPropertySourceLoader {

    /**
     * File extension for property source loader.
     */
    public static final String FILE_EXTENSION = "env";

    public DotEnvPropertySourceLoader() {
    }

    public DotEnvPropertySourceLoader(boolean logEnabled) { super(logEnabled); }

    @Override
    public Set<String> getExtensions() { return Collections.singleton(FILE_EXTENSION); }

    @Override
    protected void processInput(String name, InputStream input, Map<String, Object> finalMap) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input))) {
            String line;
            while ((line = reader.readLine()) != null) {
                parseLine(line, finalMap);
            }
        }
    }

    private void parseLine(String line, Map<String, Object> finalMap) {
        line = line.trim();

        if (line.isEmpty() || line.startsWith("#")) { return; }

        int delimIndex = line.indexOf('=');
        if (delimIndex <= 0) { return; }

        String key = line.substring(0, delimIndex).trim();
        String val = line.substring(delimIndex+1).trim();

        if (key.isEmpty() || val.isEmpty()) { return; }

        key = processKey(key);
        val = processVal(val);

        finalMap.put(key, val);
    }

    private String processKey(String key) {
        return key.toLowerCase().replace('_', '.');
    }

    private String processVal(String val) {
        if (val.length() >= 2) {
            if ((val.startsWith("'") && val.endsWith("'")) || (val.startsWith("\"") && val.endsWith("\""))) {
                return val.substring(1, val.length()-1);
            }
        }
        return val;
    }
}
