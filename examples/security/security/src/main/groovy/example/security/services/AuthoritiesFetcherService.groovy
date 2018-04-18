package example.security.services

import io.micronaut.security.authentication.providers.AuthoritiesFetcher
import javax.inject.Singleton

@Singleton
class AuthoritiesFetcherService implements AuthoritiesFetcher {

    protected  final UserRoleGormService userRoleGormService

    AuthoritiesFetcherService(UserRoleGormService userRoleGormService) {
        this.userRoleGormService = userRoleGormService
    }

    @Override
    List<String> findAuthoritiesByUsername(String username) {
        this.userRoleGormService.findAllAuthoritiesByUsername(username)
    }
}
