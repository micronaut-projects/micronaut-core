/*
 * Copyright 2017-2018 original authors
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
package io.micronaut.configuration.hibernate.jpa.jcache

import io.micronaut.configuration.hibernate.jpa.JpaConfiguration
import io.micronaut.context.ApplicationContext
import org.hibernate.cache.jcache.ConfigSettings
import spock.lang.Specification

import javax.cache.CacheManager

/**
 * @author Marcel Overdijk
 * @since 1.1
 */
class HibernateJCacheSetupSpec extends Specification {

    void "test cache manager bound"() {
        given:
        CacheManager cacheManager = Mock(CacheManager)
        ApplicationContext applicationContext = ApplicationContext.build([
                'datasources.default.name'                     : 'mydb',
                'jpa.default.properties.hibernate.hbm2ddl.auto': 'create-drop'
        ])
                .mainClass(HibernateJCacheSetupSpec)
                .build()
        applicationContext.registerSingleton(cacheManager)
        applicationContext.start()

        JpaConfiguration jpaConfiguration = applicationContext.getBean(JpaConfiguration)
        Map<String, Object> jpaProperties = jpaConfiguration.getProperties()

        expect:
        jpaProperties[ConfigSettings.CACHE_MANAGER] == cacheManager

        cleanup:
        applicationContext.close()
    }

    void "test cache manager not bound"() {
        given:
        CacheManager cacheManager = Mock(CacheManager)
        ApplicationContext applicationContext = ApplicationContext.build([
                'datasources.default.name'                     : 'mydb',
                'jpa.default.properties.hibernate.hbm2ddl.auto': 'create-drop'
        ])
                .mainClass(HibernateJCacheSetupSpec)
                .build()
        applicationContext.start()

        JpaConfiguration jpaConfiguration = applicationContext.getBean(JpaConfiguration)
        Map<String, Object> jpaProperties = jpaConfiguration.getProperties()

        expect:
        jpaProperties[ConfigSettings.CACHE_MANAGER] == null

        cleanup:
        applicationContext.close()
    }
}

