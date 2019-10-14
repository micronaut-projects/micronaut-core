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
package io.micronaut.context.condition;

/**
 * Details of the current operating system.
 */
final class OperatingSystem {
    private final Family family;

    private OperatingSystem(Family family) {
        this.family = family;
    }

    /**
     * Resolves and returns the current operating system.
     *
     * @return the current operating system.
     */
    static OperatingSystem getCurrent() {
        String osName = System.getenv("os.name").toLowerCase();
        Family osFamily;
        if (osName.contains("linux")) {
            osFamily = Family.LINUX;
        } else if (osName.contains("mac os")) {
            osFamily = Family.MAC_OS;
        } else if (osName.contains("windows")) {
            osFamily = Family.WINDOWS;
        } else if (osName.contains("sunos")) {
            osFamily = Family.SOLARIS;
        } else {
            osFamily = Family.OTHER;
        }
        return new OperatingSystem(osFamily);
    }

    /**
     * @return <code>true</code> if the current operating system is in the Linux family.
     */
    boolean isLinux() {
        return family == Family.LINUX;
    }

    /**
     * @return <code>true</code> if the current operating system is in the Linux family.
     */
    boolean isWindows() {
        return family == Family.WINDOWS;
    }

    /**
     * @return <code>true</code> if the current operating system is in the Linux family.
     */
    boolean isMacOs() {
        return family == Family.MAC_OS;
    }

    /**
     * @return <code>true</code> if the current operating system is in the Linux family.
     */
    boolean isSolaris() {
        return family == Family.SOLARIS;
    }

    /**
     * An operating system family.
     */
    enum Family {
        LINUX, MAC_OS, WINDOWS, SOLARIS, OTHER
    }
}
