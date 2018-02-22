package example.storefront.client.v1

import example.api.v1.Email
import example.api.v1.HealthStatusOperation
import example.api.v1.MailOperation
import groovy.transform.CompileStatic
import org.particleframework.http.HttpResponse
import org.particleframework.http.annotation.Body
import org.particleframework.http.annotation.Post
import org.particleframework.http.client.Client

import javax.validation.Valid

@CompileStatic
@Client(id = 'mail')
interface MailClient extends HealthStatusOperation {
    @Post("/v1/mail/send")
    HttpResponse send(@Valid @Body Email email)
}
