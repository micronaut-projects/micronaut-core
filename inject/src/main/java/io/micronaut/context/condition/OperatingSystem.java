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
package io.micronaut.context.condition;

import io.micronaut.context.annotation.Requires.Family;

import java.util.Locale;

/**
 * Details of the current operating system.
 */
public final class OperatingSystem {

    private static OperatingSystem instance;
    private final Family family;

    private OperatingSystem(Family family) {
        this.family = family;
    }

    /**
     * Resolves and returns the current operating system.
     *
     * @return the current operating system.
     */
    public static OperatingSystem getCurrent() {
        if (instance == null) {
            synchronized (OperatingSystem.class) {
                if (instance == null) {
                    String osName = System.getProperty("os.name").toLowerCase(Locale.ENGLISH);
                    Family osFamily;
                    if (osName.contains("linux")) {
                        osFamily = Family.LINUX;
                    } else if (osName.startsWith("mac") || osName.startsWith("darwin")) {
                        osFamily = Family.MAC_OS;
                    } else if (osName.contains("windows")) {
                        osFamily = Family.WINDOWS;
                    } else if (osName.contains("sunos")) {
                        osFamily = Family.SOLARIS;
                    } else {
                        osFamily = Family.OTHER;
                    }
                    instance = new OperatingSystem(osFamily);
                }
            }
        }
        return instance;
    }

    /**
     * @return <code>true</code> if the current operating system is in the Linux family.
     */
    public boolean isLinux() {
        return family == Family.LINUX;
    }

    /**
     * @return <code>true</code> if the current operating system is in the Windows family.
     */
    public boolean isWindows() {
        return family == Family.WINDOWS;
    }

    /**
     * @return <code>true</code> if the current operating system is in the Mac OS family.
     */
    public boolean isMacOs() {
        return family == Family.MAC_OS;
    }

    /**
     * @return <code>true</code> if the current operating system is in the Solaris family.
     */
    public boolean isSolaris() {
        return family == Family.SOLARIS;
    }

    /**
     * @return The OS family
     */
    public Family getFamily() {
        return family;
    }

}
