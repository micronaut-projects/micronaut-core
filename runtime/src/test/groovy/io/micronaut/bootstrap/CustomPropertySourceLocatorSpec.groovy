/*
 * Copyright 2018 original authors
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
package io.micronaut.bootstrap

import io.micronaut.context.ApplicationContext
import io.micronaut.context.DefaultApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.context.env.Environment
import io.micronaut.context.env.PropertySource
import io.micronaut.context.env.PropertySourceLocator
import io.micronaut.core.io.ResourceLoader
import io.micronaut.context.ApplicationContext
import io.micronaut.context.DefaultApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.context.env.Environment
import io.micronaut.context.env.PropertySource
import io.micronaut.context.env.PropertySourceLocator
import io.micronaut.core.io.ResourceLoader
import spock.lang.Specification

import javax.inject.Singleton

/**
 * @author graemerocher
 * @since 1.0
 */
class CustomPropertySourceLocatorSpec extends Specification {


    void "test that a PropertySource from a PropertySourceLocator overrides application config"() {
        given:
        def cl = getClass().getClassLoader()
        ResourceLoader resourceLoader = new ResourceLoader() {
            @Override
            Optional<InputStream> getResourceAsStream(String path) {
                if(path == "bootstrap.properties") {
                    return Optional.of(new ByteArrayInputStream('''\
some.bootstrap.value=bar
some.bootstrap.config=true
'''.bytes))
                }
                return Optional.empty()
            }

            @Override
            ClassLoader getClassLoader() {
                return cl
            }
        }

        ApplicationContext applicationContext = new DefaultApplicationContext(resourceLoader)
        applicationContext.environment.addPropertySource(PropertySource.of(
                'custom.prop.a':'BBB',
                'some.bootstrap.value':'overridden'
        ))
        applicationContext.start()

        expect:
        applicationContext.environment.getProperty('custom.prop.a', String).get() == 'AAA'
        applicationContext.environment.getProperty('some.bootstrap.value', String).get() == 'overridden'
    }

    void "test that a PropertySource from a PropertySourceLocator doesn't override application config when not enabled"() {
        given:
        ApplicationContext applicationContext = new DefaultApplicationContext()
        applicationContext.environment.addPropertySource(PropertySource.of(
                'custom.prop.a':'BBB'
        ))
        applicationContext.start()

        expect:
        applicationContext.environment.getProperty('custom.prop.a', String).get() == 'BBB'
    }

    @Singleton
    @Requires(property = 'some.bootstrap.config')
    static class MyLocator implements PropertySourceLocator {

        @Override
        Optional<PropertySource> load(Environment environment) {
            return Optional.of(
                    PropertySource.of(
                            'custom',
                            ['custom.prop.a': 'AAA']
                    )
            )
        }
    }
}
