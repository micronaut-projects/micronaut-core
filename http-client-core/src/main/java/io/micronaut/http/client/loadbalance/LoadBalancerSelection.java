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

import java.util.concurrent.atomic.AtomicBoolean;

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
    private volatile boolean claimed;

    /**
     * @param loadBalancer The load balancer that selected the instance
     * @param instance     The selected instance
     */
    public LoadBalancerSelection(LoadBalancer loadBalancer, ServiceInstance instance) {
        this.loadBalancer = loadBalancer;
        this.instance = instance;
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
            loadBalancer.report(instance, outcome);
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
