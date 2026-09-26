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

import io.micronaut.core.annotation.Internal;

import java.util.List;

/**
 * Provides the dependent beans of a bean.
 *
 * @author Denis Stepanov
 * @since 5.1.0
 * @deprecated Since 5.3.0 every {@link BeanRegistration} answers with {@link BeanRegistration#getDependentBeans()};
 * the registrations the context creates still implement this for a reader compiled against an earlier version.
 */
@Deprecated(since = "5.3.0", forRemoval = true)
@Internal
public interface DependentBeanProvider {

    /**
     * @return The dependent beans
     * @deprecated Use {@link BeanRegistration#getDependentBeans()}
     */
    @Deprecated(since = "5.3.0", forRemoval = true)
    List<BeanRegistration<?>> dependentBeans();
}
