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
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.execution.ExecutionFlow;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

// TODO: docs
@Internal
public final class FormRouteCompleter {
    private static final Logger LOG = LoggerFactory.getLogger(FormRouteCompleter.class);

    private final FormCapableHttpRequest<?> request;

    private final AtomicInteger cancelledDownstreamCount = new AtomicInteger();
    private int downstreamCount = 0;
    private boolean started = false;
    private Subscription upstream;

    private Throwable globalError;
    private boolean globalComplete;

    private final Map<String, Unicast> fieldUnicasts = new HashMap<>();

    private Map<String, List<CloseableByteBody>> bufferedForGetBody = new LinkedHashMap<>();
    private Map<String, Object> stringsForGetBody;
    private Throwable exceptionForGetBody;

    FormRouteCompleter(FormCapableHttpRequest<?> request) {
        this.request = request;
    }

    public Publisher<RawFormField> subscribeField(String name) {
        if (started) {
            throw new IllegalStateException("FormRouteCompleter already started");
        }
        Unicast unicast = new Unicast();
        if (fieldUnicasts.putIfAbsent(name, unicast) != null) {
            throw new IllegalStateException("Field already claimed: " + name);
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

    public void start() {
        if (started) {
            throw new IllegalStateException("FormRouteCompleter already started");
        }
        started = true;
        request.getRawFormFields().subscribe(new SubscriberImpl());
    }

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
            return stringsForGetBody;
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
    @NonNull
    public static Map<String, Object> mapForGetBody(@NonNull Map<String, ? extends Iterable<CloseableByteBody>> byteBodies, @NonNull Charset charset) {
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
                upstream.request(1);
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
        private Subscriber<? super RawFormField> subscriber;
        private volatile RawFormField queued = null;
        private final AtomicLong demand = new AtomicLong(0);
        private final AtomicReference<State> state = new AtomicReference<>();
        private boolean endForwarded;
        private volatile boolean cancelled;

        @Override
        public void subscribe(Subscriber<? super RawFormField> s) {
            if (subscriber != null) {
                throw new IllegalStateException("Only one subscriber allowed");
            }
            this.subscriber = s;
            s.onSubscribe(this);
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
            while (queued != null && demand.get() > 0) {
                demand.decrementAndGet();
                if (cancelled) {
                    queued.close();
                } else {
                    subscriber.onNext(queued);
                }
                upstream.request(1);
            }
            if (!endForwarded && queued == null && (globalComplete || globalError != null)) {
                endForwarded = true;
                if (globalError != null) {
                    subscriber.onError(globalError);
                } else {
                    subscriber.onComplete();
                }
            }
        }

        @Override
        public void cancel() {
            if (!cancelled) {
                cancelled = true;
                if (cancelledDownstreamCount.incrementAndGet() == downstreamCount) {
                    upstream.cancel();
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
}
