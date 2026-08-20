package io.micronaut.context.env

import io.micronaut.context.env.dotenv.DotEnvPropertySourceLoader
import io.micronaut.core.io.service.ServiceDefinition
import io.micronaut.core.io.service.SoftServiceLoader
import spock.lang.Specification

class DotEnvPropertySourceLoaderSpec extends Specification {

    void "test basic properties"() {
        given:
        def serviceDefinition = Mock(ServiceDefinition)
        serviceDefinition.isPresent() >> true
        serviceDefinition.load() >> new DotEnvPropertySourceLoader()

        Environment env = new DefaultEnvironment({ ["test"] }) {
            @Override
            protected SoftServiceLoader<PropertySourceLoader> readPropertySourceLoaders() {
                GroovyClassLoader gcl = new GroovyClassLoader()
                gcl.addURL(DotEnvPropertySourceLoader.getResource("/META-INF/services/io.micronaut.context.env.PropertySourceLoader"))
                return new SoftServiceLoader<PropertySourceLoader>(PropertySourceLoader, gcl)
            }

            @Override
            Optional<InputStream> getResourceAsStream(String path) {
                if (path.endsWith(".env.test")) {
                    return Optional.of(new ByteArrayInputStream('''
HIBERNATE_CACHE_QUERIES=false
DATASOURCE_POOLED=true
DATASOURCE_DRIVER_CLASS_NAME=org.h2.Driver
'''.bytes))
                } else if (path.endsWith(".env")) {
                    return Optional.of(new ByteArrayInputStream('''
HIBERNATE_CACHE_QUERIES=false
DATASOURCE_POOLED=true
DATASOURCE_DRIVER_CLASS_NAME=org.postgres.Driver
DATASOURCE_USERNAME=sa
'''.bytes))
                }

                return Optional.empty()
            }
        }

        when:
        env.start()

        then:
        env.get("hibernate.cache.queries", Boolean).get() == false
        env.get("datasource.pooled", Boolean).get() == true
        env.get("datasource.driver.class.name", String).get() == 'org.h2.Driver'
        env.get("datasource.username", String).get() == 'sa'

    }

    void "test quoted properties"() {
        given:
        def serviceDefinition = Mock(ServiceDefinition)
        serviceDefinition.isPresent() >> true
        serviceDefinition.load() >> new DotEnvPropertySourceLoader()

        Environment env = new DefaultEnvironment({ ["test"] }) {
            @Override
            protected SoftServiceLoader<PropertySourceLoader> readPropertySourceLoaders() {
                GroovyClassLoader gcl = new GroovyClassLoader()
                gcl.addURL(DotEnvPropertySourceLoader.getResource("/META-INF/services/io.micronaut.context.env.PropertySourceLoader"))
                return new SoftServiceLoader<PropertySourceLoader>(PropertySourceLoader, gcl)
            }

            @Override
            Optional<InputStream> getResourceAsStream(String path) {
                if (path.endsWith(".env.test")) {
                    return Optional.of(new ByteArrayInputStream('''
HIBERNATE_CACHE_QUERIES="false"
DATASOURCE_POOLED='true'
DATASOURCE_DRIVER_CLASS_NAME='org.h2.Driver'
'''.bytes))
                }

                return Optional.empty();
            }
        }

        when:
        env.start()

        then:
        env.get("hibernate.cache.queries", Boolean).get() == false
        env.get("datasource.pooled", Boolean).get() == true
        env.get("datasource.driver.class.name", String).get() == 'org.h2.Driver'

    }

