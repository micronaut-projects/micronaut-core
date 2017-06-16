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
package org.particleframework.configuration.hibernate.gorm

import grails.gorm.annotation.Entity
import groovy.transform.CompileStatic
import org.grails.datastore.mapping.services.Service
import org.grails.orm.hibernate.HibernateDatastore
import org.particleframework.context.ApplicationContext
import org.particleframework.context.annotation.Context
import org.particleframework.spring.core.env.PropertyResolverAdapter

import javax.inject.Provider
import java.util.stream.Stream

/**
 * Configures GORM within Particle
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@CompileStatic
@Context
class HibernateDatastoreProvider implements Provider<HibernateDatastore> {

    final ApplicationContext context

    HibernateDatastoreProvider(ApplicationContext context) {
        this.context = context
    }

    @Override
    HibernateDatastore get() {
        Stream<Class> entities = context.environment.scan(Entity)
        Class[] classes = entities.toArray() as Class[]
        HibernateDatastore datastore = new HibernateDatastore(new PropertyResolverAdapter(context),classes)
        for(o in datastore.getServices()) {
            Service service = (Service)o
            context.registerSingleton(service)
        }
        return datastore
    }
}
