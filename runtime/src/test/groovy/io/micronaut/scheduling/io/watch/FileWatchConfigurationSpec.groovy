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
package io.micronaut.scheduling.io.watch

import io.micronaut.context.ApplicationContext
import spock.lang.Specification

import java.nio.file.Path

class FileWatchConfigurationSpec extends Specification {

    void "test file watch config"() {
        given:
        ApplicationContext context = ApplicationContext.run(
                (FileWatchConfiguration.PATHS) : 'src/main'
        )
        FileWatchConfiguration config = context.getBean(FileWatchConfiguration)

        expect:
        config.isEnabled()
        !config.isRestart()
        config.paths.size() == 1
        config.paths.first() instanceof Path


        cleanup:
        context.close()
    }
}
