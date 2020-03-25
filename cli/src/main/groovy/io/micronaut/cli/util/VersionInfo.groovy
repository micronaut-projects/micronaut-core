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
package io.micronaut.cli.util

import io.micronaut.cli.MicronautCli

class VersionInfo {

    static String getVersion(Class clazz) {
        def version = clazz.getPackage()?.getImplementationVersion()

        if(!version) {
            Properties prop = new Properties()
            prop.load(MicronautCli.getClassLoader().getResourceAsStream("META-INF/MANIFEST.MF"))

            version = prop.getProperty("Implementation-Version")
        }

        version
    }

    static int getJavaVersion() {
        String version = System.getProperty("java.version")
        if (version.startsWith("1.")) {
            version = version.substring(2)
        }
        // Allow these formats:
        // 1.8.0_72-ea
        // 9-ea
        // 9
        // 9.0.1
        int dotPos = version.indexOf('.')
        int dashPos = version.indexOf('-')
        return Integer.parseInt(version.substring(0,
                dotPos > -1 ? dotPos : dashPos > -1 ? dashPos : version.size()))
    }

    static String getJdkVersion() {
        String version = System.getProperty("java.version")
        int dotPos = version.indexOf('.')
        int dashPos = version.indexOf('-')

        if (version.startsWith("1.")) {
            dotPos += 2
        }

        return version.substring(0,
                dotPos > -1 ? dotPos : dashPos > -1 ? dashPos : version.size())
    }

}
