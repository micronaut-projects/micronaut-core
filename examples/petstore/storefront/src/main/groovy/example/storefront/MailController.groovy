package example.storefront

import example.api.v1.HealthStatus
import example.storefront.client.v1.MailClient
import groovy.transform.CompileStatic
import io.reactivex.Single
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Post

import javax.inject.Inject
import javax.inject.Singleton

@CompileStatic
@Controller('/mail')
class MailController {

    @Inject
    EmailService emailService

    @Inject
    MailClient mailClient

    @Get('/health')
    Single<HealthStatus> health() {
        mailClient.health().onErrorReturn({ new HealthStatus('DOWN') })
    }

    @Post(uri = '/send', consumes = MediaType.APPLICATION_JSON)
    HttpResponse send(@Body('slug') String slug, @Body('email') String email) {
        emailService.send(slug, email)
        HttpResponse.ok()
    }
}