    void "test escaped characters in double quotes"() {
        given:
        def serviceDefinition = Mock(ServiceDefinition)
        serviceDefinition.isPresent() >> true
        serviceDefinition.load() >> new DotEnvPropertySourceLoader()

        Environment env = new DefaultEnvironment({ ["test"] }) {
            @Override
            protected SoftServiceLoader<PropertySourceLoader> readPropertySourceLoaders() {
                GroovyClassLoader gcl = new GroovyClassLoader()
                gcl.addURL(DotEnvPropertySourceLoader.getResource("/META-INF/services/io.micronaut.context.env.PropertySourceLoader"))
                return new SoftServiceLoader<PropertySourceLoader>(PropertySourceLoader, gcl)
            }

            @Override
            Optional<InputStream> getResourceAsStream(String path) {
                if (path.endsWith(".env.test")) {
                    return Optional.of(new ByteArrayInputStream('''
KEY1="VAL1\\nVAL2"
KEY2="VAL1\\tVAL2"
KEY3="VAL1\\rVAL2"
KEY4="VAL1\\$KEY1"
KEY5="VAL1\\\\VAL2"
'''.bytes))
                }

                return Optional.empty();
            }
        }

        when:
        env.start()

        then:
        env.get("key1", String).get() == "VAL1\nVAL2"
        env.get("key2", String).get() == "VAL1\tVAL2"
        env.get("key3", String).get() == "VAL1\rVAL2"
        env.get("key4", String).get() == "VAL1\$KEY1"
        env.get("key5", String).get() == "VAL1\\VAL2"
    }

    void "test variable substitution with braces"() {
        given:
        def serviceDefinition = Mock(ServiceDefinition)
        serviceDefinition.isPresent() >> true
        serviceDefinition.load() >> new DotEnvPropertySourceLoader()

        Environment env = new DefaultEnvironment({ ["test"] }) {
            @Override
            protected SoftServiceLoader<PropertySourceLoader> readPropertySourceLoaders() {
                GroovyClassLoader gcl = new GroovyClassLoader()
                gcl.addURL(DotEnvPropertySourceLoader.getResource("/META-INF/services/io.micronaut.context.env.PropertySourceLoader"))
                return new SoftServiceLoader<PropertySourceLoader>(PropertySourceLoader, gcl)
            }

            @Override
            Optional<InputStream> getResourceAsStream(String path) {
                if (path.endsWith(".env.test")) {
                    return Optional.of(new ByteArrayInputStream('''
BASE_URL=https://api.example.com
API_VERSION=v1
FULL_URL=\${BASE_URL}/\${API_VERSION}/users
'''.bytes))
                }

                return Optional.empty();
            }
        }

        when:
        env.start()

        then:
        env.get("base.url", String).get() == "https://api.example.com"
        env.get("api.version", String).get() == "v1"
        env.get("full.url", String).get() == "https://api.example.com/v1/users"
    }

    void "test variable substitution without braces"() {
        given:
        def serviceDefinition = Mock(ServiceDefinition)
        serviceDefinition.isPresent() >> true
        serviceDefinition.load() >> new DotEnvPropertySourceLoader()

        Environment env = new DefaultEnvironment({ ["test"] }) {
            @Override
            protected SoftServiceLoader<PropertySourceLoader> readPropertySourceLoaders() {
                GroovyClassLoader gcl = new GroovyClassLoader()
                gcl.addURL(DotEnvPropertySourceLoader.getResource("/META-INF/services/io.micronaut.context.env.PropertySourceLoader"))
                return new SoftServiceLoader<PropertySourceLoader>(PropertySourceLoader, gcl)
            }

            @Override
            Optional<InputStream> getResourceAsStream(String path) {
                if (path.endsWith(".env.test")) {
                    return Optional.of(new ByteArrayInputStream('''
TESTHOME=/home/user
TESTPATH=\$TESTHOME/bin:\$TESTHOME/local
'''.bytes))
                }

                return Optional.empty();
            }
        }

        when:
        env.start()

        then:
        env.get("testhome", String).get() == "/home/user"
        env.get("testpath", String).get() == "/home/user/bin:/home/user/local"
    }

