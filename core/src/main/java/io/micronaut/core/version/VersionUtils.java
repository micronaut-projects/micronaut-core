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
package io.micronaut.core.version;

import java.io.IOException;
import java.net.URL;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

/**
 * Utility methods for versioning.
 *
 * @author graemerocher
 * @since 1.0
 */
public class VersionUtils {

    /**
     * The current version of Micronaut.
     */
    public static final String MICRONAUT_VERSION = computeMicronautVersion();

    /**
     * Return whether the current version of Micronaut is at least the given version using semantic rules.
     *
     * @param requiredVersion The required version
     * @return True if it is
     */
    public static boolean isAtLeastMicronautVersion(String requiredVersion) {
        return MICRONAUT_VERSION == null || SemanticVersion.isAtLeast(MICRONAUT_VERSION, requiredVersion);
    }

    private static String computeMicronautVersion() {
        String micronautVersion = VersionUtils.class.getPackage().getImplementationVersion();
        if (micronautVersion == null) {
            String className = VersionUtils.class.getSimpleName() + ".class";
            final URL res = VersionUtils.class.getResource(className);
            if (res != null) {
                String classPath = res.toString();

                if (classPath.startsWith("jar")) {
                    String manifestPath = classPath.substring(0, classPath.lastIndexOf("!") + 1) +
                            "/META-INF/MANIFEST.MF";
                    Manifest manifest;
                    try {
                        manifest = new Manifest(new URL(manifestPath).openStream());
                        Attributes attr = manifest.getMainAttributes();
                        micronautVersion = attr.getValue("Implementation-Version");
                    } catch (IOException e) {
                        // ignore
                    }
                }
            }
        }
        return micronautVersion;
    }
}
