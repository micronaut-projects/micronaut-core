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
package io.micronaut.cli.io

import groovy.transform.CompileStatic
import io.micronaut.cli.io.support.SpringIOUtils

import java.nio.file.Paths

/**
 * Utility methods for performing I/O operations.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@CompileStatic
class IOUtils extends SpringIOUtils {

    /**
     * Finds a JAR for the given resource
     *
     * @param resource The resource
     * @return The JAR file or null if it can't be found
     */
    static File findJarFile(URL resource) {
        if (resource?.protocol == 'jar') {
            def absolutePath = resource?.path
            if (absolutePath) {
                try {
                    return Paths.get(new URL(absolutePath.substring(0, absolutePath.lastIndexOf("!"))).toURI()).toFile()
                } catch (MalformedURLException e) {
                    return null
                }
            }
        }
        return null
    }

}
