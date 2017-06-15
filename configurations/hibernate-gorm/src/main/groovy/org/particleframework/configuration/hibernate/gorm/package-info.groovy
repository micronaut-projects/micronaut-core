@org.particleframework.context.annotation.Configuration
@Requirements([
    @Requires(classes = HibernateDatastore),
    @Requires(property = 'dataSource.url')
])
package org.particleframework.configuration.hibernate.gorm

import org.grails.orm.hibernate.HibernateDatastore
import org.particleframework.context.annotation.Requirements
import org.particleframework.context.annotation.Requires

