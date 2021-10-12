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
package io.micronaut.context;

/**
 * An interface for classes that manage the {@link ApplicationContext} life cycle and shut it down when the class is shutdown.
 *
 * @param <T> The concrete type
 */
public interface ApplicationContextLifeCycle<T extends ApplicationContextLifeCycle> extends ApplicationContextProvider, LifeCycle {

    @SuppressWarnings("unchecked")
    @Override
    default T start() {
        return (T) this;
    }

    @SuppressWarnings("unchecked")
    @Override
    default T stop() {
        ApplicationContext applicationContext = getApplicationContext();
        if (applicationContext != null && applicationContext.isRunning()) {
           applicationContext.stop();
        }
        return (T) this;
    }
}
