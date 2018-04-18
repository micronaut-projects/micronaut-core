package example.security.services

import groovy.transform.CompileStatic
import io.micronaut.security.authentication.providers.UserFetcher
import io.micronaut.security.authentication.providers.UserState
import javax.inject.Singleton

@CompileStatic
@Singleton
class UserFetcherService implements UserFetcher {

    protected  final UserGormService userGormService

    UserFetcherService(UserGormService userGormService) {
        this.userGormService = userGormService
    }

    @Override
    Optional<UserState> findByUsername(String username) {
        UserState user = userGormService.findByUsername(username) as UserState
        Optional.ofNullable(user)
    }
}
