/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.http.server.netty.ssl;

import io.micronaut.context.BeanContext;
import io.micronaut.context.condition.Condition;
import io.micronaut.context.condition.ConditionContext;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.value.PropertyResolver;
import io.micronaut.http.ssl.ServerSslConfiguration;
import io.micronaut.http.ssl.SslConfiguration;

/**
 * Checks for either the deprecated {@code micronaut.ssl.enabled} and the replacement {@code micronaut.server.ssl.enabled} properties.
 * Passes if either are configured
 *
 * Subclasses make choices based on the existence and value of them.
 *
 * @since 3.3.x
 */
class SslEnabledCondition  implements Condition {

    @Override
    public boolean matches(ConditionContext context) {
        BeanContext beanContext = context.getBeanContext();
        if (beanContext instanceof PropertyResolver resolver) {

            boolean deprecated = enabledForPrefix(resolver, SslConfiguration.PREFIX);
            boolean server = enabledForPrefix(resolver, ServerSslConfiguration.PREFIX);
            if (!deprecated && !server) {
                context.fail("Neither the old deprecated " + SslConfiguration.PREFIX + ".build-self-signed, nor the new " + ServerSslConfiguration.PREFIX + ".build-self-signed were enabled.");
                return false;
            } else {
                return true;
            }
        } else {
            context.fail("Bean requires property but BeanContext does not support property resolution");
            return false;
        }
    }

    private boolean enabledForPrefix(PropertyResolver resolver, String prefix) {
        return resolver.getProperty(prefix + ".enabled", ConversionContext.BOOLEAN).orElse(false);
    }
}
