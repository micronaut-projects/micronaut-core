package io.micronaut.json.env

import io.micronaut.context.ApplicationContextConfiguration
import io.micronaut.context.env.Environment
import io.micronaut.core.io.ResourceLoader
import io.micronaut.core.io.scan.ClassPathResourceLoader
import spock.lang.Specification

import java.util.stream.Stream

class JsonPropertySourceLoaderSpec extends Specification {

    void "test application json is loaded with the default JsonMapper"() {
        given:
        Environment env = Environment.create(new ApplicationContextConfiguration() {
            @Override
            List<String> getEnvironments() {
                return ["test"]
            }

            @Override
            ClassPathResourceLoader getResourceLoader() {
                return new ClassPathResourceLoader() {
                    @Override
                    Optional<InputStream> getResourceAsStream(String path) {
                        if (path.endsWith("-test.json")) {
                            return Optional.empty()
                        }
                        if (path.endsWith("application.json")) {
                            return Optional.of(new ByteArrayInputStream('''\
{
  "server": {
    "port": 8080
  },
  "feature": {
    "enabled": true
  }
}
'''.bytes))
                        }
                        return Optional.empty()
                    }

                    @Override
                    Optional<URL> getResource(String path) {
                        return Optional.empty()
                    }

                    @Override
                    Stream<URL> getResources(String name) {
                        return Stream.empty()
                    }

                    @Override
                    ResourceLoader forBase(String basePath) {
                        return this
                    }
                }
            }
        })

        when:
        env.start()

        then:
        env.get("server.port", Integer).get() == 8080
        env.get("feature.enabled", Boolean).get()
    }
}
