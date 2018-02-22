package example.storefront.client.v1

import example.api.v1.MailOperation
import groovy.transform.CompileStatic
import org.particleframework.http.client.Client

@CompileStatic
@Client(id = 'mail', path = "/v1/mail")
interface MailClient extends MailOperation {
}
