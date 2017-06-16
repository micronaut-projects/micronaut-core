@org.particleframework.context.annotation.Configuration
@Requirements([
//    @Requires(property = 'dataSource.url'),
    @Requires(classes = HibernateDatastore)
])
package org.particleframework.configuration.hibernate.gorm

import org.grails.orm.hibernate.HibernateDatastore
import org.particleframework.context.annotation.Requirements
import org.particleframework.context.annotation.Requires

