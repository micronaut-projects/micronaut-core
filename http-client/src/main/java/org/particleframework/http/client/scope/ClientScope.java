/*
 * Copyright 2018 original authors
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
package org.particleframework.http.client.scope;

import org.particleframework.context.LifeCycle;
import org.particleframework.context.scope.CustomScope;
import org.particleframework.http.client.Client;
import org.particleframework.inject.BeanDefinition;
import org.particleframework.inject.BeanIdentifier;

import javax.inject.Provider;
import javax.inject.Singleton;
import java.util.Optional;

/**
 * A scope for injecting {@link org.particleframework.http.client.HttpClient} implementations
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Singleton
// TODO
class ClientScope implements CustomScope<Client>, LifeCycle<ClientScope> {
    @Override
    public boolean isRunning() {
        return true;
    }

    @Override
    public Class<Client> annotationType() {
        return Client.class;
    }

    @Override
    public <T> T get(BeanDefinition<T> beanDefinition, BeanIdentifier identifier, Provider<T> provider) {
        return provider.get();
    }

    @Override
    public <T> Optional<T> remove(BeanIdentifier identifier) {
        return Optional.empty();
    }
}
