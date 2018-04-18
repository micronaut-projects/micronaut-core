package example.security.services

import example.security.domain.User
import grails.gorm.services.Service

@Service(User)
interface UserGormService {

    User save(String username, String password)

    User findByUsername(String username)

    User findById(Serializable id)

    void delete(Serializable id)
}
