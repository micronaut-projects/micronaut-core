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
package io.micronaut.http.client.loadbalance;

import io.micronaut.core.annotation.Internal;
import io.micronaut.discovery.ServiceInstance;
import io.micronaut.http.client.LoadBalancer;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * One selection of an instance by a load balancer, for the exchange sent to it: its outcome is
 * reported to the load balancer exactly once, the first outcome wins. An exchange that ends
 * without an outcome is {@link #release() released} with {@link LoadBalancer.Outcome#CANCELLED}.
 *
 * <p>Until the client {@link #claim() claims} the selection, for an exchange whose response
 * handling reports the outcome, the client releases it whenever the exchange ends; once claimed,
 * the response handling must report or release it.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
public final class LoadBalancerSelection {

    private final LoadBalancer loadBalancer;
    private final ServiceInstance instance;
    private final AtomicBoolean reported = new AtomicBoolean();
    private final @Nullable Runnable onEnd;
    private final AtomicBoolean ended = new AtomicBoolean();
    /**
     * This exchange, and each later exchange it lent its end to, see {@link #retainEnd()}: the
     * end action runs once they all ended.
     */
    private final AtomicInteger holders = new AtomicInteger(1);
    private volatile boolean claimed;
    private volatile boolean endHeld;

    /**
     * @param loadBalancer The load balancer that selected the instance
     * @param instance     The selected instance
     */
    public LoadBalancerSelection(LoadBalancer loadBalancer, ServiceInstance instance) {
        this(loadBalancer, instance, null);
    }

    /**
     * @param loadBalancer The load balancer that selected the instance
     * @param instance     The selected instance
     * @param onEnd        Run once when the exchange ends, after its outcome was reported, e.g.
     *                     to give back a permit of the concurrency limit, or {@code null}
     * @since 5.3.0
     */
    public LoadBalancerSelection(LoadBalancer loadBalancer, ServiceInstance instance, @Nullable Runnable onEnd) {
        this.loadBalancer = loadBalancer;
        this.instance = instance;
        this.onEnd = onEnd;
    }

    /**
     * @return The selected instance
     */
    public ServiceInstance instance() {
        return instance;
    }

    /**
     * Report the outcome of the exchange, unless one was reported already.
     *
     * @param outcome The outcome
     */
    public void report(LoadBalancer.Outcome outcome) {
        if (reported.compareAndSet(false, true)) {
            try {
                loadBalancer.report(instance, outcome);
            } finally {
                if (!endHeld) {
                    end();
                }
            }
        }
    }

    /**
     * The outcome is reported before the exchange ends, e.g. when the caller takes over a body
     * that is still streaming: the transport calls {@link #end()} once the body ended. Called
     * before the outcome is reported.
     *
     * @since 5.3.0
     */
    public void holdEnd() {
        endHeld = true;
    }

    /**
     * The exchange ended: its response body ended or failed, or it was cancelled. Only the first
     * call has an effect. Implied by reporting the outcome, unless {@link #holdEnd()} was called.
     *
     * @since 5.3.0
     */
    public void end() {
        if (onEnd != null && ended.compareAndSet(false, true)) {
            dropHolder();
        }
    }

    /**
     * Keep the end action of this exchange for a later exchange that continues it, e.g. the
     * request that follows a redirect: the action runs once this exchange and the later one both
     * ended. Called before this exchange ends.
     *
     * @return The end of the later exchange, which only its first run counts, or {@code null} if
     * this selection has no end action or its exchange already ended
     * @since 5.3.0
     */
    public @Nullable Runnable retainEnd() {
        if (onEnd == null) {
            return null;
        }
        int current;
        do {
            current = holders.get();
            if (current == 0) {
                return null;
            }
        } while (!holders.compareAndSet(current, current + 1));
        AtomicBoolean done = new AtomicBoolean();
        return () -> {
            if (done.compareAndSet(false, true)) {
                dropHolder();
            }
        };
    }

    private void dropHolder() {
        if (holders.decrementAndGet() == 0 && onEnd != null) {
            onEnd.run();
        }
    }

    /**
     * End the exchange without an outcome, unless one was reported already.
     */
    public void release() {
        report(LoadBalancer.Outcome.CANCELLED);
    }

    /**
     * The response handling of the exchange takes over: it reports or releases the selection.
     */
    public void claim() {
        claimed = true;
    }

    /**
     * {@link #release() Release} the selection unless the response handling
     * {@link #claim() claimed} it.
     */
    public void releaseUnclaimed() {
        if (!claimed) {
            release();
        }
    }
}
