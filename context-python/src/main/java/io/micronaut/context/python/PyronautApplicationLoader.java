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
package io.micronaut.context.python;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;

/**
 * Utility class for loading the generated Pyronaut application script.
 * Handles loading and executing the Python code generated during annotation processing.
 *
 * @author Micronaut Team
 * @since 5.0.0
 */
public final class PyronautApplicationLoader {

    private PyronautApplicationLoader() {
        // Utility class
    }

    /**
     * Load and execute the generated Pyronaut application script in the given context.
     *
     * @param context The GraalPy context to load the script into
     * @throws IOException if the script cannot be loaded
     */
    public static void loadApplicationScript(Context context) throws IOException {
        try (InputStream inputStream = PyronautApplicationLoader.class.getClassLoader()
                .getResourceAsStream("META-INF/pyronaut_application.py")) {

            if (inputStream != null) {
                String scriptContent = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
                Source source = Source.newBuilder("python", scriptContent, "pyronaut_application.py")
                    .build();
                context.eval(source);
            }
            // If no script is found, continue without loading (graceful degradation)
        }
    }

    /**
     * Check if the Pyronaut application script is available.
     *
     * @return true if the script exists, false otherwise
     */
    public static boolean isApplicationScriptAvailable() {
        try (InputStream inputStream = PyronautApplicationLoader.class.getClassLoader()
                .getResourceAsStream("META-INF/pyronaut_application.py")) {
            return inputStream != null;
        } catch (IOException e) {
            return false;
        }
    }
}
