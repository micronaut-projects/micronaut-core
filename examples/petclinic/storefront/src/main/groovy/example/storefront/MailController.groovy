package example.storefront

import example.api.v1.Email
import example.api.v1.HealthStatus
import example.storefront.client.v1.MailClient
import example.storefront.client.v1.MailHealthClient
import groovy.transform.CompileStatic
import io.reactivex.Single
import org.particleframework.http.HttpResponse
import org.particleframework.http.MediaType
import org.particleframework.http.annotation.Body
import org.particleframework.http.annotation.Controller
import org.particleframework.http.annotation.Get
import org.particleframework.http.annotation.Post

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
        mailClient.health().onErrorReturn( { new HealthStatus('DOWN') })
    }

    @Post('/send')
    HttpResponse send(@Body MailFormSubmission form) {
        Email emailDTO = new Email()
        emailDTO.setRecipient(form.email)
        mailClient.send(emailDTO)
        HttpResponse.ok()
    }
}