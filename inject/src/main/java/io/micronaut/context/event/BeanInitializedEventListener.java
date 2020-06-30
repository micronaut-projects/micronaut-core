/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.context.event;

import io.micronaut.core.annotation.Indexed;

import java.util.EventListener;

/**
 * <p>Allows hooking into bean instantiation at the point prior to when {@link javax.annotation.PostConstruct}
 * initialization hooks have been called and in the case of bean {@link javax.inject.Provider} instances the
 * {@link javax.inject.Provider#get()} method has not yet been invoked.</p>
 * <p>
 * <p>This allows (for example) customization of bean properties prior to any initialization logic or factory logic.</p>
 *
 * @param <T> The event type
 * @author Graeme Rocher
 * @since 1.0
 */
@Indexed(BeanInitializedEventListener.class)
public interface BeanInitializedEventListener<T> extends EventListener {

    /**
     * <p>Fired when a bean is instantiated but the {@link javax.annotation.PostConstruct} initialization hooks have not
     * yet been called and in this case of bean {@link javax.inject.Provider} instances the
     * {@link javax.inject.Provider#get()} method has not yet been invoked.</p>
     *
     * @param event The bean initializing event
     * @return The bean or a replacement bean of the same type
     */
    T onInitialized(BeanInitializingEvent<T> event);
}
