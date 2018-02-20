package example.storefront

import example.api.v1.MailOperation
import groovy.transform.CompileStatic
import org.particleframework.http.client.Client

@CompileStatic
@Client(value = 'mail')
interface MailClient extends MailOperation {
}
