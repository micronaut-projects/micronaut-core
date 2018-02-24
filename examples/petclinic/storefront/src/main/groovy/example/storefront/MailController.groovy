package example.storefront

import example.api.v1.Email
import example.api.v1.HealthStatus
import example.api.v1.Pet
import example.storefront.client.v1.MailClient
import example.storefront.client.v1.PetClient
import groovy.transform.CompileStatic
import io.reactivex.Single
import org.particleframework.http.HttpResponse
import org.particleframework.http.MediaType
import org.particleframework.http.annotation.Body
import org.particleframework.http.annotation.Controller
import org.particleframework.http.annotation.Get
import org.particleframework.http.annotation.Post
import org.particleframework.http.client.Client
import org.particleframework.http.client.RxStreamingHttpClient

import javax.inject.Inject
import javax.inject.Singleton

@CompileStatic
@Singleton
@Controller('/mail')
class MailController {

    @Inject
    MailClient mailClient

    private final PetClient petClient

    MailController(PetClient petClient) {
        this.petClient = petClient
    }


    @Get('/health')
    Single<HealthStatus> health() {
        mailClient.health().onErrorReturn({ new HealthStatus('DOWN') })
    }

    @Post(uri = '/send', consumes = MediaType.APPLICATION_JSON)
    HttpResponse send(@Body EmailCommand cmd) {
        petClient.find(cmd.slug).toSingle().subscribe({Pet pet -> sendEmail(cmd, pet)}, {})

        HttpResponse.ok()
    }

    void sendEmail(EmailCommand cmd, Pet pet) {
        Email email = new Email()
        email.with {
            setRecipient cmd.email
            setSubject "Micronaut Pet Store - Re: ${pet.name} from ${pet.vendor}"
        }
        mailClient.send(email)
    }
}

class EmailCommand {
    String email
    String slug
}