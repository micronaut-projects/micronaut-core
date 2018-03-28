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
package io.micronaut.discovery.aws.route53.condition;

import io.micronaut.context.annotation.Requires;
import io.micronaut.discovery.aws.route53.Route53DiscoveryConfiguration;
import io.micronaut.discovery.consul.ConsulConfiguration;

import java.lang.annotation.*;

/**
 * Meta annotation for Consul requirements
 *
 * @author graemerocher
 * @since 1.0
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.PACKAGE, ElementType.TYPE})
@Requires(property = Route53DiscoveryConfiguration.PREFIX + ".enabled", value = "true", defaultValue = "true")
public @interface RequiresRoute53 {
}
