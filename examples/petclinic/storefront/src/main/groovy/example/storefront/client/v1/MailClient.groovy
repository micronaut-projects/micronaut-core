package example.storefront.client.v1

import example.api.v1.MailOperation
import groovy.transform.CompileStatic
import org.particleframework.http.client.Client

@CompileStatic
@Client(value = 'mail', path = "/v1/pets")
interface MailClient extends MailOperation {
}
