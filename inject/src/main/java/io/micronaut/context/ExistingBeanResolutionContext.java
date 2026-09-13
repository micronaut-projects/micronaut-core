/*
 * Copyright 2017-2026 original authors
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

import java.util.ArrayList;
import java.util.List;

/**
 * A resolution context for a bean that already exists, opened from its registration.
 *
 * <p>The context that created the bean carried the beans created for it as its dependents, and handed them to the
 * registration. This context carries them again, so that whatever resolves for the existing bean, its pre-destroy
 * interception or a proxy that fronts it, sees the bean's dependent scope as the creation did. A bean created through
 * this context is a new dependent of the existing bean and is handed to the registration when the context is closed,
 * so that it is destroyed with the bean.</p>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
@SuppressWarnings("removal")
final class ExistingBeanResolutionContext extends DefaultBeanResolutionContext {

    private final BeanDisposingRegistration<?> registration;
    private int handed;

    ExistingBeanResolutionContext(BeanContext context, BeanDisposingRegistration<?> registration) {
        super(context, registration.getBeanDefinition());
        this.registration = registration;
        List<BeanRegistration<?>> dependents = registration.dependentBeans();
        this.handed = dependents.size();
        pushDependentBeans(new ArrayList<>(dependents));
        // for a reader compiled against an earlier version, which looked for them here
        setAttribute(BeanResolutionContext.EXISTING_DEPENDENT_BEANS, dependents);
    }

    /**
     * Hands the beans created through this context so far to the registration, and leaves the context as it is.
     *
     * <p>The container closes the context it resolves through after every bean it creates in it, so this runs in
     * the middle of a resolution as well as at its end; handing over early is harmless, and the dependents stay in
     * place for what still resolves.</p>
     */
    @Override
    public void close() {
        List<BeanRegistration<?>> dependents = getDependentBeans();
        for (int i = handed; i < dependents.size(); i++) {
            registration.addDependentBean(dependents.get(i));
        }
        handed = dependents.size();
        super.close();
    }
}
