package org.particleframework.management.health.indicator.diskspace

import org.particleframework.context.ApplicationContext
import org.particleframework.context.env.PropertySource
import spock.lang.Specification

class DiskSpaceIndicatorConfigurationSpec extends Specification {

    void "test threshold configuration"() {
        given:
        ApplicationContext context = ApplicationContext.build("test")
        context.environment.addPropertySource(PropertySource.of(['endpoints.health.disk-space.threshold': '100GB']))
        context.start()

        expect:
        context.getBean(DiskSpaceIndicatorConfiguration).threshold == 1024L * 1024L * 1024L * 100L

        cleanup:
        context.close()
    }

    void "test path configuration"() {
        given:
        ApplicationContext context = ApplicationContext.build("test")
        context.environment.addPropertySource(PropertySource.of(['endpoints.health.disk-space.path': '/foo']))
        context.start()

        expect:
        context.getBean(DiskSpaceIndicatorConfiguration).path.absolutePath == "/foo"

        cleanup:
        context.close()
    }
}
