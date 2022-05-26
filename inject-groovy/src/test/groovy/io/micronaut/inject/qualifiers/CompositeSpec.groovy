/*
 * Copyright 2017-2019 original authors
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
package io.micronaut.inject.qualifiers

import io.micronaut.context.DefaultBeanContext
import io.micronaut.context.Qualifier
import spock.lang.Specification

import jakarta.inject.Named
import jakarta.inject.Singleton

class CompositeSpec extends Specification {

    void 'test using a composite qualifier'() {
        given:
        DefaultBeanContext context = new DefaultBeanContext()
        context.start()

        when:
        Qualifier qualifier = Qualifiers.byQualifiers(Qualifiers.byType(Runnable), Qualifiers.byName('thread'))

        then:
        context.getBeanDefinitions(qualifier).size() == 1

        cleanup:
        context.close()
    }


    @Singleton
    @Named("thread")
    static class Runner implements Runnable {
        @Override
        void run() {

        }
    }
}

