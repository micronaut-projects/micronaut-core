package example.storefront

import example.api.v1.HealthStatus
import groovy.transform.CompileStatic
import io.reactivex.Single
import org.particleframework.http.annotation.Controller
import org.particleframework.http.annotation.Get
import javax.inject.Inject
import javax.inject.Singleton

@CompileStatic
@Singleton
@Controller('/mail')
class MailController {

    @Inject
    MailClient mailClient

    @Get('/health')
    Single<HealthStatus> health() {
        mailClient.health()
    }
}
