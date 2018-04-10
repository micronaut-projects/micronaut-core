package example.security.services

import example.security.domain.Role
import example.security.domain.User
import example.security.domain.UserRole
import grails.gorm.services.Query
import grails.gorm.services.Service

@Service(UserRole)
interface UserRoleGormService {

    UserRole save(User user, Role role)

    UserRole find(User user, Role role)

    void delete(Serializable id)

    @Query("""select $r.authority
    from ${UserRole ur}
    inner join ${User u = ur.user}
    inner join ${Role r = ur.role}
    where $u.username = $username""")
    List<String> findAuthoritiesByUsername(String username)
}
