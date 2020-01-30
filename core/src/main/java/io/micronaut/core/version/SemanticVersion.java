/*
 * Copyright 2017-2019 original authors
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

/**
 * Utility class for comparing semantic versions.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class SemanticVersion implements Comparable<SemanticVersion> {

    private static final int PARTS_MIN = 3;
    /**
     * The major version.
     */
    private final Integer major;
    /**
     * The minor version.
     */
    private final Integer minor;
    /**
     * The patch version.
     */
    private final Integer patch;

    /**
     * The full version.
     */
    private final String version;

    /**
     * Constructor.
     * @param version version
     */
    public SemanticVersion(String version) {
        this.version = version;
        String[] parts = version
                .replace('_', '.')
                .replace('-', '.')
                .split("\\.");
        if (parts.length >= PARTS_MIN) {
            try {
                this.major = Integer.valueOf(parts[0]);
                this.minor = Integer.valueOf(parts[1]);
                this.patch = Integer.valueOf(parts[2]);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Version number is not semantic [" + version + "]! Should be in the format d.d.d. See http://semver.org");
            }
        } else {
            throw new IllegalArgumentException("Version number is not semantic. Should be in the format d.d.d. See http://semver.org");
        }
    }

    /**
     * @return The version string
     */
    public String getVersion() {
        return version;
    }

    @Override
    public int compareTo(SemanticVersion o) {
        int majorCompare = this.major.compareTo(o.major);
        if (majorCompare != 0) {
            return majorCompare;
        }

        int minorCompare = this.minor.compareTo(o.minor);
        if (minorCompare != 0) {
            return minorCompare;
        }

        int patchCompare = this.patch.compareTo(o.patch);
        if (patchCompare != 0) {
            return patchCompare;
        }
        return 0;
    }

    /**
     * Check whether the current version is at least the given major and minor version.
     *
     * @param version      The version to check
     * @param majorVersion The major version
     * @param minorVersion The minor version
     * @return True if it is
     */
    public static boolean isAtLeastMajorMinor(String version, int majorVersion, int minorVersion) {
        SemanticVersion semanticVersion = new SemanticVersion(version);
        return isAtLeastMajorMinorImpl(semanticVersion, majorVersion, minorVersion);
    }

    /**
     * Check whether the version is at least the given version.
     *
     * @param version         The version
     * @param requiredVersion The required version
     * @return True if it is
     */
    public static boolean isAtLeast(String version, String requiredVersion) {
        if (version != null) {
            SemanticVersion thisVersion = new SemanticVersion(version);
            SemanticVersion otherVersion = new SemanticVersion(requiredVersion);
            return thisVersion.compareTo(otherVersion) != -1;
        }
        return false;
    }

    private static boolean isAtLeastMajorMinorImpl(SemanticVersion version, int majorVersion, int minorVersion) {
        return version != null && version.major >= majorVersion && version.minor >= minorVersion;
    }
}
