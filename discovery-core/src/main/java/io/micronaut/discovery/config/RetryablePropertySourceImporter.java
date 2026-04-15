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
package io.micronaut.discovery.config;

import io.micronaut.context.env.PropertySource;
import io.micronaut.context.env.PropertySourceImporter;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.convert.value.ConvertibleValues;
import io.micronaut.core.util.ConnectionString;
import io.micronaut.retry.RetryOperations;
import io.micronaut.retry.RetryOperationsFactory;
import io.micronaut.retry.RetryPolicy;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Abstract {@link PropertySourceImporter} implementation that standardizes retry-aware import declarations.
 *
 * <p>This base class makes it easier for distributed configuration importers to support the same retry settings
 * for both scalar connection-string imports and structured map imports. The final interface methods parse the
 * configured retry options into a typed {@link RetryPolicy} and delegate importer-specific behavior to protected
 * template methods.</p>
 *
 * <p>The following standard retry properties are supported:</p>
 * <ul>
 *     <li>{@code retry-attempts} – maximum number of attempts</li>
 *     <li>{@code retry-count} – alias for {@code retry-attempts}</li>
 *     <li>{@code retry-delay} – delay between attempts</li>
 *     <li>{@code retry-max-delay} – maximum overall retry delay</li>
 *     <li>{@code retry-multiplier} – delay multiplier</li>
 *     <li>{@code retry-jitter} – jitter factor from {@code 0.0} to {@code 1.0}</li>
 * </ul>
 *
 * @param <D> The importer-specific declaration type
 * @since 5.0
 */
@Experimental
public abstract class RetryablePropertySourceImporter<D> implements PropertySourceImporter<RetryablePropertySourceImporter.RetryableImportDeclaration<D>> {

    /**
     * Query or map property name for maximum retry attempts.
     */
    public static final String RETRY_ATTEMPTS = "retry-attempts";

    /**
     * Alias for {@link #RETRY_ATTEMPTS}.
     */
    public static final String RETRY_COUNT = "retry-count";

    /**
     * Query or map property name for the delay between attempts.
     */
    public static final String RETRY_DELAY = "retry-delay";

    /**
     * Query or map property name for the maximum overall retry delay.
     */
    public static final String RETRY_MAX_DELAY = "retry-max-delay";

    /**
     * Query or map property name for the retry multiplier.
     */
    public static final String RETRY_MULTIPLIER = "retry-multiplier";

    /**
     * Query or map property name for retry jitter.
     */
    public static final String RETRY_JITTER = "retry-jitter";

    private final RetryOperationsFactory retryOperationsFactory;
    @Nullable
    private final ScheduledExecutorService executorService;

    /**
     * Creates a retryable importer backed by an internal single-threaded scheduler.
     */
    protected RetryablePropertySourceImporter() {
        this(Executors.newScheduledThreadPool(1));
    }

    /**
     * @param retryOperationsFactory Factory used to create retry operations for import execution
     */
    protected RetryablePropertySourceImporter(RetryOperationsFactory retryOperationsFactory) {
        this(retryOperationsFactory, null);
    }

    private RetryablePropertySourceImporter(ScheduledExecutorService executorService) {
        this(RetryOperationsFactory.create(executorService), executorService);
    }

    private RetryablePropertySourceImporter(RetryOperationsFactory retryOperationsFactory,
                                            @Nullable ScheduledExecutorService executorService) {
        this.retryOperationsFactory = Objects.requireNonNull(retryOperationsFactory, "retryOperationsFactory");
        this.executorService = executorService;
    }

    @Override
    public final RetryableImportDeclaration<D> newImportDeclaration(ConnectionString connectionString) {
        ConvertibleValues<String> options = ConvertibleValues.of(connectionString.getOptions());
        RetryPolicy retryPolicy = resolveRetryPolicy(options);
        return new RetryableImportDeclaration<>(
            newImportDeclaration(connectionString, retryPolicy),
            retryPolicy
        );
    }

    @Override
    public final RetryableImportDeclaration<D> newImportDeclaration(ConvertibleValues<Object> values) {
        RetryPolicy retryPolicy = resolveRetryPolicy(values);
        return new RetryableImportDeclaration<>(
            newImportDeclaration(values, retryPolicy),
            retryPolicy
        );
    }

    @Override
    public final Optional<PropertySource> importPropertySource(ImportContext<RetryableImportDeclaration<D>> context) {
        RetryableImportDeclaration<D> declaration = context.importDeclaration();
        RetryOperations retryOperations = retryOperationsFactory.createRetryOperations(declaration.retryPolicy());
        return retryOperations.execute(() -> importRetryablePropertySource(new DelegatingImportContext<>(context, declaration.declaration())));
    }

