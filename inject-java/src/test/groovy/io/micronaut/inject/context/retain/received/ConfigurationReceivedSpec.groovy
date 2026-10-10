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
package io.micronaut.inject.context.retain.received

import io.micronaut.context.ApplicationContext
import io.micronaut.context.BeanRegistration
import io.micronaut.context.DefaultBeanContext
import io.micronaut.context.reload.AnnotatedBeanRetentionPolicy
import io.micronaut.context.reload.PolicyRetentionCriteria
import spock.lang.Specification

import java.util.function.Predicate

/**
 * A configuration bean in a retained bean's closure is left out of it, but what the configuration bean received is
 * examined, since the retained bean may hold it through the configuration: a provider, or a class the restart
 * replaces, refuses the retained bean.
 */
class ConfigurationReceivedSpec extends Specification {

    private static final Map<String, Object> PROPERTIES = ['spec.name': 'ConfigurationReceivedSpec']

    void "configuration that received a provider refuses the bean made from it"() {
        given:
        ApplicationContext first = start(List.of())
        ProvidedClient provided = first.getBean(ProvidedClient)
        PlainClient plain = first.getBean(PlainClient)
        ListenedClient listened = first.getBean(ListenedClient)

        when:
        Collection<BeanRegistration<?>> retained = stopRetaining(first, { false })

        then: "the provider would resolve through the stopped context"
        !retained*.bean.any { it.is(provided) }

        and: "the beans whose configuration received nothing bound to the context are kept, without the configuration"
        retained*.bean.any { it.is(plain) }
        retained*.bean.any { it.is(listened) }
        !retained*.bean.any { it instanceof PlainSettings || it instanceof ListenedSettings || it instanceof ProvidedSettings }

        when:
        ApplicationContext second = start(retained)

        then:
        second.getBean(PlainClient).is(plain)
        second.getBean(ListenedClient).is(listened)
        !second.getBean(ProvidedClient).is(provided)

        cleanup:
        second?.close()
    }

    void "configuration that received a class the restart replaces refuses the bean made from it"() {
        given:
        ApplicationContext first = start(List.of())
        ListenedClient listened = first.getBean(ListenedClient)
        PlainClient plain = first.getBean(PlainClient)

        when:
        Collection<BeanRegistration<?>> retained = stopRetaining(first, { Class<?> type -> type == SettingsListener })

        then: "the listener the configuration received is of the old generation of the application"
        !retained*.bean.any { it.is(listened) }
        retained*.bean.any { it.is(plain) }

        cleanup:
        start(retained).close()
    }

    private static Collection<BeanRegistration<?>> stopRetaining(ApplicationContext context, Predicate<Class<?>> replaced) {
        return ((DefaultBeanContext) context).stopRetaining(new PolicyRetentionCriteria(List.of(AnnotatedBeanRetentionPolicy.INSTANCE),
            { String prefix -> false }, replaced))
    }

    private static ApplicationContext start(Collection<BeanRegistration<?>> retained) {
        return ApplicationContext.builder()
            .properties(PROPERTIES)
            .beanDependencyTrackingEnabled(true)
            .retainedRegistrations(retained)
            .start()
    }
}
