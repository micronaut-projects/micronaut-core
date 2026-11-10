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
package io.micronaut.inject.context.dependencies.application

import io.micronaut.context.ApplicationContext
import io.micronaut.context.DefaultBeanContext
import io.micronaut.context.watch.ConfigurationChange
import io.micronaut.context.watch.ReloadingConfigurationWatcher
import spock.lang.AutoCleanup
import spock.lang.Specification

class RecreateRunningApplicationSpec extends Specification {

    @AutoCleanup
    ApplicationContext context = ApplicationContext.builder()
        .properties('spec.name': 'RecreateRunningApplicationSpec')
        .beanDependencyTrackingEnabled(true)
        .start()

    void "recreating a bean the running server received keeps it, rather than destroy the server and stop the application"() {
        given:
        Server server = context.getBean(Server).start()
        Codec codec = context.getBean(Codec)

        when:
        boolean recreated = context.recreate(codec)

        then:
        !recreated
        context.running
        server.running
        context.getBean(Server).is(server)
        context.getBean(Codec).is(codec)

        when: "by type"
        recreated = context.recreate(Codec, null)

        then:
        !recreated
        context.running
        server.running
        context.getBean(Codec).is(codec)

        and: "the caller is told it was refused, and a development runtime restarts instead"
        ((DefaultBeanContext) context).tryRecreate(codec) == DefaultBeanContext.Recreation.REFUSED
        ((DefaultBeanContext) context).refusedRecreations() == 3
    }

    void "recreating the running server itself is refused"() {
        given:
        Server server = context.getBean(Server).start()

        expect:
        !context.recreate(server)
        context.running
        server.running
        context.getBean(Server).is(server)
    }

    void "a configuration watch asking to recreate a bean the running server received is told the change needs a restart"() {
        given:
        Server server = context.getBean(Server).start()
        Codec codec = context.getBean(Codec)

        when:
        def outcomes = ((DefaultBeanContext) context).notifyConfigurationChange(ConfigurationChange.ofKeys(Set.of('codec.charset')))

        then:
        outcomes == [ReloadingConfigurationWatcher.Outcome.REQUIRES_RESTART]
        context.running
        server.running
        context.getBean(Codec).is(codec)
    }

    void "a configuration watch asking to recreate a bean the context does not hold is told IGNORED, though another recreation was refused meanwhile"() {
        given:
        Server server = context.getBean(Server).start()
        Codec codec = context.getBean(Codec)
        Template template = context.getBean(Template)
        boolean refused = false
        // another caller, such as a refresh on another thread, has a recreation refused while the template's runs
        InterleavedScope.duringLookup = { -> refused = !context.recreate(codec) }

        when:
        def outcomes = ((DefaultBeanContext) context).notifyConfigurationChange(ConfigurationChange.ofKeys(Set.of('template.name')))

        then: "the template's recreation was not refused, its context just does not hold it"
        refused
        outcomes == [ReloadingConfigurationWatcher.Outcome.IGNORED]
        ((DefaultBeanContext) context).tryRecreate(template) == DefaultBeanContext.Recreation.NOT_HELD
        context.running
        server.running

        cleanup:
        InterleavedScope.duringLookup = null
    }

    void "a bean the server received is recreated as before once the server is not running, and a bean it did not receive always is"() {
        given:
        Server server = context.getBean(Server)
        Codec codec = context.getBean(Codec)
        ResolverHolder holder = context.getBean(ResolverHolder)

        expect: "the server was never started"
        ((DefaultBeanContext) context).tryRecreate(codec) == DefaultBeanContext.Recreation.RECREATED
        !context.getBean(Codec).is(codec)
        !context.getBean(Server).is(server)

        when:
        context.getBean(Server).start()

        then: "the server does not hold the resolver"
        context.recreate(holder.resolver)
        context.running
        !context.getBean(ResolverHolder).is(holder)
        ((DefaultBeanContext) context).refusedRecreations() == 0
    }
}