    @Override
    public final void close() {
        try {
            closeRetryableImporter();
        } finally {
            if (executorService != null) {
                executorService.shutdown();
            }
        }
    }

    /**
     * Creates the importer-specific declaration from a scalar connection-string import.
     *
     * @param connectionString The parsed connection string
     * @param retryPolicy The resolved retry policy
     * @return The importer-specific declaration
     */
    protected abstract D newImportDeclaration(ConnectionString connectionString, RetryPolicy retryPolicy);

    /**
     * Creates the importer-specific declaration from a structured import map.
     *
     * @param values The structured import values
     * @param retryPolicy The resolved retry policy
     * @return The importer-specific declaration
     */
    protected abstract D newImportDeclaration(ConvertibleValues<Object> values, RetryPolicy retryPolicy);

    /**
     * Imports a property source using the importer-specific declaration.
     *
     * @param context The import context
     * @return The imported property source, if one was resolved
     */
    protected abstract Optional<PropertySource> importRetryablePropertySource(ImportContext<D> context);

    /**
     * Hook invoked from the final {@link #close()} implementation before any internally-created scheduler is shut down.
     *
     * <p>The default implementation is a no-op.</p>
     */
    protected void closeRetryableImporter() {
    }

    /**
     * Resolves retry settings from connection-string options or map values.
     *
     * @param values The import values
     * @return The resolved retry policy
     */
    protected RetryPolicy resolveRetryPolicy(ConvertibleValues<?> values) {
        RetryPolicy.Builder builder = RetryPolicy.builder();
        int maxAttempts = values.get(RETRY_ATTEMPTS, Integer.class)
            .or(() -> values.get(RETRY_COUNT, Integer.class))
            .orElse(RetryPolicy.DEFAULT_MAX_ATTEMPTS);
        builder.maxAttempts(maxAttempts);
        values.get(RETRY_DELAY, Duration.class).ifPresent(builder::delay);
        values.get(RETRY_MAX_DELAY, Duration.class).ifPresent(builder::maxDelay);
        values.get(RETRY_MULTIPLIER, Double.class).ifPresent(builder::multiplier);
        values.get(RETRY_JITTER, Double.class).ifPresent(builder::jitter);
        return builder.build();
    }

    private record DelegatingImportContext<D>(PropertySourceImporter.ImportContext<RetryableImportDeclaration<D>> delegate,
                                              D importDeclaration) implements PropertySourceImporter.ImportContext<D> {
        @Override
        public io.micronaut.context.env.Environment environment() {
            return delegate.environment();
        }

        @Override
        public @Nullable ConnectionString connectionString() {
            return delegate.connectionString();
        }

        @Override
        public D importDeclaration() {
            return importDeclaration;
        }

        @Override
        public PropertySource.@Nullable Origin parentOrigin() {
            return delegate.parentOrigin();
        }

        @Override
        public Optional<PropertySource> importPropertySource(io.micronaut.core.io.ResourceLoader resourceLoader,
                                                             String resourcePath,
                                                             String sourceName,
                                                             PropertySource.Origin origin) {
            return delegate.importPropertySource(resourceLoader, resourcePath, sourceName, origin);
        }

        @Override
        public Optional<PropertySource> importPropertySource(String content,
                                                             String sourceName,
                                                             String extension,
                                                             PropertySource.Origin origin) {
            return delegate.importPropertySource(content, sourceName, extension, origin);
        }

        @Override
        public Optional<PropertySource> importClasspathPropertySource(String resourcePath,
                                                                      String sourceName,
                                                                      PropertySource.Origin origin,
                                                                      boolean allowMultiple) {
            return delegate.importClasspathPropertySource(resourcePath, sourceName, origin, allowMultiple);
        }
    }

    /**
     * Retry-aware wrapper around an importer-specific declaration.
     *
     * @param declaration The importer-specific declaration
     * @param retryPolicy The resolved retry policy
     * @param <D> The importer-specific declaration type
     */
    public record RetryableImportDeclaration<D>(D declaration, RetryPolicy retryPolicy) {

        /**
         * Creates a retry-aware import declaration.
         *
         * @param declaration The importer-specific declaration
         * @param retryPolicy The retry policy used for import execution
         */
        public RetryableImportDeclaration {
            Objects.requireNonNull(declaration, "declaration");
            Objects.requireNonNull(retryPolicy, "retryPolicy");
        }
    }
}
