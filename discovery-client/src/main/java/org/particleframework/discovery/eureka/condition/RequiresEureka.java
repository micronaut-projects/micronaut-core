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
package org.particleframework.discovery.eureka.condition;

import org.particleframework.context.annotation.Requires;
import org.particleframework.discovery.eureka.EurekaConfiguration;

import java.lang.annotation.*;

/**
 * Meta annotation for that can be added to any component that requires Eureka to load
 *
 * @author graemerocher
 * @since 1.0
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.PACKAGE, ElementType.TYPE})
@Requires(property = EurekaConfiguration.HOST)
@Requires(property = EurekaConfiguration.PORT)
public @interface RequiresEureka {
}
