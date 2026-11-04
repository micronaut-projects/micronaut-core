/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.context

import groovy.transform.CompileStatic
import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.exceptions.BeanInstantiationException
import io.micronaut.context.watch.BeanDefinitionWatcher
import io.micronaut.context.watch.BeanWatch
import io.micronaut.context.watch.ExecutableMethodWatcher
import io.micronaut.inject.BeanDefinitionReference
import io.micronaut.inject.context.watch.Buzz
import jakarta.inject.Singleton
import org.slf4j.LoggerFactory
import spock.lang.Shared

class BeanWatchRegistryAbsentClassSpec extends AbstractTypeElementSpec {

    private static final String ABSENT = 'test.$Absent$Definition'
    private static final String BROKEN = 'test.$Broken$Definition'
    private static final String FOUND = 'test.$Found$Definition'

    @Shared
    ClassLoader compiled

    // logback is on the test runtime classpath only
    def logger = LoggerFactory.getLogger(BeanWatchRegistry)
    def appender = Class.forName('ch.qos.logback.core.read.ListAppender').getDeclaredConstructor().newInstance()

    void setupSpec() {
        compiled = buildClassLoader('test.Found', '''
package test;

import io.micronaut.inject.context.watch.Buzz;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
class Found {
    @Buzz
    public void buzz() {
    }
}

@Singleton
class Absent {
    @Inject
    void setMissing(Missing missing) {
    }
}

@Singleton
class Broken {
    @Inject
    void setFailing(Failing failing) {
    }
}

class Missing {
}

class Failing {
}
''')
    }

    void setup() {
        appender.start()
        logger.addAppender(appender)
    }

    void cleanup() {
        logger.detachAppender(appender)
    }

    void "a watch of #watched skips a definition with an absent class quietly and lists the others"() {
        given: "a context whose first definition refers to a class missing at runtime, as with an optional dependency"
        ApplicationContext context = start(ABSENT, FOUND)
        List<Object> batches = []

        when:
        BeanWatch watch = register(context, batches)

        then: "the first batch came without an error, with the definitions that do load"
        batches.size() == 1
        batches[0].added().size() == 1
        errors().isEmpty()

        cleanup:
        watch?.close()
        context?.close()

        where:
        register << [BeanWatchRegistryAbsentClassSpec.&watchMethods, BeanWatchRegistryAbsentClassSpec.&watchStereotype]
        watched << ['methods', 'a stereotype']
    }

    void "a definition with an absent class still fails a lookup of its own type"() {
        given:
        ApplicationContext context = start(ABSENT, FOUND)

        when:
        context.getBeanDefinition(context.classLoader.loadClass('test.Absent'))

        then:
        def e = thrown(BeanInstantiationException)
        e.message.contains('test/Missing')

        cleanup:
        context?.close()
    }

    void "a watch of #watched reports a definition that failed for any other reason"() {
        given:
        ApplicationContext context = start(BROKEN, FOUND)
        List<Object> batches = []

        when:
        BeanWatch watch = register(context, batches)

        then:
        batches.isEmpty()
        errors().any { it.contains('failed to process a change') && it.contains('the loader failed') }

        cleanup:
        watch?.close()
        context?.close()

        where:
        register << [BeanWatchRegistryAbsentClassSpec.&watchMethods, BeanWatchRegistryAbsentClassSpec.&watchStereotype]
        watched << ['methods', 'a stereotype']
    }

    private static BeanWatch watchMethods(ApplicationContext context, List<Object> batches) {
        return ((WatchableBeanContext) context).methods(Buzz).watch({ batches.add(it) } as ExecutableMethodWatcher)
    }

    private static BeanWatch watchStereotype(ApplicationContext context, List<Object> batches) {
        return ((WatchableBeanContext) context).definitions().stereotype(Singleton).watch({ batches.add(it) } as BeanDefinitionWatcher)
    }

    private List<String> errors() {
        return appender.list.findAll { it.level.toString() == 'ERROR' }*.formattedMessage
    }

    private ApplicationContext start(String... definitions) {
        ClassLoader loader = new RestrictedLoader(compiled)
        def builder = ApplicationContext.builder()
        builder.classLoader(loader)
        builder.beanDefinitionsProvider {
            references(loader, definitions) + getBuiltInBeanReferences()
        }
        return builder.build().start()
    }

    // statically compiled: a dynamic call on a definition would introspect it and meet the missing class
    @CompileStatic
    private static List<BeanDefinitionReference<?>> references(ClassLoader loader, String[] definitions) {
        List<BeanDefinitionReference<?>> references = []
        for (String definition : definitions) {
            references.add((BeanDefinitionReference<?>) loader.loadClass(definition).getDeclaredConstructor().newInstance())
        }
        return references
    }

    /**
     * Defines the compiled classes itself, so that one can be missing for them and another can fail to load.
     */
    private static final class RestrictedLoader extends ClassLoader {
        private final ClassLoader compiled

        RestrictedLoader(ClassLoader compiled) {
            super(BeanWatchRegistryAbsentClassSpec.classLoader)
            this.compiled = compiled
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (name == 'test.Missing') {
                throw new ClassNotFoundException(name)
            }
            if (name == 'test.Failing') {
                throw new IllegalStateException('the loader failed')
            }
            if (!name.startsWith('test.')) {
                return super.loadClass(name, resolve)
            }
            Class<?> loaded = findLoadedClass(name)
            if (loaded != null) {
                return loaded
            }
            List<URL> resources = compiled.getResources(name.replace('.', '/') + '.class').toList()
            if (resources.isEmpty()) {
                throw new ClassNotFoundException(name)
            }
            byte[] code = resources.first().openStream().withCloseable { it.readAllBytes() }
            return defineClass(name, code, 0, code.length)
        }
    }
}
