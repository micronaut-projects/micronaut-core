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
package io.micronaut.context.python;

import io.micronaut.context.BeanContext;
import io.micronaut.context.annotation.Property;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.context.event.StartupEvent;
import io.micronaut.context.python.annotation.EventLoopSafe;
import io.micronaut.context.python.annotation.EventLoopSafeOwner;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.util.StringUtils;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.inject.qualifiers.Qualifiers;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Creates, when the application starts, the singleton beans of the generated classes declaring a
 * route whose compiled body never enters Python ({@link EventLoopSafe}): creating the bean runs
 * Python (the constructor of the Python class, the injection of a module's attributes), which the
 * first request of a route kept on the event loop would otherwise do there, holding the event loop
 * until the Python context is free. Creating the bean also primes the slots of its attributes fixed
 * at construction ({@code PythonFixedAttributes#prime}).
 * <p>
 * {@link PythonExecutorSelector} keeps a route on the event loop only when its bean was created
 * here: a bean of another scope is created again (a prototype, per request), and one that failed to
 * be created here is created when it is first needed, both on the IO executor.
 *
 * @author Graeme Rocher
 * @since 5.3.0
 */
@Singleton
@Internal
final class PythonEventLoopBeans implements ApplicationEventListener<StartupEvent> {

    private static final Logger LOG = LoggerFactory.getLogger(PythonEventLoopBeans.class);
    private static final String HTTP_METHOD_MAPPING = "io.micronaut.http.annotation.HttpMethodMapping";

    private final BeanContext beanContext;
    private final boolean eventLoop;
    /** The declaring types of the event-loop-safe methods of the beans created at startup. */
    private final Set<Class<?>> created = ConcurrentHashMap.newKeySet();
    /** The declaring types of the event-loop-safe methods of a bean not created at startup. */
    private final Set<Class<?>> excluded = ConcurrentHashMap.newKeySet();

    /**
     * @param beanContext The bean context
     * @param eventLoop   Whether a compiled route that never enters Python may stay on the event loop
     */
    PythonEventLoopBeans(BeanContext beanContext,
                         @Property(name = PythonExecutorSelector.EVENT_LOOP_PROPERTY, defaultValue = StringUtils.TRUE) boolean eventLoop) {
        this.beanContext = beanContext;
        this.eventLoop = eventLoop;
    }

    @Override
    public void onApplicationEvent(StartupEvent event) {
        if (!eventLoop) {
            return;
        }
        for (BeanDefinition<?> definition : beanContext.getBeanDefinitions(Qualifiers.byStereotype(EventLoopSafeOwner.class))) {
            create(definition);
        }
    }

    private void create(BeanDefinition<?> definition) {
        Set<Class<?>> declaring = new HashSet<>();
        for (ExecutableMethod<?, ?> method : definition.getExecutableMethods()) {
            // a route: no executor is selected for any other method
            if (method.hasAnnotation(EventLoopSafe.class) && method.hasStereotype(HTTP_METHOD_MAPPING)) {
                declaring.add(method.getDeclaringType());
            }
        }
        if (declaring.isEmpty()) {
            return;
        }
        if (!definition.isSingleton()) {
            excluded.addAll(declaring);
            return;
        }
        try {
            beanContext.getBean(definition);
            created.addAll(declaring);
        } catch (RuntimeException e) {
            // created when first needed, as any bean is: its routes run on the IO executor
            excluded.addAll(declaring);
            if (LOG.isDebugEnabled()) {
                LOG.debug("The bean [{}] was not created at startup; its compiled routes run on the IO executor", definition.getBeanType().getName(), e);
            }
        }
    }

    /**
     * @param declaringType The class declaring an event-loop-safe method
     * @return Whether its bean was created at startup
     */
    boolean createdAtStartup(Class<?> declaringType) {
        return created.contains(declaringType) && !excluded.contains(declaringType);
    }
}
