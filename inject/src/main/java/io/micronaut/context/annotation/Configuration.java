/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.context.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

/**
 * <p>A configuration is a grouping of bean definitions under a package. A configuration can have requirements applied
 * to it with {@link Requires} such that the entire configuration only loads of the requirements are met. For example consider the following {@code package-info.java} file: </p>
 *
 *
 * <pre class="code">
 * &#064;Configuration
 * &#064;Requires(classes = Cluster.class)
 * package io.micronaut.configuration.cassandra;
 *
 * import com.datastax.driver.core.Cluster;
 * import io.micronaut.context.annotation.Configuration;
 * import io.micronaut.context.annotation.Requires;
 * </pre>
 *
 * <p>In the example above the {@link Requires} annotation ensures all beans contained within the package are loaded only if the {@code Cluster} class is present on the classpath.</p>
 *
 * <p>The {@link io.micronaut.context.ApplicationContextBuilder#include(String...)} and {@link io.micronaut.context.ApplicationContextBuilder#exclude(String...)} methods can also be used to control which configurations are
 * loaded when building the {@link io.micronaut.context.ApplicationContext}</p>
 *
 * @author Graeme Rocher
 * @see Requires
 * @see io.micronaut.context.ApplicationContextBuilder#exclude(String...)
 * @since 1.0
 */
@Documented
@Target(ElementType.PACKAGE)
public @interface Configuration {
}
