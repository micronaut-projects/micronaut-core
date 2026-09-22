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
package io.micronaut.http.server.binding;

import io.micronaut.core.annotation.Internal;
import io.micronaut.http.form.FormCapableHttpRequest;
import io.micronaut.http.form.FormPart;
import io.micronaut.http.form.FormParts;
import io.micronaut.http.multipart.RawFormField;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

/**
 * The {@link FormParts} of a request: a cursor over its raw form fields, which requests one
 * field at a time from the form decoder, so nothing is read ahead of the handler. The next field
 * is only requested once the consumer of the previous one is done and its part was released.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
final class DefaultFormParts implements FormParts, Subscriber<RawFormField> {

    private final FormCapableHttpRequest<?> request;
    private final UploadContext context;

    // guarded by this
    private @Nullable Subscription subscription;
    private @Nullable CompletableFuture<@Nullable RawFormField> pending;
    private boolean subscribed;
    private boolean ended;
    private @Nullable Throwable failure;
    private boolean busy;
    private @Nullable DefaultFormPart active;
    private @Nullable Walk<?> walking;
    private @Nullable CompletionStage<Void> closed;

    DefaultFormParts(FormCapableHttpRequest<?> request, UploadContext context) {
        this.request = request;
        this.context = context;
    }

    @Override
    public CompletionStage<Void> forEach(Function<? super FormPart, ? extends CompletionStage<?>> consumer) {
        Objects.requireNonNull(consumer, "consumer");
        return walk(part -> Objects.requireNonNull(consumer.apply(part), "The consumer returned no stage"), false, null, null);
    }

    @Override
    public CompletionStage<Boolean> part(String name, Function<? super FormPart, ? extends CompletionStage<?>> consumer) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(consumer, "consumer");
        return walk(part -> name.equals(part.name())
            ? Objects.requireNonNull(consumer.apply(part), "The consumer returned no stage")
            : null, true, Boolean.FALSE, Boolean.TRUE);
    }

    @Override
    public void close() {
        closeAsync();
    }

    @Override
    public CompletionStage<Void> closeAsync() {
        Subscription s;
        CompletableFuture<@Nullable RawFormField> p;
        DefaultFormPart part;
        Walk<?> operation;
        CompletableFuture<Void> result = new CompletableFuture<>();
        synchronized (this) {
            if (closed != null) {
                return closed;
            }
            closed = result.minimalCompletionStage();
            s = subscription;
            p = pending;
            pending = null;
            part = active;
            active = null;
            operation = walking;
        }
        if (operation != null) {
            // the operation in progress ends now, even if its consumer never completes
            operation.result.completeExceptionally(closedException());
        }
        if (s != null) {
            // the form decoder discards the rest of the body
            s.cancel();
        }
        if (p != null) {
            p.completeExceptionally(closedException());
        }
        if (part == null) {
            result.complete(null);
        } else {
            // the consumer of the part loses it: what it did not consume is discarded
            part.closeAsync().whenComplete((ignored, error) -> {
                if (error != null) {
                    result.completeExceptionally(error);
                } else {
                    result.complete(null);
                }
            });
        }
        synchronized (this) {
            return Objects.requireNonNull(closed);
        }
    }

    private static CancellationException closedException() {
        return new CancellationException("The form parts were closed");
    }

    private <T> CompletionStage<T> walk(Function<DefaultFormPart, @Nullable CompletionStage<?>> visitor, boolean once, @Nullable T ended, @Nullable T visited) {
        synchronized (this) {
            if (closed != null) {
                return CompletableFuture.failedFuture(new IllegalStateException("The form parts were closed"));
            }
            if (busy) {
                return CompletableFuture.failedFuture(new IllegalStateException("Another operation on the form parts is in progress"));
            }
            busy = true;
        }
        Walk<T> walk = new Walk<>(visitor, once, ended, visited);
        synchronized (this) {
            walking = walk;
        }
        walk.run();
        return walk.result;
    }

    /**
     * @return The next field, {@code null} at the end of the form
     */
    private CompletableFuture<@Nullable RawFormField> next() {
        CompletableFuture<@Nullable RawFormField> future = new CompletableFuture<>();
        Subscription s;
        boolean subscribe = false;
        synchronized (this) {
            if (failure != null) {
                return CompletableFuture.failedFuture(failure);
            }
            if (closed != null) {
                return CompletableFuture.failedFuture(closedException());
            }
            if (ended) {
                return CompletableFuture.completedFuture(null);
            }
            pending = future;
            s = subscription;
            if (!subscribed) {
                subscribed = true;
                subscribe = true;
            }
        }
        if (subscribe) {
            request.getRawFormFields().subscribe(this);
        } else if (s != null) {
            s.request(1);
        }
        return future;
    }

    @Override
    public void onSubscribe(Subscription s) {
        boolean cancel;
        boolean demand;
        synchronized (this) {
            subscription = s;
            cancel = closed != null;
            demand = pending != null;
        }
        if (cancel) {
            s.cancel();
        } else if (demand) {
            s.request(1);
        }
    }

    @Override
    public void onNext(RawFormField field) {
        CompletableFuture<@Nullable RawFormField> p;
        synchronized (this) {
            p = closed != null ? null : pending;
            pending = null;
        }
        if (p == null) {
            field.close();
        } else {
            p.complete(field);
        }
    }

    @Override
    public void onError(Throwable t) {
        CompletableFuture<@Nullable RawFormField> p;
        synchronized (this) {
            failure = t;
            p = pending;
            pending = null;
        }
        if (p != null) {
            p.completeExceptionally(t);
        }
    }

    @Override
    public void onComplete() {
        CompletableFuture<@Nullable RawFormField> p;
        synchronized (this) {
            ended = true;
            p = pending;
            pending = null;
        }
        if (p != null) {
            p.complete(null);
        }
    }

    /**
     * Hand a part to the consumer of the current operation.
     *
     * @param part The part
     * @return {@code false} if the parts were closed, and the part was released
     */
    private boolean own(DefaultFormPart part) {
        synchronized (this) {
            if (closed == null) {
                active = part;
                return true;
            }
        }
        part.close();
        return false;
    }

    /**
     * End the ownership of the consumer of a part, when its stage completes or fails.
     *
     * @param part The part
     * @return Completes when what the consumer did not consume is released
     */
    private CompletableFuture<Void> release(DefaultFormPart part) {
        synchronized (this) {
            if (active == part) {
                active = null;
            }
        }
        try {
            return part.closeAsync().toCompletableFuture();
        } catch (Throwable e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    private static Throwable unwrap(Throwable error) {
        return error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
    }

    /**
     * One operation: reads fields until the visitor consumed one (when {@code once}) or the form
     * ended. Stages that are already complete are handled in a loop, not by recursion.
     *
     * @param <T> The result
     */
    private final class Walk<T> {
        private final Function<DefaultFormPart, @Nullable CompletionStage<?>> visitor;
        private final boolean once;
        private final @Nullable T endedValue;
        private final @Nullable T visitedValue;
        private final CompletableFuture<T> result = new CompletableFuture<>();

        Walk(Function<DefaultFormPart, @Nullable CompletionStage<?>> visitor, boolean once, @Nullable T endedValue, @Nullable T visitedValue) {
            this.visitor = visitor;
            this.once = once;
            this.endedValue = endedValue;
            this.visitedValue = visitedValue;
        }

        void run() {
            while (true) {
                CompletableFuture<@Nullable RawFormField> next = next();
                if (!next.isDone()) {
                    next.whenComplete((field, error) -> {
                        if (accept(field, error)) {
                            run();
                        }
                    });
                    return;
                }
                RawFormField field;
                try {
                    field = next.join();
                } catch (CompletionException | CancellationException e) {
                    accept(null, e);
                    return;
                }
                if (!accept(field, null)) {
                    return;
                }
            }
        }

        /**
         * @return Whether to read the next field now
         */
        private boolean accept(@Nullable RawFormField field, @Nullable Throwable error) {
            if (error != null) {
                finish(null, error);
                return false;
            }
            if (field == null) {
                finish(endedValue, null);
                return false;
            }
            DefaultFormPart part = new DefaultFormPart(new StreamingUploadContent(field, context));
            if (!own(part)) {
                // closed meanwhile
                finish(null, closedException());
                return false;
            }
            CompletableFuture<?> stage;
            try {
                CompletionStage<?> visited = visitor.apply(part);
                if (visited == null) {
                    // skipped: discarded before the next part is read
                    return afterRelease(release(part), null, false);
                }
                stage = visited.toCompletableFuture();
            } catch (Throwable e) {
                return afterRelease(release(part), e, false);
            }
            if (!stage.isDone()) {
                stage.whenComplete((ignored, e) -> {
                    if (afterRelease(release(part), e, once)) {
                        run();
                    }
                });
                return false;
            }
            Throwable e = null;
            try {
                stage.join();
            } catch (Throwable t) {
                e = t;
            }
            return afterRelease(release(part), e, once);
        }

        /**
         * Continue once the part was released: with the next field, or by finishing.
         *
         * @param released Completes when the part was released
         * @param error    The failure of the consumer
         * @param visited  Whether the part was the one the operation was looking for
         * @return Whether to read the next field now
         */
        private boolean afterRelease(CompletableFuture<Void> released, @Nullable Throwable error, boolean visited) {
            if (!released.isDone()) {
                released.whenComplete((ignored, releaseError) -> {
                    if (proceed(error, releaseError, visited)) {
                        run();
                    }
                });
                return false;
            }
            Throwable releaseError = null;
            try {
                released.join();
            } catch (Throwable t) {
                releaseError = t;
            }
            return proceed(error, releaseError, visited);
        }

        private boolean proceed(@Nullable Throwable error, @Nullable Throwable releaseError, boolean visited) {
            if (error != null) {
                Throwable cause = unwrap(error);
                if (releaseError != null && unwrap(releaseError) != cause) {
                    cause.addSuppressed(unwrap(releaseError));
                }
                finish(null, cause);
                return false;
            }
            if (releaseError != null) {
                finish(null, releaseError);
                return false;
            }
            synchronized (DefaultFormParts.this) {
                if (closed != null) {
                    finish(null, closedException());
                    return false;
                }
            }
            if (visited) {
                finish(visitedValue, null);
                return false;
            }
            return true;
        }

        private void finish(@Nullable T value, @Nullable Throwable error) {
            synchronized (DefaultFormParts.this) {
                busy = false;
                if (walking == this) {
                    walking = null;
                }
            }
            if (error != null) {
                result.completeExceptionally(unwrap(error));
            } else {
                result.complete(value);
            }
        }
    }
}
