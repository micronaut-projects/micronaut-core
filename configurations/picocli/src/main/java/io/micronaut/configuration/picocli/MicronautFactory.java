/*
 * Copyright 2017-2018 original authors
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

package io.micronaut.configuration.picocli;

import io.micronaut.context.env.Environment;
import io.micronaut.context.*;
import io.micronaut.context.exceptions.*;
import picocli.CommandLine.IFactory;

import java.util.Objects;

/**
 * An {@link ApplicationContext}-based implementation of the picocli {@code IFactory} interface.
 * <p>
 * Specifying this factory when instantiating a {@code CommandLine} or invoking the
 * {@code CommandLine.run} or {@code CommandLine.call} methods allows picocli to
 * leverage Micronaut dependency injection.
 * </p><p>
 * {@linkplain #close() Closing} this factory will close the underlying {@code ApplicationContext}.
 * </p>
 *
 * @author Remko Popma
 * @since 1.0
 */
public class MicronautFactory implements IFactory, AutoCloseable {
    private final ApplicationContext ctx;

    /**
     * Constructs a {@code MicronautFactory} with the result of calling
     * {@code ApplicationContext.run("cli")}.
     */
    public MicronautFactory() {
        this(ApplicationContext.run(Environment.CLI));
    }

    /**
     * Constructs a {@code MicronautFactory} with the specified {@code ApplicationContext}.
     *
     * @param applicationContext the context to use to look up or instantiate beans
     */
    public MicronautFactory(ApplicationContext applicationContext) {
        this.ctx = Objects.requireNonNull(applicationContext, "applicationContext");
        if (!ctx.isRunning()) {
            ctx.start();
        }
    }

    /**
     * Delegates to the {@code ApplicationContext} to either find or instantiate a bean of the specified type.
     * @param cls the class of the bean to return
     * @param <K> the type of the bean to return
     * @return an instance of the specified class
     * @throws NoSuchBeanException if no bean of the specified type exists
     * @throws Exception if a problem occurred during lookup or instantiation
     */
    @Override
    public <K> K create(Class<K> cls) throws Exception {
        return ctx.findOrInstantiateBean(cls).orElseThrow(() -> new NoSuchBeanException(cls));
    }

    /**
     * Closes the underlying {@code ApplicationContext}.
     *
     * @throws Exception if the underlying application context could not be closed
     */
    @Override
    public void close() throws Exception {
        ctx.close();
    }
}
