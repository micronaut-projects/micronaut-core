package example.security.domain

import grails.gorm.annotation.Entity
import org.grails.datastore.gorm.GormEntity

@Entity
class UserRole implements GormEntity<UserRole> {
    User user
    Role role

    static constraints = {
        user nullable: false
        role nullable: false
    }
}
