@org.particleframework.context.annotation.Configuration
@Requirements([
    @Requires(classes = [HibernateDatastore, javax.persistence.Entity]),
    @Requires(entities = [Entity, javax.persistence.Entity])
])
package org.particleframework.configuration.hibernate.gorm

import grails.gorm.annotation.Entity
import org.grails.orm.hibernate.HibernateDatastore
import org.particleframework.context.annotation.Requirements
import org.particleframework.context.annotation.Requires

