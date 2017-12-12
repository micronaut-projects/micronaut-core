/*
 * Copyright 2017 original authors
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
package org.particleframework.management.endpoint.health.indicator;

import org.particleframework.management.endpoint.health.HealthResult;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

/**
 * <p>Base class to extend from when subscribing to {@link HealthIndicator#getResult()}.</p>
 *
 * @author James Kleeh
 * @since 1.0
 */
abstract public class HealthIndicatorSubscriber implements Subscriber<HealthResult> {

    protected HealthIndicator indicator;

    public HealthIndicatorSubscriber(HealthIndicator indicator) {
        this.indicator = indicator;
    }

    @Override
    public void onSubscribe(Subscription s) {
        s.request(1);
    }

    @Override
    abstract public void onNext(HealthResult o);

    @Override
    public void onError(Throwable t) {
        throw new UnsupportedOperationException("Health indicators should never throw errors", t);
    }

    @Override
    public void onComplete() {
        //NO-OP
    }
}
