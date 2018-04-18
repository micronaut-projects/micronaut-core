package example.security.domain

import grails.gorm.annotation.Entity
import org.grails.datastore.gorm.GormEntity

@Entity
class Role implements GormEntity<Role> {
    String authority

    static constraints = {
        authority nullable: false, unique: true
    }
}