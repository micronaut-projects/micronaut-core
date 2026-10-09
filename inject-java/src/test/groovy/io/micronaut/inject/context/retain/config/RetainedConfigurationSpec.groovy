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
package io.micronaut.inject.context.retain.config

import io.micronaut.context.ApplicationContext
import io.micronaut.context.BeanRegistration
import io.micronaut.context.DefaultBeanContext
import io.micronaut.context.annotation.Retain
import spock.lang.Specification

/**
 * A configuration bean a retained bean received does not keep it from being retained, whether the configuration is under
 * a prefix its retention names or under another one, which is observed: the bean only copied the values, which stay
 * valid until a change under either prefix.
 */
class RetainedConfigurationSpec extends Specification {

    def setup() {
        SizedPool.CREATED.set(0)
    }

    void "a retained bean that received configuration beans under a prefix that releases it is retained, and they are not"() {
        given:
        ApplicationContext first = start(List.of())
        SizedPool covered = first.getBean(SizedPool.Covered)
        SizedPool uncovered = first.getBean(SizedPool.Uncovered)
        PoolProperties properties = first.getBean(PoolProperties)
        PoolLimits limits = first.getBean(PoolLimits)
        OtherProperties other = first.getBean(OtherProperties)

        when:
        Collection<BeanRegistration<?>> retained = stopRetaining(first, Set.of())

        then: "the pool whose configuration its retention covers is retained, without its configuration beans"
        retained*.bean.any { it.is(covered) }
        !retained*.bean.any { it.is(properties) || it.is(limits) }

        and: "so is the pool that received configuration under another prefix, which is observed rather than retained"
        retained*.bean.any { it.is(uncovered) }
        !retained*.bean.any { it.is(other) }

        when:
        ApplicationContext second = start(retained)

        then: "the next context serves the same pool, and configuration beans of its own"
        second.getBean(SizedPool.Covered).is(covered)
        covered.size == 3
        covered.max == 7
        !second.getBean(PoolProperties).is(properties)
        second.getBean(PoolProperties).size == 3
        !second.getBean(PoolLimits).is(limits)
        second.getBean(SizedPool.Uncovered).is(uncovered)
        !second.getBean(OtherProperties).is(other)
        SizedPool.CREATED.get() == 2

        cleanup:
        second?.close()
    }

    void "a change under the prefix of configuration a retained bean received releases it, although its retention names another prefix"() {
        given:
        ApplicationContext first = start(List.of())
        SizedPool covered = first.getBean(SizedPool.Covered)
        SizedPool uncovered = first.getBean(SizedPool.Uncovered)

        when:
        Collection<BeanRegistration<?>> retained = stopRetaining(first, Set.of('other.pool'))

        then: "the pool made from the changed configuration is released, the other kept"
        !retained*.bean.any { it.is(uncovered) }
        retained*.bean.any { it.is(covered) }

        cleanup:
        start(retained).close()
    }

    private static Collection<BeanRegistration<?>> stopRetaining(ApplicationContext context, Set<String> touched) {
        return ((DefaultBeanContext) context).stopRetaining(new DefaultBeanContext.RetentionCriteria() {
            @Override
            boolean retain(BeanRegistration<?> registration) {
                return registration.beanDefinition.annotationMetadata.declaredMetadata.hasStereotype(Retain)
            }

            @Override
            Set<String> invalidatedBy(BeanRegistration<?> registration) {
                return registration.beanDefinition.annotationMetadata.declaredMetadata.stringValues(Retain, 'invalidatedBy') as Set<String>
            }

            @Override
            boolean touches(String prefix) {
                return touched.contains(prefix)
            }
        })
    }

    private static ApplicationContext start(Collection<BeanRegistration<?>> retained) {
        return ApplicationContext.builder()
            .properties(['spec.name': 'RetainedConfigurationSpec', 'my.pool.size': '3', 'my.pool.limits.max': '7', 'other.pool.size': '2'])
            .beanDependencyTrackingEnabled(true)
            .retainedRegistrations(retained)
            .start()
    }
}
