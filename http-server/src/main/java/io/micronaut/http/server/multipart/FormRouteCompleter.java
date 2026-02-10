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
package io.micronaut.http.server.multipart;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.bind.exceptions.UnsatisfiedArgumentException;
import io.micronaut.core.execution.ExecutionFlow;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.body.AvailableByteBody;
import io.micronaut.http.body.CloseableAvailableByteBody;
import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.http.body.InternalByteBody;
import io.micronaut.http.form.FormCapableHttpRequest;
import io.micronaut.http.multipart.RawFormField;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * This class distributes fields from {@link FormCapableHttpRequest#getRawFormFields()} to
 * different argument binders by name.
 *
 * @since 5.0.0
 * @author Jonas Konrad
 */
@Internal
public final class FormRouteCompleter {
    private static final Logger LOG = LoggerFactory.getLogger(FormRouteCompleter.class);

    private final FormCapableHttpRequest<?> request;

    private final AtomicInteger cancelledDownstreamCount = new AtomicInteger();
    private int downstreamCount = 0;
    private boolean started = false;
    @Nullable
    private Subscription upstream;

    @Nullable
    private Throwable globalError;
    private boolean globalComplete;

    private final Map<String, Unicast> fieldUnicasts = new HashMap<>();

    @Nullable
    private Map<String, List<CloseableByteBody>> bufferedForGetBody = new LinkedHashMap<>();
    @Nullable
    private Map<String, Object> stringsForGetBody;
    @Nullable
    private Throwable exceptionForGetBody;

    private volatile boolean deadlockDetection = true;

    FormRouteCompleter(FormCapableHttpRequest<?> request) {
        this.request = request;
    }

    /**
     * Subscribe to a field of a particular name.
     *
     * @param name The field name
     * @param metadata The subscription metadata, used for detecting and reporting deadlocks
     * @return The publisher
     */
    public Publisher<RawFormField> subscribeField(String name, SubscriptionMetadata metadata) {
        if (started) {
            throw new IllegalStateException("FormRouteCompleter already started");
        }
        Unicast unicast = new Unicast(metadata);
        Unicast existing = fieldUnicasts.putIfAbsent(name, unicast);
        if (existing != null) {
            throw new IllegalStateException("Field '" + name + "' is claimed by multiple parameters:\n  [" + existing.metadata.argument + "]\n  [" + metadata.argument + "]");
        }
        downstreamCount++;
        return unicast;
    }

    public boolean isClaimed(String inputName) {
        if (started) {
            throw new IllegalStateException("FormRouteCompleter already started");
        }
        return fieldUnicasts.containsKey(inputName);
    }

    /**
     * Start reading the form data. After this method is called, no more fields may be subscribed
     * to.
     */
    public void start() {
        if (started) {
            throw new IllegalStateException("FormRouteCompleter already started");
        }
        started = true;
        request.getRawFormFields().subscribe(new SubscriberImpl());
    }

    /**
     * Stop deadlock detection. This is called immediately before the controller is called. After
     * this, it's up to the user to properly relieve backpressure on the parameters we've passed to
     * her.
     */
    public void stopDeadlockDetection() {
        deadlockDetection = false;
    }

    /**
     * Convert the full form data into a {@link Map} for {@link HttpRequest#getBody()}.
     *
     * @param charset The charset
     * @return The map
     */
    public Map<String, Object> mapForGetBody(Charset charset) {
        if (bufferedForGetBody != null) {
            Map<String, List<CloseableByteBody>> b = bufferedForGetBody;
            bufferedForGetBody = null;
            try {
                stringsForGetBody = mapForGetBody(b, charset);
            } catch (Throwable e) {
                exceptionForGetBody = e;
            }
        }
        if (exceptionForGetBody != null) {
            return sneakyThrow(exceptionForGetBody);
        } else {
            return Objects.requireNonNull(stringsForGetBody);
        }
    }

    /**
     * Transform a map of {@link CloseableByteBody} into a map of strings (and list of strings if
     * necessary).
     * <p>
     * Ownership of the bodies transfers to this method immediately. If this method throws an
     * exception, all bodies are still closed.
     *
     * @param byteBodies The bodies to transform
     * @param charset    The charset to use for decoding
     * @return The transformed bodies
     * @throws IllegalStateException If any of the input bodies is not yet fully available
     */
    public static Map<String, Object> mapForGetBody(Map<String, ? extends Iterable<CloseableByteBody>> byteBodies, Charset charset) {
        Map<String, Object> map = new LinkedHashMap<>();
        Throwable error = null;
        for (Map.Entry<String, ? extends Iterable<CloseableByteBody>> entry : byteBodies.entrySet()) {
            for (CloseableByteBody value : entry.getValue()) {
                if (error != null) {
                    try {
                        value.close();
                    } catch (Throwable t) {
                        error.addSuppressed(t);
                    }
                } else {
                    try {
                        ExecutionFlow<? extends CloseableAvailableByteBody> availableFlow = InternalByteBody.bufferFlow(value);
                        CloseableAvailableByteBody immediate = availableFlow.tryCompleteValue();
                        if (immediate == null) {
                            Throwable e = availableFlow.tryCompleteError();
                            if (e != null) {
                                throw e;
                            } else {
                                availableFlow.onComplete((b, t) -> {
                                    if (b != null) {
                                        b.close();
                                    }
                                });
                                throw new IllegalStateException("Form field has not yet been fully received");
                            }
                        }
                        String text = immediate.toString(charset);

                        Object existing = map.get(entry.getKey());
                        if (existing == null) {
                            map.put(entry.getKey(), text);
                        } else if (existing instanceof List<?>) {
                            //noinspection unchecked
                            ((List<? super String>) existing).add(text);
                        } else {
                            List<String> list = new ArrayList<>();
                            list.add((String) existing);
                            list.add(text);
                            map.put(entry.getKey(), list);
                        }
                    } catch (Throwable t) {
                        error = t;
                    }
                }
            }
        }
        if (error != null) {
            return sneakyThrow(error);
        }
        return map;
    }

    @SuppressWarnings("unchecked")
    private static <T extends Throwable, R> R sneakyThrow(Throwable t) throws T {
        throw (T) t;
    }

    private final class SubscriberImpl implements Subscriber<RawFormField> {
        @Override
        public void onSubscribe(Subscription s) {
            upstream = s;
            s.request(1);
        }

        @Override
        public void onNext(RawFormField rawFormField) {
            Unicast unicast = fieldUnicasts.get(rawFormField.metadata().name());
            if (unicast == null) {
                rawFormField.close();
                Objects.requireNonNull(upstream).request(1);
            } else {
                unicast.emit(rawFormField);
            }
        }

        @Override
        public void onError(Throwable t) {
            globalError = t;
            fieldUnicasts.values().forEach(Unicast::markDirty);
        }

        @Override
        public void onComplete() {
            globalComplete = true;
            fieldUnicasts.values().forEach(Unicast::markDirty);
        }
    }

    private final class Unicast implements Publisher<RawFormField>, Subscription {
        final SubscriptionMetadata metadata;

        @Nullable
        private Subscriber<? super RawFormField> subscriber;
        @Nullable
        private volatile RawFormField queued = null;
        private final AtomicLong demand = new AtomicLong(0);
        private final AtomicReference<State> state = new AtomicReference<>(State.CLEAN);
        private boolean endForwarded;
        private volatile boolean cancelled;
        @Nullable
        private Throwable ownError;

        Unicast(SubscriptionMetadata metadata) {
            this.metadata = metadata;
        }

        @Override
        public void subscribe(Subscriber<? super RawFormField> s) {
            if (subscriber != null) {
                throw new IllegalStateException("Only one subscriber allowed");
            }
            // onSubscribe first to make sure that no other onX method is called before we're done
            s.onSubscribe(this);
            this.subscriber = s;
            markDirty();
        }

        @Nullable
        private Exception predictDeadlock(RawFormField field) {
            if (!deadlockDetection) {
                return null;
            }

            if (metadata.mode == SubscriptionMode.WAITS_FOR_FULL || metadata.mode == SubscriptionMode.ASYNC_NO_BACKPRESSURE) {
                // the subscriber can consume data before the route executes, so we're fine.
                return null;
            } else if (metadata.mode == SubscriptionMode.WAITS_FOR_START && field.byteBody() instanceof AvailableByteBody) {
                // the subscriber will consume the available data, and we can move on to the next field immediately.
                return null;
            }

            // the subscriber won't read the input data until the route executes. let's check if
            // any other subscriber is preventing that.

            List<SubscriptionMetadata> blocked = null;
            for (Unicast unicast : fieldUnicasts.values()) {
                if (unicast.cancelled ||
                    unicast.metadata.mode == SubscriptionMode.ASYNC ||
                    unicast.metadata.mode == SubscriptionMode.ASYNC_NO_BACKPRESSURE ||
                    unicast == this) {
                    continue;
                }
                if (blocked == null) {
                    blocked = new ArrayList<>();
                }
                blocked.add(unicast.metadata);
            }
            if (blocked == null) {
                return null;
            } else {
                return new FormBindingDeadlockException(metadata, blocked);
            }
        }

        void emit(RawFormField field) {
            assert queued == null;
            queued = field;
            markDirty();
        }

        @Override
        public void request(long n) {
            demand.updateAndGet(old -> {
                long next = old + n;
                if (next < 0) {
                    next = Long.MAX_VALUE;
                }
                return next;
            });
            markDirty();
        }

        void markDirty() {
            State s = state.getAndUpdate(old -> switch (old) {
                case CLEAN -> State.WORKING_CLEAN;
                case WORKING_CLEAN, WORKING_DIRTY -> State.WORKING_DIRTY;
            });
            if (s == State.CLEAN) {
                // we're the first thread to call markDirty. Do some work!
                do {
                    try {
                        work();
                    } catch (Exception e) {
                        LOG.error("Failed to process form data", e);
                    } catch (Throwable t) {
                        // enter a safe state and rethrow
                        state.set(State.CLEAN);
                        throw t;
                    }
                } while (state.getAndUpdate(old -> switch (old) {
                    // shouldn't happen. we're the only thread that can
                    case CLEAN -> throw new AssertionError("Can't be in clean state");
                    // state field is unchanged from when we set it to WORKING_CLEAN before the
                    // work() call. We can safely exit.
                    case WORKING_CLEAN -> State.CLEAN;
                    // Another thread changed the state from WORKING_CLEAN to WORKING_DIRTY. We
                    // need to run work() again.
                    case WORKING_DIRTY -> State.WORKING_CLEAN;
                }) == State.WORKING_DIRTY);
            }
        }

        private void work() {
            Subscriber<? super RawFormField> s = subscriber;
            while (queued != null) {
                Exception deadlock = predictDeadlock(Objects.requireNonNull(queued));
                if (deadlock != null) {
                    // we detected a deadlock. cancel this subscription and forward the error
                    cancelled = true;
                    ownError = deadlock;
                    demand.set(Long.MAX_VALUE);
                    // fall through to cancelled branch to consume the item
                }
                if (demand.get() <= 0) {
                    break;
                }
                if (cancelled) {
                    Objects.requireNonNull(queued).close();
                    queued = null;
                    Objects.requireNonNull(upstream).request(1);
                    break;
                }
                if (s == null) {
                    // we're still in onSubscribe, wait
                    break;
                }

                demand.decrementAndGet();
                s.onNext(queued);
                queued = null;
                Objects.requireNonNull(upstream).request(1);
            }
            if (!endForwarded && queued == null && (globalComplete || globalError != null || ownError != null) && s != null) {
                endForwarded = true;
                if (globalError != null) {
                    s.onError(globalError);
                } else if (ownError != null) {
                    s.onError(ownError);
                } else {
                    s.onComplete();
                }
            }
        }

        @Override
        public void cancel() {
            if (!cancelled) {
                cancelled = true;
                if (cancelledDownstreamCount.incrementAndGet() == downstreamCount) {
                    Objects.requireNonNull(upstream).cancel();
                }
            }
            demand.set(Long.MAX_VALUE);
            markDirty();
        }

        private enum State {
            CLEAN,
            WORKING_CLEAN,
            WORKING_DIRTY,
        }
    }

    public record SubscriptionMetadata(
        SubscriptionMode mode,
        Argument<?> argument
    ) {
    }

    public enum SubscriptionMode {
        /**
         * Before the route can execute, these form fields must have been fully received, including
         * all their data. Note that if the caller cancels their subscription at some point, that
         * means it doesn't stand in the way of executing the route anymore.
         */
        WAITS_FOR_FULL,
        /**
         * Before the route can execute, the headers of the first form field of this name must be
         * received, but the field body will not be consumed until the route actually executes.
         */
        WAITS_FOR_START,
        /**
         * This parameter does not need to be waited for the route to execute, but at the same
         * time, the parameter will not be consumed until the route does execute.
         */
        ASYNC,
        /**
         * This parameter does not need to be waited for the route to execute, and it applies no
         * backpressure, meaning it won't block other fields.
         */
        ASYNC_NO_BACKPRESSURE,
    }

    public static final class FormBindingDeadlockException extends UnsatisfiedArgumentException {
        public FormBindingDeadlockException(SubscriptionMetadata blockingMetadata, List<SubscriptionMetadata> blockedMetadata) {
            super(blockingMetadata.argument, makeMessage(blockedMetadata));
        }

        private static String makeMessage(List<SubscriptionMetadata> blockedMetadata) {
            StringBuilder sb = new StringBuilder();
            sb.append("This argument won't consume posted data until you subscribe to it in the controller. This prevents the following arguments from being bound:");
            for (SubscriptionMetadata blocked : blockedMetadata) {
                sb.append("\n  [").append(blocked.argument).append("] has not yet been received.");
            }
            return sb.toString();
        }
    }
}
