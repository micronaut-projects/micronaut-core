package example.storefront

import example.api.v1.HealthStatus
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import groovy.transform.CompileStatic
import io.reactivex.Single
import org.particleframework.http.HttpRequest
import org.particleframework.http.HttpResponse
import org.particleframework.http.client.Client
import org.particleframework.http.client.RxHttpClient

import javax.inject.Inject
import javax.inject.Singleton


@CompileStatic
@Singleton
class MailClient {

    @Inject
    @Client('http://localhost:8090')
    RxHttpClient client

    Single<HealthStatus> health() {
        client.exchange(
                HttpRequest.GET("/health"),
                String
        ).singleOrError().map { HttpResponse<String> rsp ->

            if ( rsp.status.code == 200 ) {
                String status = parseStatus(rsp.body())
                if ( !status ) {
                    return new HealthStatus('DOWN')
                }
                return new HealthStatus(status)
            } else {
                return new HealthStatus('DOWN')
            }
        }

    }

    static String parseStatus(String body) {
        Object obj = new JsonSlurper().parseText(body)
        if ( obj instanceof Map ) {
            return ((Map) obj).status
        }
        null
    }

    void send(String email) {
        String requestBody = JsonOutput.toJson([recipient: email])
        client.exchange(HttpRequest.POST('/mail/send', requestBody)).blockingSubscribe()
    }
}