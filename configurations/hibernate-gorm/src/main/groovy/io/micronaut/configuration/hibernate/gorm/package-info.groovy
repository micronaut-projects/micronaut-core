@io.micronaut.context.annotation.Configuration
@Requirements([
    @Requires(classes = [HibernateDatastore, javax.persistence.Entity]),
    @Requires(entities = [Entity, javax.persistence.Entity])
])
package io.micronaut.configuration.hibernate.gorm

import grails.gorm.annotation.Entity
import org.grails.orm.hibernate.HibernateDatastore
import io.micronaut.context.annotation.Requirements
import io.micronaut.context.annotation.Requires

