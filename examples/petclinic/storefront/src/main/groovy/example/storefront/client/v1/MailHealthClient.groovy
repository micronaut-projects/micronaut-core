package example.storefront.client.v1

import example.api.v1.HealthStatusOperation
import groovy.transform.CompileStatic
import org.particleframework.http.client.Client

@CompileStatic
@Client(id = 'mail')
interface MailHealthClient extends HealthStatusOperation {
}
