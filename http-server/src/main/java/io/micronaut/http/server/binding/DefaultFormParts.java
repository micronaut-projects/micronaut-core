/*
 * Copyright 2017-2025 original authors
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
import io.micronaut.http.multipart.RawFormField;
import io.micronaut.http.server.multipart.FormFactory;
import io.micronaut.web.router.builder.FormPart;
import io.micronaut.web.router.builder.FormParts;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

/**
 * The {@link FormParts} of a request: a cursor over its raw form fields, which requests one
 * field at a time from the form decoder, so nothing is read ahead of the handler.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
final class DefaultFormParts implements FormParts, Subscriber<RawFormField> {

    private final FormCapableHttpRequest<?> request;
    private final FormFactory formFactory;

    // guarded by this
    private @Nullable Subscription subscription;
    private @Nullable CompletableFuture<@Nullable RawFormField> pending;
    private boolean subscribed;
    private boolean ended;
    private @Nullable Throwable failure;
    private boolean closed;
    private boolean busy;

    DefaultFormParts(FormCapableHttpRequest<?> request, FormFactory formFactory) {
        this.request = request;
        this.formFactory = formFactory;
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
        Subscription s;
        CompletableFuture<@Nullable RawFormField> p;
        synchronized (this) {
            if (closed) {
                return;
            }
            closed = true;
            s = subscription;
            p = pending;
            pending = null;
        }
        if (s != null) {
            // the form decoder discards the rest of the body
            s.cancel();
        }
        if (p != null) {
            p.complete(null);
        }
    }

    private <T> CompletionStage<T> walk(Function<DefaultFormPart, @Nullable CompletionStage<?>> visitor, boolean once, @Nullable T ended, @Nullable T visited) {
        synchronized (this) {
            if (busy) {
                return CompletableFuture.failedFuture(new IllegalStateException("Another operation on the form parts is in progress"));
            }
            busy = true;
        }
        Walk<T> walk = new Walk<>(visitor, once, ended, visited);
        walk.run();
        return walk.result;
    }

    /**
     * @return The next field, {@code null} at the end of the form or when closed
     */
    private CompletableFuture<@Nullable RawFormField> next() {
        CompletableFuture<@Nullable RawFormField> future = new CompletableFuture<>();
        Subscription s;
        boolean subscribe = false;
        synchronized (this) {
            if (failure != null) {
                return CompletableFuture.failedFuture(failure);
            }
            if (ended || closed) {
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
            cancel = closed;
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
            p = closed ? null : pending;
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
                } catch (CompletionException e) {
                    accept(null, e);
                    return;
                }
                if (!accept(field, null)) {
                    return;
                }
            }
        }

        /**
         * @return Whether to read the next field
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
            DefaultFormPart part = new DefaultFormPart(field, formFactory, request.getCharacterEncoding());
            CompletableFuture<?> stage;
            try {
                CompletionStage<?> visited = visitor.apply(part);
                if (visited == null) {
                    part.discardIfUnread();
                    return true;
                }
                stage = visited.toCompletableFuture();
            } catch (Throwable e) {
                part.discardIfUnread();
                finish(null, e);
                return false;
            }
            if (!stage.isDone()) {
                stage.whenComplete((ignored, e) -> {
                    part.discardIfUnread();
                    if (e != null) {
                        finish(null, e);
                    } else if (once) {
                        finish(visitedValue, null);
                    } else {
                        run();
                    }
                });
                return false;
            }
            part.discardIfUnread();
            try {
                stage.join();
            } catch (Throwable e) {
                finish(null, e);
                return false;
            }
            if (once) {
                finish(visitedValue, null);
                return false;
            }
            return true;
        }

        private void finish(@Nullable T value, @Nullable Throwable error) {
            synchronized (DefaultFormParts.this) {
                busy = false;
            }
            if (error != null) {
                result.completeExceptionally(error instanceof CompletionException && error.getCause() != null ? error.getCause() : error);
            } else {
                result.complete(value);
            }
        }
    }
}
