/*
 * Copyright 2017 original authors
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
package org.particleframework.cache

import org.particleframework.context.ApplicationContext
import org.particleframework.inject.qualifiers.Qualifiers
import spock.lang.Specification

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class SyncCacheSpec extends Specification {

    void "test configure sync cache"() {
        given:
        ApplicationContext applicationContext = ApplicationContext.run(
                'particle.caches.test.initialCapacity':1,
                'particle.caches.test.maximumSize':3
        )

        when:
        SyncCache syncCache = applicationContext.getBean(SyncCache, Qualifiers.byName('test'))

        then:
        syncCache.name == 'test'

        when:
        syncCache.put("one", 1)
        syncCache.put("two", 2)
        syncCache.put("three", 3)
        syncCache.put("four", 4)
        sleep(1000)

        then:
        !syncCache.get("one", Integer).isPresent()
        syncCache.get("two", Integer).isPresent()
        syncCache.get("three", Integer).isPresent()
        syncCache.get("four", Integer).isPresent()

        when:
        syncCache.invalidate("two")

        then:
        !syncCache.get("one", Integer).isPresent()
        !syncCache.get("two", Integer).isPresent()
        syncCache.get("three", Integer).isPresent()
        syncCache.putIfAbsent("three", 3).isPresent()
        syncCache.get("four", Integer).isPresent()


        when:
        syncCache.invalidateAll()

        then:
        !syncCache.get("one", Integer).isPresent()
        !syncCache.get("two", Integer).isPresent()
        !syncCache.get("three", Integer).isPresent()
        !syncCache.get("four", Integer).isPresent()

        cleanup:
        applicationContext.stop()
    }
}