    void "test chained variable substitution"() {
        given:
        def serviceDefinition = Mock(ServiceDefinition)
        serviceDefinition.isPresent() >> true
        serviceDefinition.load() >> new DotEnvPropertySourceLoader()

        Environment env = new DefaultEnvironment({ ["test"] }) {
            @Override
            protected SoftServiceLoader<PropertySourceLoader> readPropertySourceLoaders() {
                GroovyClassLoader gcl = new GroovyClassLoader()
                gcl.addURL(DotEnvPropertySourceLoader.getResource("/META-INF/services/io.micronaut.context.env.PropertySourceLoader"))
                return new SoftServiceLoader<PropertySourceLoader>(PropertySourceLoader, gcl)
            }

            @Override
            Optional<InputStream> getResourceAsStream(String path) {
                if (path.endsWith(".env.test")) {
                    return Optional.of(new ByteArrayInputStream('''
KEY_A=value_a
KEY_B=\${KEY_A}_b
KEY_C=\${KEY_B}_c
'''.bytes))
                }

                return Optional.empty();
            }
        }

        when:
        env.start()

        then:
        env.get("key.a", String).get() == "value_a"
        env.get("key.b", String).get() == "value_a_b"
        env.get("key.c", String).get() == "value_a_b_c"
    }

    void "test multiline values with double quotes"() {
        given:
        def serviceDefinition = Mock(ServiceDefinition)
        serviceDefinition.isPresent() >> true
        serviceDefinition.load() >> new DotEnvPropertySourceLoader()

        Environment env = new DefaultEnvironment({ ["test"] }) {
            @Override
            protected SoftServiceLoader<PropertySourceLoader> readPropertySourceLoaders() {
                GroovyClassLoader gcl = new GroovyClassLoader()
                gcl.addURL(DotEnvPropertySourceLoader.getResource("/META-INF/services/io.micronaut.context.env.PropertySourceLoader"))
                return new SoftServiceLoader<PropertySourceLoader>(PropertySourceLoader, gcl)
            }

            @Override
            Optional<InputStream> getResourceAsStream(String path) {
                if (path.endsWith(".env.test")) {
                    return Optional.of(new ByteArrayInputStream('''
MULTILINE="line1
line2
line3"
'''.bytes))
                }

                return Optional.empty();
            }
        }

        when:
        env.start()

        then:
        env.get("multiline", String).get() == "line1\nline2\nline3"
    }

    void "test comments"() {
        given:
        def serviceDefinition = Mock(ServiceDefinition)
        serviceDefinition.isPresent() >> true
        serviceDefinition.load() >> new DotEnvPropertySourceLoader()

        Environment env = new DefaultEnvironment({ ["test"] }) {
            @Override
            protected SoftServiceLoader<PropertySourceLoader> readPropertySourceLoaders() {
                GroovyClassLoader gcl = new GroovyClassLoader()
                gcl.addURL(DotEnvPropertySourceLoader.getResource("/META-INF/services/io.micronaut.context.env.PropertySourceLoader"))
                return new SoftServiceLoader<PropertySourceLoader>(PropertySourceLoader, gcl)
            }

            @Override
            Optional<InputStream> getResourceAsStream(String path) {
                if (path.endsWith(".env.test")) {
                    return Optional.of(new ByteArrayInputStream('''
KEY1=value1
KEY2=value2 # inline comment
KEY3="value with # not a comment"
'''.bytes))
                }
                return Optional.empty();
            }
        }

        when:
        env.start()

        then:
        env.get("key1", String).get() == "value1"
        env.get("key2", String).get() == "value2"
        env.get("key3", String).get() == "value with # not a comment"
    }

