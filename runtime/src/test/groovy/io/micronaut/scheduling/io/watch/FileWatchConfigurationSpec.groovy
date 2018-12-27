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
