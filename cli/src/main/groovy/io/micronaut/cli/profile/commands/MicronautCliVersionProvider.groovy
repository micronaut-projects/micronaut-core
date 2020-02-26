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
package io.micronaut.cli.profile.commands

import groovy.transform.CompileStatic
import io.micronaut.cli.MicronautCli
import io.micronaut.cli.util.VersionInfo
import picocli.CommandLine.IVersionProvider

/**
 * Generates version information. Example usage:
 * <pre>
 * &#064;Command(name = 'command', versionProvider = MicronautCliVersionProvider)
 * class App {
 *     &#064;Option(names = ["-V", "--version"], versionHelp = true, description = 'Display version information and quit.')
 *     boolean isVersionRequested
 * }
 * </pre>
 * Or use picocli's built-in standard usage help option (--help and -h) and version help option (-V and --version).
 * <pre>
 * &#064;Command(name = 'command', mixinStandardHelpOptions = true, versionProvider = MicronautCliVersionProvider)
 * class App {
 *     // ...
 * }
 * </pre>
 *
 * @author Remko Popma
 * @version 1.0
 */
@CompileStatic
class MicronautCliVersionProvider implements IVersionProvider {
    String[] getVersion() {
        ["Micronaut Version: ${VersionInfo.getVersion(MicronautCli)}",
         "JVM Version: ${System.getProperty('java.version')}"] as String[]
    }
}