    void "test single quotes preserve literals"() {
        given:
        def serviceDefinition = Mock(ServiceDefinition)
        serviceDefinition.isPresent() >> true
        serviceDefinition.load() >> new DotEnvPropertySourceLoader()

        Environment env = new DefaultEnvironment({ ["test"] }) {
            @Override
            protected SoftServiceLoader<PropertySourceLoader> readPropertySourceLoaders() {
                GroovyClassLoader gcl = new GroovyClassLoader()
                gcl.addURL(DotEnvPropertySourceLoader.getResource("/META-INF/services/io.micronaut.context.env.PropertySourceLoader"))
                return new SoftServiceLoader<PropertySourceLoader>(PropertySourceLoader, gcl)
            }

            @Override
            Optional<InputStream> getResourceAsStream(String path) {
                if (path.endsWith(".env.test")) {
                    return Optional.of(new ByteArrayInputStream('''
KEY1='\\n\\t\\r not processed'
KEY2='\$VAR not substituted'
KEY3='literal "quotes" inside'
'''.bytes))
                }

                return Optional.empty();
            }
        }

        when:
        env.start()

        then:
        env.get("key1", String).get() == '\\n\\t\\r not processed'
        env.get("key2", String).get() == '\$VAR not substituted'
        env.get("key3", String).get() == 'literal "quotes" inside'
    }

    void "test empty values and whitespace"() {
        given:
        def serviceDefinition = Mock(ServiceDefinition)
        serviceDefinition.isPresent() >> true
        serviceDefinition.load() >> new DotEnvPropertySourceLoader()

        Environment env = new DefaultEnvironment({ ["test"] }) {
            @Override
            protected SoftServiceLoader<PropertySourceLoader> readPropertySourceLoaders() {
                GroovyClassLoader gcl = new GroovyClassLoader()
                gcl.addURL(DotEnvPropertySourceLoader.getResource("/META-INF/services/io.micronaut.context.env.PropertySourceLoader"))
                return new SoftServiceLoader<PropertySourceLoader>(PropertySourceLoader, gcl)
            }

            @Override
            Optional<InputStream> getResourceAsStream(String path) {
                if (path.endsWith(".env.test")) {
                    return Optional.of(new ByteArrayInputStream('''
EMPTY=
SPACES_AROUND = value
QUOTED_SPACES="  spaces  "
'''.bytes))
                }

                return Optional.empty();
            }
        }

        when:
        env.start()

        then:
        !env.containsProperty("empty");
        env.get("spaces.around", String).get() == "value"
        env.get("quoted.spaces", String).get() == "  spaces  "
    }

    void "test mixed quotes"() {
        given:
        def serviceDefinition = Mock(ServiceDefinition)
        serviceDefinition.isPresent() >> true
        serviceDefinition.load() >> new DotEnvPropertySourceLoader()

        Environment env = new DefaultEnvironment({ ["test"] }) {
            @Override
            protected SoftServiceLoader<PropertySourceLoader> readPropertySourceLoaders() {
                GroovyClassLoader gcl = new GroovyClassLoader()
                gcl.addURL(DotEnvPropertySourceLoader.getResource("/META-INF/services/io.micronaut.context.env.PropertySourceLoader"))
                return new SoftServiceLoader<PropertySourceLoader>(PropertySourceLoader, gcl)
            }

            @Override
            Optional<InputStream> getResourceAsStream(String path) {
                if (path.endsWith(".env.test")) {
                    return Optional.of(new ByteArrayInputStream('''
KEY1="double with 'single' inside"
KEY2='single with "double" inside'
'''.bytes))
                }

                return Optional.empty();
            }
        }

        when:
        env.start()

        then:
        env.get("key1", String).get() == "double with 'single' inside"
        env.get("key2", String).get() == 'single with "double" inside'
    }

    void "test invalid keys are skipped"() {
        given:
        def serviceDefinition = Mock(ServiceDefinition)
        serviceDefinition.isPresent() >> true
        serviceDefinition.load() >> new DotEnvPropertySourceLoader()

        Environment env = new DefaultEnvironment({ ["test"] }) {
            @Override
            protected SoftServiceLoader<PropertySourceLoader> readPropertySourceLoaders() {
                GroovyClassLoader gcl = new GroovyClassLoader()
                gcl.addURL(DotEnvPropertySourceLoader.getResource("/META-INF/services/io.micronaut.context.env.PropertySourceLoader"))
                return new SoftServiceLoader<PropertySourceLoader>(PropertySourceLoader, gcl)
            }

            @Override
            Optional<InputStream> getResourceAsStream(String path) {
                if (path.endsWith(".env.test")) {
                    return Optional.of(new ByteArrayInputStream('''
VALID_KEY=value1
123_INVALID=value2
DASHED-KEY=value3
ANOTHER_VALID=value4
'''.bytes))
                }

                return Optional.empty();
            }
        }

        when:
        env.start()

        then:
        env.get("valid.key", String).get() == "value1"
        env.get("another.valid", String).get() == "value4"
        !env.containsProperty("123.invalid")
        !env.containsProperty("dashed-key")
    }

