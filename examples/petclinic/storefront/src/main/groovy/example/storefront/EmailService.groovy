package example.storefront

import example.api.v1.Email
import example.api.v1.Pet
import example.storefront.client.v1.MailClient
import example.storefront.client.v1.PetClient
import groovy.transform.CompileStatic

import javax.inject.Singleton

@Singleton
@CompileStatic
class EmailService {

    private final PetClient petClient
    private final MailClient mailClient

    EmailService(PetClient petClient, MailClient mailClient) {
        this.petClient = petClient
        this.mailClient = mailClient
    }

    void send(String slug, String email) {
        petClient.find(slug).toSingle().subscribe({Pet pet -> sendEmail(email, pet)}, {})
    }

    void sendEmail(String email, Pet pet) {
        Email emailDTO = new Email()
        emailDTO.with {
            recipient = email
            subject = "Micronaut Pet Store - Re: ${pet.name} from ${pet.vendor}"
        }
        mailClient.send(emailDTO)
    }
}
