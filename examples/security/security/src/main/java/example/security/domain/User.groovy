package example.security.domain

import grails.gorm.annotation.Entity
import org.grails.datastore.gorm.GormEntity

@Entity
class User implements GormEntity<User> {
    String username
    String password
    boolean enabled = true
    boolean accountExpired = false
    boolean accountLocked = false
    boolean passwordExpired = false

    static constraints = {
        username nullable: false, blank: false, unique: true
        password nullable: false, blank: false, password: true
    }

    static mapping = {
        password column: '`password`'
    }
}
