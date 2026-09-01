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
package io.micronaut.inject.lifecycle.registrationclose

import io.micronaut.context.ApplicationContext
import io.micronaut.context.BeanRegistration
import io.micronaut.core.type.Argument
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class IdempotentRegistrationCloseSpec extends Specification {

    @Shared
    @AutoCleanup
    ApplicationContext context = ApplicationContext.run()

    void "closing a registration twice destroys the bean once"() {
        given:
        SimpleBeanPreDestroyListener preDestroy = context.getBean(SimpleBeanPreDestroyListener)
        SimpleBeanDestroyedListener destroyed = context.getBean(SimpleBeanDestroyedListener)
        int destroyedBefore = destroyed.destroyed.size()
        int preDestroyBefore = preDestroy.destroyed.size()

        when: "the registration is closed twice"
        BeanRegistration<SimpleBean> registration =
                context.getBeanRegistration(Argument.of(SimpleBean), null)
        registration.close()
        registration.close()

        then: "each destruction listener saw the bean once"
        preDestroy.destroyed.size() == preDestroyBefore + 1
        destroyed.destroyed.size() == destroyedBefore + 1
    }
}
