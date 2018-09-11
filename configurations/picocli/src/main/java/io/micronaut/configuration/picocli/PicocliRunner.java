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

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.Environment;
import picocli.CommandLine;
import picocli.CommandLine.*;

import java.util.concurrent.Callable;

/**
 * Utility class with convenience methods for running picocli-based commands with
 * a micronaut application context.
 *
 * @author Remko Popma
 * @since 1.0
 */
public class PicocliRunner {
    /**
     * Instantiates a new {@link ApplicationContext} for the {@link Environment#CLI} environment,
     * obtains an instance of the specified {@code Callable} command class from the context,
     * injecting any beans as required,
     * then parses the specified command line arguments, populating fields and methods annotated
     * with picocli {@link Option @Option} and {@link Parameters @Parameters}
     * annotations, and finally calls the command and returns the result.
     * <p>
     * The {@code ApplicationContext} is {@linkplain ApplicationContext#close() closed} before this method returns.
     * </p>
     * @param cls the Callable command class
     * @param args the command line arguments
     * @param <C> The callable type
     * @param <T> The callable return type
     * @return {@code null} if an error occurred while parsing the command line options,
     *      or if help was requested and printed. Otherwise returns the result of calling the Callable
     * @throws InitializationException if the specified command object does not have
     *          a {@link Command}, {@link Option} or {@link Parameters} annotation
     * @throws ExecutionException if the Callable throws an exception
     * @throws Exception if the ApplicationContext could not be closed
     */
    public static <C extends Callable<T>, T> T call(Class<C> cls, String... args) throws Exception {
        try (ApplicationContext ctx = ApplicationContext.build(cls, Environment.CLI).build()) {
            return call(cls, ctx, args);
        }
    }

    /**
     * Obtains an instance of the specified {@code Callable} command class from the specified context,
     * injecting any beans from the specified context as required,
     * then parses the specified command line arguments, populating fields and methods annotated
     * with picocli {@link Option @Option} and {@link Parameters @Parameters}
     * annotations, and finally calls the command and returns the result.
     * <p>
     * The caller is responsible for {@linkplain ApplicationContext#close() closing} the context.
     * </p>
     * @param cls the Callable command class
     * @param ctx the ApplicationContext that injects dependencies into the command
     * @param args the command line arguments
     * @param <C> The callable type
     * @param <T> The callable return type

     * @return {@code null} if an error occurred while parsing the command line options,
     *      or if help was requested and printed. Otherwise returns the result of calling the Callable
     * @throws InitializationException if the specified command object does not have
     *          a {@link Command}, {@link Option} or {@link Parameters} annotation
     * @throws ExecutionException if the Callable throws an exception
     */
    public static <C extends Callable<T>, T> T call(Class<C> cls, ApplicationContext ctx, String... args) {
        return CommandLine.call(cls, new MicronautFactory(ctx), args);
    }

    /**
     * Instantiates a new {@link ApplicationContext} for the {@link Environment#CLI} environment,
     * obtains an instance of the specified {@code Runnable} command class from the context,
     * injecting any beans as required,
     * then parses the specified command line arguments, populating fields and methods annotated
     * with picocli {@link Option @Option} and {@link Parameters @Parameters}
     * annotations, and finally runs the command.
     * <p>
     * The {@code ApplicationContext} is {@linkplain ApplicationContext#close() closed} before this method returns.
     * </p>
     * @param cls the Runnable command class
     * @param args the command line arguments
     * @param <R> The runnable type
     *
     * @throws InitializationException if the specified command object does not have
     *          a {@link Command}, {@link Option} or {@link Parameters} annotation
     * @throws ExecutionException if the Runnable throws an exception
     * @throws Exception if the ApplicationContext could not be closed
     */
    public static <R extends Runnable> void run(Class<R> cls, String... args) throws Exception {
        try (ApplicationContext ctx = ApplicationContext.build(cls, Environment.CLI).build()) {
            run(cls, ctx, args);
        }
    }

    /**
     * Obtains an instance of the specified {@code Runnable} command class from the specified context,
     * injecting any beans from the specified context as required,
     * then parses the specified command line arguments, populating fields and methods annotated
     * with picocli {@link Option @Option} and {@link Parameters @Parameters}
     * annotations, and finally runs the command.
     * <p>
     * The caller is responsible for {@linkplain ApplicationContext#close() closing} the context.
     * </p>
     * @param cls the Runnable command class
     * @param ctx the ApplicationContext that injects dependencies into the command
     * @param args the command line arguments
     * @param <R> The runnable type
     * @throws InitializationException if the specified command object does not have
     *          a {@link Command}, {@link Option} or {@link Parameters} annotation
     * @throws ExecutionException if the Runnable throws an exception
     */
    public static <R extends Runnable> void run(Class<R> cls, ApplicationContext ctx, String... args) {
        CommandLine.run(cls, new MicronautFactory(ctx), args);
    }
}
