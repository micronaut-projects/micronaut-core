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
package org.particleframework.aop;

/**
 * <p>{@link Interceptor} classes implement the {@link org.particleframework.core.order.Ordered} interface
 * in order to control the order of execution when multiple interceptors are present.</p>
 *
 * <p>This class provides a set of common constants for typical phases used by interceptors thus making it easier to position a n interceptor in the correct phase.</p>
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public enum InterceptPhase {
    /**
     * Validation phase of execution
     */
    VALIDATE(-120),
    /**
     * Caching phase of execution
     */
    CACHE(-80),
    /**
     * Retry phase of execution
     */
    RETRY(-60),
    /**
     * Transaction phase of execution
     */
    TRANSACTION(-20);

    private final int position;

    InterceptPhase(int position) {
        this.position = position;
    }

    /**
     * @return The position
     */
    public int getPosition() {
        return position;
    }
}
