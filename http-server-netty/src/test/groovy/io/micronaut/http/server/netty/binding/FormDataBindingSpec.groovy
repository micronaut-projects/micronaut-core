/*
 * Copyright 2017-2019 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.http.server.netty.binding

import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Body
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.http.client.multipart.MultipartBody
import io.micronaut.http.server.netty.AbstractMicronautSpec
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post
import io.reactivex.Flowable
import io.reactivex.Maybe
import spock.lang.Issue

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class FormDataBindingSpec extends AbstractMicronautSpec {

    void "test simple string-based body parsing"() {
        when:
        def response = rxClient.exchange(HttpRequest.POST('/form/simple', [
                name:"Fred",
                age:"10"
        ]).contentType(MediaType.APPLICATION_FORM_URLENCODED_TYPE), String).blockingFirst()

        then:
        response.status == HttpStatus.OK
        response.body.isPresent()
        response.body.get() == "name: Fred, age: 10"
    }

    void "test pojo body parsing"() {
        when:
        def response = rxClient.exchange(HttpRequest.POST('/form/pojo', [
                name:"Fred",
                age:"10",
                something: "else"
        ]).contentType(MediaType.APPLICATION_FORM_URLENCODED_TYPE), String).blockingFirst()

        then:
        response.status == HttpStatus.OK
        response.body.isPresent()
        response.body.get() == "name: Fred, age: 10"
    }

    void "test simple string-based body parsing with missing data"() {
        when:
        rxClient.exchange(HttpRequest.POST('/form/simple', [
                name:"Fred"
        ]).contentType(MediaType.APPLICATION_FORM_URLENCODED_TYPE), String).blockingFirst()

        then:
        def e = thrown(HttpClientResponseException)
        e.response.status == HttpStatus.BAD_REQUEST
    }

    @Issue('https://github.com/micronaut-projects/micronaut-core/issues/1032')
    void "test POST SAML form url encoded"() {
        given:
        SamlClient client = embeddedServer.applicationContext.getBean(SamlClient)

        expect:
        client.process(SAML_DATA) == SAML_DATA
    }

    @Issue('https://github.com/micronaut-projects/micronaut-core/issues/1032')
    void "test POST SAML form multipart form data"() {
        given:
        MultipartBody body = MultipartBody.builder().addPart("SAMLResponse", SAML_DATA).build()
        String data = rxClient.retrieve(HttpRequest.POST("/form/saml/test/form-data", body)
                .contentType(MediaType.MULTIPART_FORM_DATA_TYPE), String).blockingFirst()

        expect:
        data == SAML_DATA
    }

    void "test url encoded request with very small chunk size"() {
        when:
        URL url = new URL(embeddedServer.getURL(), '/form/saml/test/small-form')
        HttpURLConnection conn = url.openConnection()
        conn.readTimeout = 30000
        conn.chunkedStreamingMode = 18 // A value of 18 will fail, 19 is enough to get the '='
        conn.setDoOutput(true)
        conn.setRequestProperty ("Content-Type", MediaType.APPLICATION_FORM_URLENCODED)
        def requestStream = conn.getOutputStream()
        requestStream.write('aaa0123456789=ABC%20%20%20%20&bbb0123456789=DEF%20%20%20%20'.getBytes())
        requestStream.close()
        def response = conn.getInputStream().text
        then:
        response == 'ABC    DEF    '
    }

    void "test url encoded request with very small chunk size bind to pogo"() {
        when:
        URL url = new URL(embeddedServer.getURL(), '/form/saml/test/small-form/pogo')
        HttpURLConnection conn = url.openConnection()
        conn.readTimeout = 30000
        conn.chunkedStreamingMode = 18 // A value of 18 will fail, 19 is enough to get the '='
        conn.setDoOutput(true)
        conn.setRequestProperty ("Content-Type", MediaType.APPLICATION_FORM_URLENCODED)
        def requestStream = conn.getOutputStream()
        requestStream.write('aaa0123456789=ABC%20%20%20%20&bbb0123456789=DEF%20%20%20%20'.getBytes())
        requestStream.close()
        def response = conn.getInputStream().text
        then:
        response == 'ABC    DEF    '
    }

    @Issue("https://github.com/micronaut-projects/micronaut-core/issues/2263")
    void "test binding directly to a string"() {
        when:
        def response = rxClient.exchange(HttpRequest.POST('/form/string', [
                name:"Fred",
                age:"10"
        ]).contentType(MediaType.APPLICATION_FORM_URLENCODED_TYPE), String).blockingFirst()

        then:
        response.status == HttpStatus.OK
        response.body.isPresent()
        response.body.get() == "name=Fred&age=10"
    }

    void "test binding directly to a reactive string"() {
        when:
        def response = rxClient.exchange(HttpRequest.POST('/form/maybe-string', [
                name:"Fred",
                age:"10"
        ]).contentType(MediaType.APPLICATION_FORM_URLENCODED_TYPE), String).blockingFirst()

        then:
        response.status == HttpStatus.OK
        response.body.isPresent()
        response.body.get() == "name=Fred&age=10"
    }
    
    @Controller(value = '/form', consumes = MediaType.APPLICATION_FORM_URLENCODED)
    static class FormController {

        @Post('/string')
        String string(@Body String string) {
            string
        }

        @Post('/maybe-string')
        Maybe<String> string(@Body Flowable<String> string) {
            string.reduce({ a, b -> a + b })
        }

        @Post('/simple')
        String simple(String name, Integer age) {
            "name: $name, age: $age"
        }

        @Post('/pojo')
        String pojo(@Body Person person) {
            "name: $person.name, age: $person.age"
        }

        static class Person {
            String name
            Integer age
        }
    }


    @Controller('/form/saml/test')
    static class MainController {

        @Post(consumes = MediaType.APPLICATION_FORM_URLENCODED)
        public String process(String SAMLResponse) {
            System.out.println("Response: " + SAMLResponse)
            System.out.println("Response length: " + SAMLResponse.length())
            assert SAMLResponse == FormDataBindingSpec.SAML_DATA
            assert SAMLResponse.length() == FormDataBindingSpec.SAML_DATA.length()
            return SAMLResponse
        }

        @Post(uri = "/form-data", consumes = MediaType.MULTIPART_FORM_DATA)
        public String processFormData(String SAMLResponse) {
            System.out.println("Response: " + SAMLResponse)
            System.out.println("Response length: " + SAMLResponse.length())
            assert SAMLResponse == FormDataBindingSpec.SAML_DATA
            assert SAMLResponse.length() == FormDataBindingSpec.SAML_DATA.length()
            return SAMLResponse
        }

        @Post(uri = "/small-form", consumes = MediaType.APPLICATION_FORM_URLENCODED)
        public String processTempFormData(String aaa0123456789, String bbb0123456789) {
            return aaa0123456789 + bbb0123456789
        }

        @Post(uri = "/small-form/pogo", consumes = MediaType.APPLICATION_FORM_URLENCODED)
        public String processTempFormData(UrlEncodedPogo pogo) {
            return pogo.aaa0123456789 + pogo.bbb0123456789
        }
    }

    static class UrlEncodedPogo {
        String aaa0123456789
        String bbb0123456789
    }

    @Client('/form/saml/test')
    static interface SamlClient {
        @Post(produces = MediaType.APPLICATION_FORM_URLENCODED)
        String process(String SAMLResponse)
    }

    static final String SAML_DATA = '''\
PHNhbWxwOlJlc3BvbnNlIHhtbG5zOnNhbWxwPSJ1cm46b2FzaXM6bmFtZXM6dGM6%0D%0AU0FNTDoyLjA6cHJvdG9jb2wiIHhtbG5zOmRzaWc9Imh0dHA6Ly93d3cudzMub3Jn%0D%0ALzIwMDAvMDkveG1sZHNpZyMiIHhtbG5zOmVuYz0iaHR0cDovL3d3dy53My5vcmcv%0D%0AMjAwMS8wNC94bWxlbmMjIiB4bWxuczpzYW1sPSJ1cm46b2FzaXM6bmFtZXM6dGM6%0D%0AU0FNTDoyLjA6YXNzZXJ0aW9uIiB4bWxuczp4NTAwPSJ1cm46b2FzaXM6bmFtZXM6%0D%0AdGM6U0FNTDoyLjA6cHJvZmlsZXM6YXR0cmlidXRlOlg1MDAiIHhtbG5zOnhzaT0i%0D%0AaHR0cDovL3d3dy53My5vcmcvMjAwMS9YTUxTY2hlbWEtaW5zdGFuY2UiIERlc3Rp%0D%0AbmF0aW9uPSJodHRwOi8vbG9jYWxob3N0OjcwMDAvYWNzIiBJRD0iaWQtN2ljWm5a%0D%0AZWVKZ3doMmxGSHVnSXBLeHktdGJJNE9iUVEta0pDSFItRiIgSW5SZXNwb25zZVRv%0D%0APSJPTkVMT0dJTl85YzAyODQ4NC02N2E1LTQ4YjYtODBhMi1mMWIwMDI3NTM5ZjAi%0D%0AIElzc3VlSW5zdGFudD0iMjAxOC0xMi0xNlQyMjoyMzo1NFoiIFZlcnNpb249IjIu%0D%0AMCI%2BPHNhbWw6SXNzdWVyIEZvcm1hdD0idXJuOm9hc2lzOm5hbWVzOnRjOlNBTUw6%0D%0AMi4wOm5hbWVpZC1mb3JtYXQ6ZW50aXR5Ij5odHRwczovL2F1dGgudnl2b2oudXB2%0D%0Acy5nbG9iYWx0ZWwuc2svb2FtL2ZlZDwvc2FtbDpJc3N1ZXI%2BPGRzaWc6U2lnbmF0%0D%0AdXJlPjxkc2lnOlNpZ25lZEluZm8%2BPGRzaWc6Q2Fub25pY2FsaXphdGlvbk1ldGhv%0D%0AZCBBbGdvcml0aG09Imh0dHA6Ly93d3cudzMub3JnLzIwMDEvMTAveG1sLWV4Yy1j%0D%0AMTRuIyIvPjxkc2lnOlNpZ25hdHVyZU1ldGhvZCBBbGdvcml0aG09Imh0dHA6Ly93%0D%0Ad3cudzMub3JnLzIwMDAvMDkveG1sZHNpZyNyc2Etc2hhMSIvPjxkc2lnOlJlZmVy%0D%0AZW5jZSBVUkk9IiNpZC03aWNablplZUpnd2gybEZIdWdJcEt4eS10Ykk0T2JRUS1r%0D%0ASkNIUi1GIj48ZHNpZzpUcmFuc2Zvcm1zPjxkc2lnOlRyYW5zZm9ybSBBbGdvcml0%0D%0AaG09Imh0dHA6Ly93d3cudzMub3JnLzIwMDAvMDkveG1sZHNpZyNlbnZlbG9wZWQt%0D%0Ac2lnbmF0dXJlIi8%2BPGRzaWc6VHJhbnNmb3JtIEFsZ29yaXRobT0iaHR0cDovL3d3%0D%0Ady53My5vcmcvMjAwMS8xMC94bWwtZXhjLWMxNG4jIi8%2BPC9kc2lnOlRyYW5zZm9y%0D%0AbXM%2BPGRzaWc6RGlnZXN0TWV0aG9kIEFsZ29yaXRobT0iaHR0cDovL3d3dy53My5v%0D%0AcmcvMjAwMC8wOS94bWxkc2lnI3NoYTEiLz48ZHNpZzpEaWdlc3RWYWx1ZT45ZHN4%0D%0AUTJVK2NFWXdJN25uTVdzaFZ3S05wMWs9PC9kc2lnOkRpZ2VzdFZhbHVlPjwvZHNp%0D%0AZzpSZWZlcmVuY2U%2BPC9kc2lnOlNpZ25lZEluZm8%2BPGRzaWc6U2lnbmF0dXJlVmFs%0D%0AdWU%2BSGlNMG9vbVpreDBuYjVobzJVaU1tT0QxRWdtaWJrWHhjYlJ0TGphdktDRnRL%0D%0AU2VYUnlWT3l3UnVQc1dyWlJOcklUOFMrMVRXWWFaa29uNWJObG5nRmVwTEhHM25i%0D%0AQUcveHZMQ0FoK21sMDFkUGp2QkptaFAxYjM3eVVjajQ5TnhaQkptZVQyTXB3Q1JC%0D%0AelIvZGlNeTA0bzJIWXhWT2k3T3F3Vk9sVGRMMmI0PTwvZHNpZzpTaWduYXR1cmVW%0D%0AYWx1ZT48ZHNpZzpLZXlJbmZvPjxkc2lnOlg1MDlEYXRhPjxkc2lnOlg1MDlDZXJ0%0D%0AaWZpY2F0ZT5NSUlDcnpDQ0FaZWdBd0lCQWdJS09WRXRSUUFBQUFBQ1FEQU5CZ2tx%0D%0AaGtpRzl3MEJBUXNGQURBYU1SZ3dGZ1lEVlFRREV3OWpZUzV6Ykc5MlpXNXphMjh1%0D%0AYzJzd0hoY05NVGN3T1RFNE1EZ3lNRE15V2hjTk1qQXdPVEU0TURnek1ETXlXakFn%0D%0ATVI0d0hBWURWUVFERXhWcFpIQXVkWEIyYzNBdGRubDJiMm91Ykc5allXd3dnWjh3%0D%0ARFFZSktvWklodmNOQVFFQkJRQURnWTBBTUlHSkFvR0JBTDIrUFdwV2JFbjIxUXc5%0D%0ATStOY0hhQlg4L096RHBKQ3lDMStpVEYxdEZ0ZnlZVFR0SG82TlVCOFlPMmhQNU5q%0D%0AVUxhYVJ3bUJiUitDdlRoNTVTcnJXL2hHaG9LM2Y3dExjMm80YkNrWXRCWGhHMUVL%0D%0AWW02aVZuYlhnTFpNTWxndHAyaktUcUJOejVnQ3ZvVXlXZitGUzF2L1A5ZEp2SFcy%0D%0AUXIwUURVaHN0SkZIQWdNQkFBR2pkVEJ6TUE0R0ExVWREd0VCL3dRRUF3SUU4REFU%0D%0AQmdOVkhTVUVEREFLQmdnckJnRUZCUWNEQVRBZEJnTlZIUTRFRmdRVVVtNmRSWGdy%0D%0AZ3ozNm9MMVM4RjNLeXh3MFhJRXdId1lEVlIwakJCZ3dGb0FVS2xPM1RocHJrMFhm%0D%0ANlZRcjJ0dTRUcG9Tem9Zd0RBWURWUjBUQVFIL0JBSXdBREFOQmdrcWhraUc5dzBC%0D%0AQVFzRkFBT0NBUUVBUnQ4ZGNNNWdlZ0xLS3BWbFpuN1htR01lYmRRbmtyNW1tTmJH%0D%0AdjhkbDNmb0tEazU4cWJmYWo4YzBaRHBDT2M3eEtiZHBZWjVpWHhIOHhuR2ZKTWJN%0D%0AUFpKMVdTejVNZy9WV1JMVFdYa1BqSnoxeUc3WVFKMEJpODRGZ2xaNVdXYVkrRFda%0D%0AWEhsdEJ4VFJpdDZiSG5jWTdNZFZHakxGNEhkQmhmSy9IcmFmcjd5Y1Z1bCtib0Fl%0D%0AT0h3TkNiRk92MmtMcktVSkM0b1NVWmQvN205Sno1MU5xQmVzWUdBVkdkMlVrSnJ0%0D%0AQlRIaUFlWTUwbUtMMDQraC9lWmhtcnFDMy9hQnFuczN4U1J2clRaUUVEa3QzYSt0%0D%0ANmhlSWRwTGg5OXRVc2hOU0x1VU9WZm9GVmtUbGJPMmR1bVVzSExQbmZGWEtnaTg2%0D%0ANDJxV1RpRFBkUmhMbmtPOThBPT08L2RzaWc6WDUwOUNlcnRpZmljYXRlPjwvZHNp%0D%0AZzpYNTA5RGF0YT48L2RzaWc6S2V5SW5mbz48L2RzaWc6U2lnbmF0dXJlPjxzYW1s%0D%0AcDpTdGF0dXM%2BPHNhbWxwOlN0YXR1c0NvZGUgVmFsdWU9InVybjpvYXNpczpuYW1l%0D%0Aczp0YzpTQU1MOjIuMDpzdGF0dXM6U3VjY2VzcyIvPjwvc2FtbHA6U3RhdHVzPjxz%0D%0AYW1sOkFzc2VydGlvbiBJRD0iaWQtdUxiejh3d2pBUVVvSWVEeXdUUGU5ZnJpTDBv%0D%0AdW10VUR0OUtVQ0ZyYyIgSXNzdWVJbnN0YW50PSIyMDE4LTEyLTE2VDIyOjIzOjU0%0D%0AWiIgVmVyc2lvbj0iMi4wIj48c2FtbDpJc3N1ZXIgRm9ybWF0PSJ1cm46b2FzaXM6%0D%0AbmFtZXM6dGM6U0FNTDoyLjA6bmFtZWlkLWZvcm1hdDplbnRpdHkiPmh0dHBzOi8v%0D%0AYXV0aC52eXZvai51cHZzLmdsb2JhbHRlbC5zay9vYW0vZmVkPC9zYW1sOklzc3Vl%0D%0Acj48ZHNpZzpTaWduYXR1cmU%2BPGRzaWc6U2lnbmVkSW5mbz48ZHNpZzpDYW5vbmlj%0D%0AYWxpemF0aW9uTWV0aG9kIEFsZ29yaXRobT0iaHR0cDovL3d3dy53My5vcmcvMjAw%0D%0AMS8xMC94bWwtZXhjLWMxNG4jIi8%2BPGRzaWc6U2lnbmF0dXJlTWV0aG9kIEFsZ29y%0D%0AaXRobT0iaHR0cDovL3d3dy53My5vcmcvMjAwMC8wOS94bWxkc2lnI3JzYS1zaGEx%0D%0AIi8%2BPGRzaWc6UmVmZXJlbmNlIFVSST0iI2lkLXVMYno4d3dqQVFVb0llRHl3VFBl%0D%0AOWZyaUwwb3VtdFVEdDlLVUNGcmMiPjxkc2lnOlRyYW5zZm9ybXM%2BPGRzaWc6VHJh%0D%0AbnNmb3JtIEFsZ29yaXRobT0iaHR0cDovL3d3dy53My5vcmcvMjAwMC8wOS94bWxk%0D%0Ac2lnI2VudmVsb3BlZC1zaWduYXR1cmUiLz48ZHNpZzpUcmFuc2Zvcm0gQWxnb3Jp%0D%0AdGhtPSJodHRwOi8vd3d3LnczLm9yZy8yMDAxLzEwL3htbC1leGMtYzE0biMiLz48%0D%0AL2RzaWc6VHJhbnNmb3Jtcz48ZHNpZzpEaWdlc3RNZXRob2QgQWxnb3JpdGhtPSJo%0D%0AdHRwOi8vd3d3LnczLm9yZy8yMDAwLzA5L3htbGRzaWcjc2hhMSIvPjxkc2lnOkRp%0D%0AZ2VzdFZhbHVlPk1WcGN5dk85UXcxdms5YjlSaThGL0Q3RDMvST08L2RzaWc6RGln%0D%0AZXN0VmFsdWU%2BPC9kc2lnOlJlZmVyZW5jZT48L2RzaWc6U2lnbmVkSW5mbz48ZHNp%0D%0AZzpTaWduYXR1cmVWYWx1ZT52SXFiZUZzeGtWMXlKRFppUDBudG5pR3VKbGdJK0xV%0D%0Ac1dzcGlodHE4bUw4cU04czZScVVmdkQzMDM1SXJaZm9SblVHZEcrOWZ4TXF2a2Zs%0D%0AalJKbGliL0w0MXdvV2Uwa2xuWXRvNXAxbittbWdEZ2MxNEhrNlhBY2NmcXdVK0lB%0D%0AMmJjeTI2Q1N6YUwxMmxuVDVjTXZZaFhMcU9BYzVMOWRpWmZ0bWRGSzdhRkU9PC9k%0D%0Ac2lnOlNpZ25hdHVyZVZhbHVlPjxkc2lnOktleUluZm8%2BPGRzaWc6WDUwOURhdGE%2B%0D%0APGRzaWc6WDUwOUNlcnRpZmljYXRlPk1JSUNyekNDQVplZ0F3SUJBZ0lLT1ZFdFJR%0D%0AQUFBQUFDUURBTkJna3Foa2lHOXcwQkFRc0ZBREFhTVJnd0ZnWURWUVFERXc5allT%0D%0ANXpiRzkyWlc1emEyOHVjMnN3SGhjTk1UY3dPVEU0TURneU1ETXlXaGNOTWpBd09U%0D%0ARTRNRGd6TURNeVdqQWdNUjR3SEFZRFZRUURFeFZwWkhBdWRYQjJjM0F0ZG5sMmIy%0D%0Ab3ViRzlqWVd3d2daOHdEUVlKS29aSWh2Y05BUUVCQlFBRGdZMEFNSUdKQW9HQkFM%0D%0AMitQV3BXYkVuMjFRdzlNK05jSGFCWDgvT3pEcEpDeUMxK2lURjF0RnRmeVlUVHRI%0D%0AbzZOVUI4WU8yaFA1TmpVTGFhUndtQmJSK0N2VGg1NVNyclcvaEdob0szZjd0TGMy%0D%0AbzRiQ2tZdEJYaEcxRUtZbTZpVm5iWGdMWk1NbGd0cDJqS1RxQk56NWdDdm9VeVdm%0D%0AK0ZTMXYvUDlkSnZIVzJRcjBRRFVoc3RKRkhBZ01CQUFHamRUQnpNQTRHQTFVZER3%0D%0ARUIvd1FFQXdJRThEQVRCZ05WSFNVRUREQUtCZ2dyQmdFRkJRY0RBVEFkQmdOVkhR%0D%0ANEVGZ1FVVW02ZFJYZ3JnejM2b0wxUzhGM0t5eHcwWElFd0h3WURWUjBqQkJnd0Zv%0D%0AQVVLbE8zVGhwcmswWGY2VlFyMnR1NFRwb1N6b1l3REFZRFZSMFRBUUgvQkFJd0FE%0D%0AQU5CZ2txaGtpRzl3MEJBUXNGQUFPQ0FRRUFSdDhkY001Z2VnTEtLcFZsWm43WG1H%0D%0ATWViZFFua3I1bW1OYkd2OGRsM2ZvS0RrNThxYmZhajhjMFpEcENPYzd4S2JkcFla%0D%0ANWlYeEg4eG5HZkpNYk1QWkoxV1N6NU1nL1ZXUkxUV1hrUGpKejF5RzdZUUowQmk4%0D%0ANEZnbFo1V1dhWStEV1pYSGx0QnhUUml0NmJIbmNZN01kVkdqTEY0SGRCaGZLL0hy%0D%0AYWZyN3ljVnVsK2JvQWVPSHdOQ2JGT3Yya0xyS1VKQzRvU1VaZC83bTlKejUxTnFC%0D%0AZXNZR0FWR2QyVWtKcnRCVEhpQWVZNTBtS0wwNCtoL2VaaG1ycUMzL2FCcW5zM3hT%0D%0AUnZyVFpRRURrdDNhK3Q2aGVJZHBMaDk5dFVzaE5TTHVVT1Zmb0ZWa1RsYk8yZHVt%0D%0AVXNITFBuZkZYS2dpODY0MnFXVGlEUGRSaExua085OEE9PTwvZHNpZzpYNTA5Q2Vy%0D%0AdGlmaWNhdGU%2BPC9kc2lnOlg1MDlEYXRhPjwvZHNpZzpLZXlJbmZvPjwvZHNpZzpT%0D%0AaWduYXR1cmU%2BPHNhbWw6U3ViamVjdD48c2FtbDpOYW1lSUQgRm9ybWF0PSJ1cm46%0D%0Ab2FzaXM6bmFtZXM6dGM6U0FNTDoyLjA6bmFtZWlkLWZvcm1hdDp0cmFuc2llbnQi%0D%0AIE5hbWVRdWFsaWZpZXI9Imh0dHBzOi8vYXV0aC52eXZvai51cHZzLmdsb2JhbHRl%0D%0AbC5zay9vYW0vZmVkIiBTUE5hbWVRdWFsaWZpZXI9Imh0dHA6Ly9kZXYuaXRtczMu%0D%0AYXhvbnByby5zayI%2BaWQtR0k0WmlYMWJSelF6aTY5aFBqcVpOODF2N3NrczRuWWVE%0D%0AdWNuUTJtUzwvc2FtbDpOYW1lSUQ%2BPHNhbWw6U3ViamVjdENvbmZpcm1hdGlvbiBN%0D%0AZXRob2Q9InVybjpvYXNpczpuYW1lczp0YzpTQU1MOjIuMDpjbTpiZWFyZXIiPjxz%0D%0AYW1sOlN1YmplY3RDb25maXJtYXRpb25EYXRhIEluUmVzcG9uc2VUbz0iT05FTE9H%0D%0ASU5fOWMwMjg0ODQtNjdhNS00OGI2LTgwYTItZjFiMDAyNzUzOWYwIiBOb3RPbk9y%0D%0AQWZ0ZXI9IjIwMTgtMTItMTZUMjI6NDM6NTRaIiBSZWNpcGllbnQ9Imh0dHA6Ly9s%0D%0Ab2NhbGhvc3Q6NzAwMC9hY3MiLz48L3NhbWw6U3ViamVjdENvbmZpcm1hdGlvbj48%0D%0AL3NhbWw6U3ViamVjdD48c2FtbDpDb25kaXRpb25zIE5vdEJlZm9yZT0iMjAxOC0x%0D%0AMi0xNlQyMjoyMzo1NFoiIE5vdE9uT3JBZnRlcj0iMjAxOC0xMi0xNlQyMjo0Mzo1%0D%0ANFoiPjxzYW1sOkF1ZGllbmNlUmVzdHJpY3Rpb24%2BPHNhbWw6QXVkaWVuY2U%2BaHR0%0D%0AcDovL2Rldi5pdG1zMy5heG9ucHJvLnNrPC9zYW1sOkF1ZGllbmNlPjwvc2FtbDpB%0D%0AdWRpZW5jZVJlc3RyaWN0aW9uPjwvc2FtbDpDb25kaXRpb25zPjxzYW1sOkF1dGhu%0D%0AU3RhdGVtZW50IEF1dGhuSW5zdGFudD0iMjAxOC0xMi0xNlQyMjowNjoxNVoiIFNl%0D%0Ac3Npb25JbmRleD0iaWQtZzhRRWkxM0lRbkxtRHVyMjZlLU9hdmJXMGhoMnd2QW9B%0D%0AZXFTTDBLbyIgU2Vzc2lvbk5vdE9uT3JBZnRlcj0iMjAxOC0xMi0xNlQyMjo0Mzo1%0D%0ANFoiPjxzYW1sOkF1dGhuQ29udGV4dD48c2FtbDpBdXRobkNvbnRleHRDbGFzc1Jl%0D%0AZj51cm46b2FzaXM6bmFtZXM6dGM6U0FNTDoyLjA6YWM6Y2xhc3NlczpQYXNzd29y%0D%0AZFByb3RlY3RlZFRyYW5zcG9ydDwvc2FtbDpBdXRobkNvbnRleHRDbGFzc1JlZj48%0D%0AL3NhbWw6QXV0aG5Db250ZXh0Pjwvc2FtbDpBdXRoblN0YXRlbWVudD48c2FtbDpB%0D%0AdHRyaWJ1dGVTdGF0ZW1lbnQ%2BPHNhbWw6QXR0cmlidXRlIE5hbWU9IkRlbGVnYXRp%0D%0Ab25UeXBlIiBOYW1lRm9ybWF0PSJ1cm46b2FzaXM6bmFtZXM6dGM6U0FNTDoyLjA6%0D%0AYXR0cm5hbWUtZm9ybWF0OmJhc2ljIj48c2FtbDpBdHRyaWJ1dGVWYWx1ZSB4bWxu%0D%0Aczp4cz0iaHR0cDovL3d3dy53My5vcmcvMjAwMS9YTUxTY2hlbWEiIHhzaTp0eXBl%0D%0APSJ4czpzdHJpbmciPjA8L3NhbWw6QXR0cmlidXRlVmFsdWU%2BPC9zYW1sOkF0dHJp%0D%0AYnV0ZT48c2FtbDpBdHRyaWJ1dGUgTmFtZT0iQWN0b3IuTGFzdE5hbWUiIE5hbWVG%0D%0Ab3JtYXQ9InVybjpvYXNpczpuYW1lczp0YzpTQU1MOjIuMDphdHRybmFtZS1mb3Jt%0D%0AYXQ6YmFzaWMiPjxzYW1sOkF0dHJpYnV0ZVZhbHVlIHhtbG5zOnhzPSJodHRwOi8v%0D%0Ad3d3LnczLm9yZy8yMDAxL1hNTFNjaGVtYSIgeHNpOnR5cGU9InhzOnN0cmluZyI%2B%0D%0AVGlzw61jaTwvc2FtbDpBdHRyaWJ1dGVWYWx1ZT48L3NhbWw6QXR0cmlidXRlPjxz%0D%0AYW1sOkF0dHJpYnV0ZSBOYW1lPSJBY3Rvci5JZGVudGl0eVR5cGUiIE5hbWVGb3Jt%0D%0AYXQ9InVybjpvYXNpczpuYW1lczp0YzpTQU1MOjIuMDphdHRybmFtZS1mb3JtYXQ6%0D%0AYmFzaWMiPjxzYW1sOkF0dHJpYnV0ZVZhbHVlIHhtbG5zOnhzPSJodHRwOi8vd3d3%0D%0ALnczLm9yZy8yMDAxL1hNTFNjaGVtYSIgeHNpOnR5cGU9InhzOnN0cmluZyI%2BMTwv%0D%0Ac2FtbDpBdHRyaWJ1dGVWYWx1ZT48L3NhbWw6QXR0cmlidXRlPjxzYW1sOkF0dHJp%0D%0AYnV0ZSBOYW1lPSJBY3Rvci5GaXJzdE5hbWUiIE5hbWVGb3JtYXQ9InVybjpvYXNp%0D%0AczpuYW1lczp0YzpTQU1MOjIuMDphdHRybmFtZS1mb3JtYXQ6YmFzaWMiPjxzYW1s%0D%0AOkF0dHJpYnV0ZVZhbHVlIHhtbG5zOnhzPSJodHRwOi8vd3d3LnczLm9yZy8yMDAx%0D%0AL1hNTFNjaGVtYSIgeHNpOnR5cGU9InhzOnN0cmluZyI%2BSmFua288L3NhbWw6QXR0%0D%0AcmlidXRlVmFsdWU%2BPC9zYW1sOkF0dHJpYnV0ZT48c2FtbDpBdHRyaWJ1dGUgTmFt%0D%0AZT0iU3ViamVjdElEU2VjdG9yIiBOYW1lRm9ybWF0PSJ1cm46b2FzaXM6bmFtZXM6%0D%0AdGM6U0FNTDoyLjA6YXR0cm5hbWUtZm9ybWF0OmJhc2ljIj48c2FtbDpBdHRyaWJ1%0D%0AdGVWYWx1ZSB4bWxuczp4cz0iaHR0cDovL3d3dy53My5vcmcvMjAwMS9YTUxTY2hl%0D%0AbWEiIHhzaTp0eXBlPSJ4czpzdHJpbmciPlNFQ1RPUl9VUFZTPC9zYW1sOkF0dHJp%0D%0AYnV0ZVZhbHVlPjwvc2FtbDpBdHRyaWJ1dGU%2BPHNhbWw6QXR0cmlidXRlIE5hbWU9%0D%0AIkFjdG9yLkF1dGhSZXNvdXJjZVR5cGUiIE5hbWVGb3JtYXQ9InVybjpvYXNpczpu%0D%0AYW1lczp0YzpTQU1MOjIuMDphdHRybmFtZS1mb3JtYXQ6YmFzaWMiPjxzYW1sOkF0%0D%0AdHJpYnV0ZVZhbHVlIHhtbG5zOnhzPSJodHRwOi8vd3d3LnczLm9yZy8yMDAxL1hN%0D%0ATFNjaGVtYSIgeHNpOnR5cGU9InhzOnN0cmluZyI%2BMTwvc2FtbDpBdHRyaWJ1dGVW%0D%0AYWx1ZT48L3NhbWw6QXR0cmlidXRlPjxzYW1sOkF0dHJpYnV0ZSBOYW1lPSJTdWJq%0D%0AZWN0LkZvcm1hdHRlZE5hbWUiIE5hbWVGb3JtYXQ9InVybjpvYXNpczpuYW1lczp0%0D%0AYzpTQU1MOjIuMDphdHRybmFtZS1mb3JtYXQ6YmFzaWMiPjxzYW1sOkF0dHJpYnV0%0D%0AZVZhbHVlIHhtbG5zOnhzPSJodHRwOi8vd3d3LnczLm9yZy8yMDAxL1hNTFNjaGVt%0D%0AYSIgeHNpOnR5cGU9InhzOnN0cmluZyI%2BSmFua28gVGlzw61jaTwvc2FtbDpBdHRy%0D%0AaWJ1dGVWYWx1ZT48L3NhbWw6QXR0cmlidXRlPjxzYW1sOkF0dHJpYnV0ZSBOYW1l%0D%0APSJTdWJqZWN0LmVEZXNrU3RhdHVzIiBOYW1lRm9ybWF0PSJ1cm46b2FzaXM6bmFt%0D%0AZXM6dGM6U0FNTDoyLjA6YXR0cm5hbWUtZm9ybWF0OmJhc2ljIj48c2FtbDpBdHRy%0D%0AaWJ1dGVWYWx1ZSB4bWxuczp4cz0iaHR0cDovL3d3dy53My5vcmcvMjAwMS9YTUxT%0D%0AY2hlbWEiIHhzaTp0eXBlPSJ4czpzdHJpbmciPkFDVElWRTwvc2FtbDpBdHRyaWJ1%0D%0AdGVWYWx1ZT48L3NhbWw6QXR0cmlidXRlPjxzYW1sOkF0dHJpYnV0ZSBOYW1lPSJB%0D%0AY3Rvci5VUFZTSWRlbnRpdHlJRCIgTmFtZUZvcm1hdD0idXJuOm9hc2lzOm5hbWVz%0D%0AOnRjOlNBTUw6Mi4wOmF0dHJuYW1lLWZvcm1hdDpiYXNpYyI%2BPHNhbWw6QXR0cmli%0D%0AdXRlVmFsdWUgeG1sbnM6eHM9Imh0dHA6Ly93d3cudzMub3JnLzIwMDEvWE1MU2No%0D%0AZW1hIiB4c2k6dHlwZT0ieHM6c3RyaW5nIj5FNENCQjc5MS01QkI5LTQ0NjEtOTcy%0D%0ARi0xOEIzQTEzRkI2NzQ8L3NhbWw6QXR0cmlidXRlVmFsdWU%2BPC9zYW1sOkF0dHJp%0D%0AYnV0ZT48c2FtbDpBdHRyaWJ1dGUgTmFtZT0iQXNzZXJ0aW9uVHlwZSIgTmFtZUZv%0D%0Acm1hdD0idXJuOm9hc2lzOm5hbWVzOnRjOlNBTUw6Mi4wOmF0dHJuYW1lLWZvcm1h%0D%0AdDpiYXNpYyI%2BPHNhbWw6QXR0cmlidXRlVmFsdWUgeG1sbnM6eHM9Imh0dHA6Ly93%0D%0Ad3cudzMub3JnLzIwMDEvWE1MU2NoZW1hIiB4c2k6dHlwZT0ieHM6c3RyaW5nIj5X%0D%0ARUJTU088L3NhbWw6QXR0cmlidXRlVmFsdWU%2BPC9zYW1sOkF0dHJpYnV0ZT48c2Ft%0D%0AbDpBdHRyaWJ1dGUgTmFtZT0iUUFBTGV2ZWwiIE5hbWVGb3JtYXQ9InVybjpvYXNp%0D%0AczpuYW1lczp0YzpTQU1MOjIuMDphdHRybmFtZS1mb3JtYXQ6YmFzaWMiPjxzYW1s%0D%0AOkF0dHJpYnV0ZVZhbHVlIHhtbG5zOnhzPSJodHRwOi8vd3d3LnczLm9yZy8yMDAx%0D%0AL1hNTFNjaGVtYSIgeHNpOnR5cGU9InhzOnN0cmluZyI%2BMTwvc2FtbDpBdHRyaWJ1%0D%0AdGVWYWx1ZT48L3NhbWw6QXR0cmlidXRlPjxzYW1sOkF0dHJpYnV0ZSBOYW1lPSJT%0D%0AdWJqZWN0LklDTyIgTmFtZUZvcm1hdD0idXJuOm9hc2lzOm5hbWVzOnRjOlNBTUw6%0D%0AMi4wOmF0dHJuYW1lLWZvcm1hdDpiYXNpYyI%2BPHNhbWw6QXR0cmlidXRlVmFsdWUg%0D%0AeG1sbnM6eHM9Imh0dHA6Ly93d3cudzMub3JnLzIwMDEvWE1MU2NoZW1hIiB4c2k6%0D%0AdHlwZT0ieHM6c3RyaW5nIi8%2BPC9zYW1sOkF0dHJpYnV0ZT48c2FtbDpBdHRyaWJ1%0D%0AdGUgTmFtZT0iU3ViamVjdElEIiBOYW1lRm9ybWF0PSJ1cm46b2FzaXM6bmFtZXM6%0D%0AdGM6U0FNTDoyLjA6YXR0cm5hbWUtZm9ybWF0OmJhc2ljIj48c2FtbDpBdHRyaWJ1%0D%0AdGVWYWx1ZSB4bWxuczp4cz0iaHR0cDovL3d3dy53My5vcmcvMjAwMS9YTUxTY2hl%0D%0AbWEiIHhzaTp0eXBlPSJ4czpzdHJpbmciPnJjOi8vc2svODMxMTQ0NTk4NF90aXNp%0D%0AY2lfamFua288L3NhbWw6QXR0cmlidXRlVmFsdWU%2BPC9zYW1sOkF0dHJpYnV0ZT48%0D%0Ac2FtbDpBdHRyaWJ1dGUgTmFtZT0iU3ViamVjdC5lRGVza1JlbW90ZVVSSSIgTmFt%0D%0AZUZvcm1hdD0idXJuOm9hc2lzOm5hbWVzOnRjOlNBTUw6Mi4wOmF0dHJuYW1lLWZv%0D%0Acm1hdDpiYXNpYyI%2BPHNhbWw6QXR0cmlidXRlVmFsdWUgeG1sbnM6eHM9Imh0dHA6%0D%0ALy93d3cudzMub3JnLzIwMDEvWE1MU2NoZW1hIiB4c2k6dHlwZT0ieHM6c3RyaW5n%0D%0AIi8%2BPC9zYW1sOkF0dHJpYnV0ZT48c2FtbDpBdHRyaWJ1dGUgTmFtZT0iQWN0b3JJ%0D%0ARCIgTmFtZUZvcm1hdD0idXJuOm9hc2lzOm5hbWVzOnRjOlNBTUw6Mi4wOmF0dHJu%0D%0AYW1lLWZvcm1hdDpiYXNpYyI%2BPHNhbWw6QXR0cmlidXRlVmFsdWUgeG1sbnM6eHM9%0D%0AImh0dHA6Ly93d3cudzMub3JnLzIwMDEvWE1MU2NoZW1hIiB4c2k6dHlwZT0ieHM6%0D%0Ac3RyaW5nIj5yYzovL3NrLzgzMTE0NDU5ODRfdGlzaWNpX2phbmtvPC9zYW1sOkF0%0D%0AdHJpYnV0ZVZhbHVlPjwvc2FtbDpBdHRyaWJ1dGU%2BPHNhbWw6QXR0cmlidXRlIE5h%0D%0AbWU9IkRlbGVnYXRpb25NZWRpYXRvcklEIiBOYW1lRm9ybWF0PSJ1cm46b2FzaXM6%0D%0AbmFtZXM6dGM6U0FNTDoyLjA6YXR0cm5hbWUtZm9ybWF0OmJhc2ljIj48c2FtbDpB%0D%0AdHRyaWJ1dGVWYWx1ZSB4bWxuczp4cz0iaHR0cDovL3d3dy53My5vcmcvMjAwMS9Y%0D%0ATUxTY2hlbWEiIHhzaTp0eXBlPSJ4czpzdHJpbmciLz48L3NhbWw6QXR0cmlidXRl%0D%0APjxzYW1sOkF0dHJpYnV0ZSBOYW1lPSJTdWJqZWN0LklkZW50aXR5VHlwZSIgTmFt%0D%0AZUZvcm1hdD0idXJuOm9hc2lzOm5hbWVzOnRjOlNBTUw6Mi4wOmF0dHJuYW1lLWZv%0D%0Acm1hdDpiYXNpYyI%2BPHNhbWw6QXR0cmlidXRlVmFsdWUgeG1sbnM6eHM9Imh0dHA6%0D%0ALy93d3cudzMub3JnLzIwMDEvWE1MU2NoZW1hIiB4c2k6dHlwZT0ieHM6c3RyaW5n%0D%0AIj4xPC9zYW1sOkF0dHJpYnV0ZVZhbHVlPjwvc2FtbDpBdHRyaWJ1dGU%2BPHNhbWw6%0D%0AQXR0cmlidXRlIE5hbWU9IkFjdG9yLlBDTyIgTmFtZUZvcm1hdD0idXJuOm9hc2lz%0D%0AOm5hbWVzOnRjOlNBTUw6Mi4wOmF0dHJuYW1lLWZvcm1hdDpiYXNpYyI%2BPHNhbWw6%0D%0AQXR0cmlidXRlVmFsdWUgeG1sbnM6eHM9Imh0dHA6Ly93d3cudzMub3JnLzIwMDEv%0D%0AWE1MU2NoZW1hIiB4c2k6dHlwZT0ieHM6c3RyaW5nIj44MzExNDQ1OTg0PC9zYW1s%0D%0AOkF0dHJpYnV0ZVZhbHVlPjwvc2FtbDpBdHRyaWJ1dGU%2BPHNhbWw6QXR0cmlidXRl%0D%0AIE5hbWU9IlN1YmplY3QuVVBWU0lkZW50aXR5SUQiIE5hbWVGb3JtYXQ9InVybjpv%0D%0AYXNpczpuYW1lczp0YzpTQU1MOjIuMDphdHRybmFtZS1mb3JtYXQ6YmFzaWMiPjxz%0D%0AYW1sOkF0dHJpYnV0ZVZhbHVlIHhtbG5zOnhzPSJodHRwOi8vd3d3LnczLm9yZy8y%0D%0AMDAxL1hNTFNjaGVtYSIgeHNpOnR5cGU9InhzOnN0cmluZyI%2BRTRDQkI3OTEtNUJC%0D%0AOS00NDYxLTk3MkYtMThCM0ExM0ZCNjc0PC9zYW1sOkF0dHJpYnV0ZVZhbHVlPjwv%0D%0Ac2FtbDpBdHRyaWJ1dGU%2BPHNhbWw6QXR0cmlidXRlIE5hbWU9IlN1YmplY3QuRmly%0D%0Ac3ROYW1lIiBOYW1lRm9ybWF0PSJ1cm46b2FzaXM6bmFtZXM6dGM6U0FNTDoyLjA6%0D%0AYXR0cm5hbWUtZm9ybWF0OmJhc2ljIj48c2FtbDpBdHRyaWJ1dGVWYWx1ZSB4bWxu%0D%0Aczp4cz0iaHR0cDovL3d3dy53My5vcmcvMjAwMS9YTUxTY2hlbWEiIHhzaTp0eXBl%0D%0APSJ4czpzdHJpbmciPkphbmtvPC9zYW1sOkF0dHJpYnV0ZVZhbHVlPjwvc2FtbDpB%0D%0AdHRyaWJ1dGU%2BPHNhbWw6QXR0cmlidXRlIE5hbWU9IlJvbGVzIiBOYW1lRm9ybWF0%0D%0APSJ1cm46b2FzaXM6bmFtZXM6dGM6U0FNTDoyLjA6YXR0cm5hbWUtZm9ybWF0OmJh%0D%0Ac2ljIj48c2FtbDpBdHRyaWJ1dGVWYWx1ZSB4bWxuczp4cz0iaHR0cDovL3d3dy53%0D%0AMy5vcmcvMjAwMS9YTUxTY2hlbWEiIHhzaTp0eXBlPSJ4czpzdHJpbmciPlJfTURV%0D%0AUlpfUkVBREVSPC9zYW1sOkF0dHJpYnV0ZVZhbHVlPjxzYW1sOkF0dHJpYnV0ZVZh%0D%0AbHVlIHhtbG5zOnhzPSJodHRwOi8vd3d3LnczLm9yZy8yMDAxL1hNTFNjaGVtYSIg%0D%0AeHNpOnR5cGU9InhzOnN0cmluZyI%2BUl9NRFVSWl9XUklURVI8L3NhbWw6QXR0cmli%0D%0AdXRlVmFsdWU%2BPC9zYW1sOkF0dHJpYnV0ZT48c2FtbDpBdHRyaWJ1dGUgTmFtZT0i%0D%0AQWN0b3IuRm9ybWF0dGVkTmFtZSIgTmFtZUZvcm1hdD0idXJuOm9hc2lzOm5hbWVz%0D%0AOnRjOlNBTUw6Mi4wOmF0dHJuYW1lLWZvcm1hdDpiYXNpYyI%2BPHNhbWw6QXR0cmli%0D%0AdXRlVmFsdWUgeG1sbnM6eHM9Imh0dHA6Ly93d3cudzMub3JnLzIwMDEvWE1MU2No%0D%0AZW1hIiB4c2k6dHlwZT0ieHM6c3RyaW5nIj5KYW5rbyBUaXPDrWNpPC9zYW1sOkF0%0D%0AdHJpYnV0ZVZhbHVlPjwvc2FtbDpBdHRyaWJ1dGU%2BPHNhbWw6QXR0cmlidXRlIE5h%0D%0AbWU9IkFjdG9yLlJFSWRlbnRpdHlJZCIgTmFtZUZvcm1hdD0idXJuOm9hc2lzOm5h%0D%0AbWVzOnRjOlNBTUw6Mi4wOmF0dHJuYW1lLWZvcm1hdDpiYXNpYyI%2BPHNhbWw6QXR0%0D%0AcmlidXRlVmFsdWUgeG1sbnM6eHM9Imh0dHA6Ly93d3cudzMub3JnLzIwMDEvWE1M%0D%0AU2NoZW1hIiB4c2k6dHlwZT0ieHM6c3RyaW5nIi8%2BPC9zYW1sOkF0dHJpYnV0ZT48%0D%0Ac2FtbDpBdHRyaWJ1dGUgTmFtZT0iU3ViamVjdC5QQ08iIE5hbWVGb3JtYXQ9InVy%0D%0AbjpvYXNpczpuYW1lczp0YzpTQU1MOjIuMDphdHRybmFtZS1mb3JtYXQ6YmFzaWMi%0D%0APjxzYW1sOkF0dHJpYnV0ZVZhbHVlIHhtbG5zOnhzPSJodHRwOi8vd3d3LnczLm9y%0D%0AZy8yMDAxL1hNTFNjaGVtYSIgeHNpOnR5cGU9InhzOnN0cmluZyI%2BODMxMTQ0NTk4%0D%0ANDwvc2FtbDpBdHRyaWJ1dGVWYWx1ZT48L3NhbWw6QXR0cmlidXRlPjxzYW1sOkF0%0D%0AdHJpYnV0ZSBOYW1lPSJTdWJqZWN0LlByZWZlcnJlZExhbmd1YWdlIiBOYW1lRm9y%0D%0AbWF0PSJ1cm46b2FzaXM6bmFtZXM6dGM6U0FNTDoyLjA6YXR0cm5hbWUtZm9ybWF0%0D%0AOmJhc2ljIj48c2FtbDpBdHRyaWJ1dGVWYWx1ZSB4bWxuczp4cz0iaHR0cDovL3d3%0D%0Ady53My5vcmcvMjAwMS9YTUxTY2hlbWEiIHhzaTp0eXBlPSJ4czpzdHJpbmciLz48%0D%0AL3NhbWw6QXR0cmlidXRlPjxzYW1sOkF0dHJpYnV0ZSBOYW1lPSJTdWJqZWN0Lkxh%0D%0Ac3ROYW1lIiBOYW1lRm9ybWF0PSJ1cm46b2FzaXM6bmFtZXM6dGM6U0FNTDoyLjA6%0D%0AYXR0cm5hbWUtZm9ybWF0OmJhc2ljIj48c2FtbDpBdHRyaWJ1dGVWYWx1ZSB4bWxu%0D%0Aczp4cz0iaHR0cDovL3d3dy53My5vcmcvMjAwMS9YTUxTY2hlbWEiIHhzaTp0eXBl%0D%0APSJ4czpzdHJpbmciPlRpc8OtY2k8L3NhbWw6QXR0cmlidXRlVmFsdWU%2BPC9zYW1s%0D%0AOkF0dHJpYnV0ZT48c2FtbDpBdHRyaWJ1dGUgTmFtZT0iU3ViamVjdC5SRUlkZW50%0D%0AaXR5SWQiIE5hbWVGb3JtYXQ9InVybjpvYXNpczpuYW1lczp0YzpTQU1MOjIuMDph%0D%0AdHRybmFtZS1mb3JtYXQ6YmFzaWMiPjxzYW1sOkF0dHJpYnV0ZVZhbHVlIHhtbG5z%0D%0AOnhzPSJodHRwOi8vd3d3LnczLm9yZy8yMDAxL1hNTFNjaGVtYSIgeHNpOnR5cGU9%0D%0AInhzOnN0cmluZyIvPjwvc2FtbDpBdHRyaWJ1dGU%2BPHNhbWw6QXR0cmlidXRlIE5h%0D%0AbWU9IkFjdG9yLlByZWZlcnJlZExhbmd1YWdlIiBOYW1lRm9ybWF0PSJ1cm46b2Fz%0D%0AaXM6bmFtZXM6dGM6U0FNTDoyLjA6YXR0cm5hbWUtZm9ybWF0OmJhc2ljIj48c2Ft%0D%0AbDpBdHRyaWJ1dGVWYWx1ZSB4bWxuczp4cz0iaHR0cDovL3d3dy53My5vcmcvMjAw%0D%0AMS9YTUxTY2hlbWEiIHhzaTp0eXBlPSJ4czpzdHJpbmciLz48L3NhbWw6QXR0cmli%0D%0AdXRlPjxzYW1sOkF0dHJpYnV0ZSBOYW1lPSJTdWJqZWN0LmVEZXNrTnVtYmVyIiBO%0D%0AYW1lRm9ybWF0PSJ1cm46b2FzaXM6bmFtZXM6dGM6U0FNTDoyLjA6YXR0cm5hbWUt%0D%0AZm9ybWF0OmJhc2ljIj48c2FtbDpBdHRyaWJ1dGVWYWx1ZSB4bWxuczp4cz0iaHR0%0D%0AcDovL3d3dy53My5vcmcvMjAwMS9YTUxTY2hlbWEiIHhzaTp0eXBlPSJ4czpzdHJp%0D%0AbmciPkUwMDAwMDQ1ODIzPC9zYW1sOkF0dHJpYnV0ZVZhbHVlPjwvc2FtbDpBdHRy%0D%0AaWJ1dGU%2BPHNhbWw6QXR0cmlidXRlIE5hbWU9IlN1YmplY3QuRW1haWwiIE5hbWVG%0D%0Ab3JtYXQ9InVybjpvYXNpczpuYW1lczp0YzpTQU1MOjIuMDphdHRybmFtZS1mb3Jt%0D%0AYXQ6YmFzaWMiPjxzYW1sOkF0dHJpYnV0ZVZhbHVlIHhtbG5zOnhzPSJodHRwOi8v%0D%0Ad3d3LnczLm9yZy8yMDAxL1hNTFNjaGVtYSIgeHNpOnR5cGU9InhzOnN0cmluZyIv%0D%0APjwvc2FtbDpBdHRyaWJ1dGU%2BPHNhbWw6QXR0cmlidXRlIE5hbWU9IkFjdG9ySURT%0D%0AZWN0b3IiIE5hbWVGb3JtYXQ9InVybjpvYXNpczpuYW1lczp0YzpTQU1MOjIuMDph%0D%0AdHRybmFtZS1mb3JtYXQ6YmFzaWMiPjxzYW1sOkF0dHJpYnV0ZVZhbHVlIHhtbG5z%0D%0AOnhzPSJodHRwOi8vd3d3LnczLm9yZy8yMDAxL1hNTFNjaGVtYSIgeHNpOnR5cGU9%0D%0AInhzOnN0cmluZyI%2BU0VDVE9SX1VQVlM8L3NhbWw6QXR0cmlidXRlVmFsdWU%2BPC9z%0D%0AYW1sOkF0dHJpYnV0ZT48c2FtbDpBdHRyaWJ1dGUgTmFtZT0iU3ViamVjdC5UZXJt%0D%0AaW5hdGlvbkRhdGUiIE5hbWVGb3JtYXQ9InVybjpvYXNpczpuYW1lczp0YzpTQU1M%0D%0AOjIuMDphdHRybmFtZS1mb3JtYXQ6YmFzaWMiPjxzYW1sOkF0dHJpYnV0ZVZhbHVl%0D%0AIHhtbG5zOnhzPSJodHRwOi8vd3d3LnczLm9yZy8yMDAxL1hNTFNjaGVtYSIgeHNp%0D%0AOnR5cGU9InhzOnN0cmluZyIvPjwvc2FtbDpBdHRyaWJ1dGU%2BPHNhbWw6QXR0cmli%0D%0AdXRlIE5hbWU9IkF1dGhSZXNvdXJjZVN1YlR5cGUiIE5hbWVGb3JtYXQ9InVybjpv%0D%0AYXNpczpuYW1lczp0YzpTQU1MOjIuMDphdHRybmFtZS1mb3JtYXQ6YmFzaWMiPjxz%0D%0AYW1sOkF0dHJpYnV0ZVZhbHVlIHhtbG5zOnhzPSJodHRwOi8vd3d3LnczLm9yZy8y%0D%0AMDAxL1hNTFNjaGVtYSIgeHNpOnR5cGU9InhzOnN0cmluZyIvPjwvc2FtbDpBdHRy%0D%0AaWJ1dGU%2BPHNhbWw6QXR0cmlidXRlIE5hbWU9IkFjdG9yLlVzZXJuYW1lIiBOYW1l%0D%0ARm9ybWF0PSJ1cm46b2FzaXM6bmFtZXM6dGM6U0FNTDoyLjA6YXR0cm5hbWUtZm9y%0D%0AbWF0OmJhc2ljIj48c2FtbDpBdHRyaWJ1dGVWYWx1ZSB4bWxuczp4cz0iaHR0cDov%0D%0AL3d3dy53My5vcmcvMjAwMS9YTUxTY2hlbWEiIHhzaTp0eXBlPSJ4czpzdHJpbmci%0D%0APkUwMDAwMDQ1ODIzPC9zYW1sOkF0dHJpYnV0ZVZhbHVlPjwvc2FtbDpBdHRyaWJ1%0D%0AdGU%2BPHNhbWw6QXR0cmlidXRlIE5hbWU9IkFjdG9yLkVtYWlsIiBOYW1lRm9ybWF0%0D%0APSJ1cm46b2FzaXM6bmFtZXM6dGM6U0FNTDoyLjA6YXR0cm5hbWUtZm9ybWF0OmJh%0D%0Ac2ljIj48c2FtbDpBdHRyaWJ1dGVWYWx1ZSB4bWxuczp4cz0iaHR0cDovL3d3dy53%0D%0AMy5vcmcvMjAwMS9YTUxTY2hlbWEiIHhzaTp0eXBlPSJ4czpzdHJpbmciLz48L3Nh%0D%0AbWw6QXR0cmlidXRlPjwvc2FtbDpBdHRyaWJ1dGVTdGF0ZW1lbnQ%2BPC9zYW1sOkFz%0D%0Ac2VydGlvbj48L3NhbWxwOlJlc3BvbnNlPg%3D%3D%0D%0A&RelayState=%2F'''
}