    void "test undefined variable substitution"() {
        given:
        def serviceDefinition = Mock(ServiceDefinition)
        serviceDefinition.isPresent() >> true
        serviceDefinition.load() >> new DotEnvPropertySourceLoader()

        Environment env = new DefaultEnvironment({ ["test"] }) {
            @Override
            protected SoftServiceLoader<PropertySourceLoader> readPropertySourceLoaders() {
                GroovyClassLoader gcl = new GroovyClassLoader()
                gcl.addURL(DotEnvPropertySourceLoader.getResource("/META-INF/services/io.micronaut.context.env.PropertySourceLoader"))
                return new SoftServiceLoader<PropertySourceLoader>(PropertySourceLoader, gcl)
            }

            @Override
            Optional<InputStream> getResourceAsStream(String path) {
                if (path.endsWith(".env.test")) {
                    return Optional.of(new ByteArrayInputStream('''
KEY=prefix_\${UNDEFINED_VAR}_suffix
'''.bytes))
                }

                return Optional.empty();
            }
        }

        when:
        env.start()

        then:
        env.get("key", String).get() == "prefix__suffix"
    }

    void "test malformed key in variable substitution"() {
        given:
        def serviceDefinition = Mock(ServiceDefinition)
        serviceDefinition.isPresent() >> true
        serviceDefinition.load() >> new DotEnvPropertySourceLoader()

        Environment env = new DefaultEnvironment({ ["test"] }) {
            @Override
            protected SoftServiceLoader<PropertySourceLoader> readPropertySourceLoaders() {
                GroovyClassLoader gcl = new GroovyClassLoader()
                gcl.addURL(DotEnvPropertySourceLoader.getResource("/META-INF/services/io.micronaut.context.env.PropertySourceLoader"))
                return new SoftServiceLoader<PropertySourceLoader>(PropertySourceLoader, gcl)
            }

            @Override
            Optional<InputStream> getResourceAsStream(String path) {
                if (path.endsWith(".env.test")) {
                    return Optional.of(new ByteArrayInputStream('''
KEY=prefix_\${MALFORMED-KEY}_suffix
'''.bytes))
                }

                return Optional.empty();
            }
        }

        when:
        env.start()

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Line 2: Malformed variable reference starting with 'MALFORMED' - variables must contain only letters, digits, and underscores"
    }

    void "test unclosed braces in variable substitution"() {
        given:
        def serviceDefinition = Mock(ServiceDefinition)
        serviceDefinition.isPresent() >> true
        serviceDefinition.load() >> new DotEnvPropertySourceLoader()

        Environment env = new DefaultEnvironment({ ["test"] }) {
            @Override
            protected SoftServiceLoader<PropertySourceLoader> readPropertySourceLoaders() {
                GroovyClassLoader gcl = new GroovyClassLoader()
                gcl.addURL(DotEnvPropertySourceLoader.getResource("/META-INF/services/io.micronaut.context.env.PropertySourceLoader"))
                return new SoftServiceLoader<PropertySourceLoader>(PropertySourceLoader, gcl)
            }

            @Override
            Optional<InputStream> getResourceAsStream(String path) {
                if (path.endsWith(".env.test")) {
                    return Optional.of(new ByteArrayInputStream('''
KEY=prefix_\${UNCLOSED_BRACES
'''.bytes))
                }

                return Optional.empty();
            }
        }

        when:
        env.start()

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Line 2: Unclosed variable brace: \${UNCLOSED_BRACES - missing closing '}'"
    }

}
