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
package io.micronaut.core.version;

import edu.umd.cs.findbugs.annotations.Nullable;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * Utility methods for versioning.
 *
 * @author graemerocher
 * @author Iván Lopez
 * @since 1.0
 */
public class VersionUtils {

    private static final Properties VERSIONS = new Properties();

    /**
     * The current version of Micronaut.
     */
    public static final String MICRONAUT_VERSION = getMicronautVersion();

    static {
        URL resource = VersionUtils.class.getResource("/micronaut-version.properties");
        if (resource != null) {
            try (Reader reader = new InputStreamReader(resource.openStream(), StandardCharsets.UTF_8)) {
                VERSIONS.load(reader);
            } catch (IOException e) {
                // ignore
            }
        }
    }

    /**
     * Return whether the current version of Micronaut is at least the given version using semantic rules.
     *
     * @param requiredVersion The required version
     * @return True if it is
     */
    public static boolean isAtLeastMicronautVersion(String requiredVersion) {
        return MICRONAUT_VERSION == null || SemanticVersion.isAtLeast(MICRONAUT_VERSION, requiredVersion);
    }

    @Nullable
    public static String getMicronautVersion() {
        Object micronautVersion = VERSIONS.get("micronaut.version");
        if (micronautVersion != null) {
            return micronautVersion.toString();
        }
        return null;
    }
}
