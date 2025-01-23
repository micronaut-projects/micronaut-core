package io.micronaut.http.client.netty

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.core.convert.ConversionService
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.BlockingHttpClient
import io.micronaut.http.client.HttpClient
import io.micronaut.http.cookie.Cookie
import io.micronaut.http.cookie.Cookies
import io.micronaut.runtime.server.EmbeddedServer
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.handler.codec.http.DefaultFullHttpResponse
import io.netty.handler.codec.http.DefaultHttpHeaders
import io.netty.handler.codec.http.FullHttpResponse
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpHeaders
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpVersion
import jakarta.inject.Singleton
import java.nio.charset.Charset
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import spock.lang.Specification

class FullNettyClientHttpResponseSpec extends Specification {

    void "test cookies"() {
        given:
          String cookieDef = "simple-cookie=avalue; max-age=60; path=/; domain=.micronaut.io"
          HttpHeaders httpHeaders = new DefaultHttpHeaders(false)
                  .add(HttpHeaderNames.SET_COOKIE, cookieDef)
          FullHttpResponse fullHttpResponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)
          fullHttpResponse.headers().set(httpHeaders)

        when:
          var response = new FullNettyClientHttpResponse(fullHttpResponse, null, null, false, ConversionService.SHARED)

        then:
            Cookies cookies = response.getCookies()
            cookies != null
            cookies.size() == 1
            cookies.contains("simple-cookie")
            Optional<Cookie> oCookie = response.getCookie("simple-cookie")
            oCookie.isPresent()
            oCookie.get().getValue() == "avalue"
    }

    void "test multiple cookie headers"() {
        FullHttpResponse fullHttpResponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)
        HttpHeaders httpHeaders = new DefaultHttpHeaders(false)
            .add(HttpHeaderNames.SET_COOKIE, "enctp=1;Domain=.xxx.xxx.com;Expires=Sat, 09-Jan-2021 17:32:47 GMT;Max-Age=7776000")
            .add(HttpHeaderNames.SET_COOKIE, "inf=123456; path=/; domain=.xxx.com;")
            .add(HttpHeaderNames.SET_COOKIE, "AUT=aaaabbbbcccc; path=/; domain=.xxx.com; HttpOnly")
            .add(HttpHeaderNames.SET_COOKIE, "SES=abcdabcd; path=/; domain=.xxx.com;")
            .add(HttpHeaderNames.SET_COOKIE, "JKL=abcdaaaa=; path=/; domain=.xxx.com; Secure;")
        fullHttpResponse.headers().set(httpHeaders)

        when:
        var response = new FullNettyClientHttpResponse(fullHttpResponse, null, null, false, ConversionService.SHARED)

        then:
        Cookies cookies = response.getCookies()
        cookies != null
        cookies.size() == 5
        cookies.get("enctp").maxAge == 7776000
        cookies.get("enctp").value == "1"
        cookies.get("inf").path == "/"
        cookies.get("inf").domain == ".xxx.com"
        cookies.get("AUT").httpOnly
        cookies.get("SES").value == "abcdabcd"
        cookies.get("SES").path == "/"
        cookies.get("JKL").secure
        cookies.get("JKL").domain == ".xxx.com"
    }

    void "test multiple responses can be created with the same body content"() {
        given:
        ByteBuf content = Unpooled.copiedBuffer("foo bar", Charset.defaultCharset())
        FullHttpResponse fullHttpResponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, content)

        when:
        var response1 = new FullNettyClientHttpResponse(fullHttpResponse, null, null, false, ConversionService.SHARED)
        var response2 = new FullNettyClientHttpResponse(fullHttpResponse, null, null, false, ConversionService.SHARED)

        then:
        response1.getBody(String.class).get() == "foo bar"
        response2.getBody(String.class).get() == "foo bar"
    }

    void "test concurrency FullNettyClientHttpResponse"() {

        given:
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, ['spec.name': FullNettyClientHttpResponseSpec.simpleName])
        ApplicationContext ctx = server.applicationContext
        BlockingHttpClient client = ctx.createBean(HttpClient, server.URL).toBlocking()

        when:

        var threadsCount = 10
        ExecutorService executor = Executors.newFixedThreadPool(threadsCount)
        ExecutorService executor2 = Executors.newScheduledThreadPool(threadsCount)

        var concurrentExceptionWasThrown = false
        var tasks = new ArrayList<Callable<?>>()

        for (var i = 0; i < 100; i++) {
            tasks.add(Executors.callable {
                if (concurrentExceptionWasThrown) {
                    return null
                }
                var requestTasks = new ArrayList<Callable<?>>()
                var response = client.exchange(HttpRequest.GET("/someGet"), byte[].class) as FullNettyClientHttpResponse<byte[]>
                for (var j = 0; j < 100; j++) {
                    requestTasks.add(Executors.callable {
                        if (concurrentExceptionWasThrown) {
                            return null
                        }
                        response.getBody(String.class).orElse(null)
                    })
                    requestTasks.add(Executors.callable {
                        if (concurrentExceptionWasThrown) {
                            return null
                        }
                        response.getBody(byte[].class).orElse(null)
                    })
                }

                try {
                    var futures = executor2.invokeAll(requestTasks);
                    for (var future : futures) {
                        future.get();
                    }
                } catch (Exception e) {
                    println("Something is wrong: ${e.message}, ${e.class.simpleName}")
                    concurrentExceptionWasThrown = true
                }
            })
        }
        executor.invokeAll(tasks)
        executor.shutdown()
        try {
            if (!executor.awaitTermination(800, TimeUnit.MILLISECONDS)) {
                executor.shutdownNow()
            }
        } catch (InterruptedException e) {
            executor.shutdownNow()
        }

        then:
        !concurrentExceptionWasThrown
    }

    @Requires(property = "spec.name", value = "FullNettyClientHttpResponseSpec")
    @Controller
    @Singleton
    static class ExampleController {

        @Get("/someGet")
        HttpResponse<String> exampleGet() {
            // autogenerated json
            return HttpResponse.ok("""
            [
              {
                "_id": "673df45fc80f6ab432122195",
                "index": 0,
                "guid": "b7cbafe6-a620-4e72-8f52-275bbd51a1b6",
                "isActive": false,
                "balance": "${'$'}2,889.56",
                "picture": "http://placehold.it/32x32",
                "age": 27,
                "eyeColor": "blue",
                "name": "Cote Weiss",
                "gender": "male",
                "company": "PHARMEX",
                "email": "coteweiss@pharmex.com",
                "phone": "+1 (921) 443-2881",
                "address": "225 Losee Terrace, Tryon, Missouri, 2789",
                "about": "Cillum consequat amet non ipsum nostrud est eu dolor voluptate minim. Enim velit sint exercitation labore do incididunt anim aute dolor quis consectetur tempor ex est. Irure nostrud cupidatat sint duis excepteur aliqua eiusmod cillum laboris eiusmod mollit deserunt elit. Tempor voluptate duis ex esse eu veniam et deserunt in amet id cupidatat do laboris. Duis quis ullamco quis voluptate duis in in exercitation. Cupidatat sunt aliqua in consectetur sint minim anim ut.\r\n",
                "registered": "2014-08-26T05:18:29 -02:00",
                "latitude": -65.593812,
                "longitude": 123.085155,
                "tags": [
                  "non",
                  "duis",
                  "ipsum",
                  "id",
                  "ea",
                  "anim",
                  "officia"
                ],
                "friends": [
                  {
                    "id": 0,
                    "name": "Janie Gross"
                  },
                  {
                    "id": 1,
                    "name": "Latasha Sykes"
                  },
                  {
                    "id": 2,
                    "name": "Summer Pruitt"
                  }
                ],
                "greeting": "Hello, Cote Weiss! You have 6 unread messages.",
                "favoriteFruit": "strawberry"
              },
              {
                "_id": "673df45fd0cd418c67455600",
                "index": 1,
                "guid": "639c4cfa-7875-4170-9045-1391ddeed7ca",
                "isActive": false,
                "balance": "${'$'}1,429.26",
                "picture": "http://placehold.it/32x32",
                "age": 40,
                "eyeColor": "green",
                "name": "Ebony Mcknight",
                "gender": "female",
                "company": "OPTICALL",
                "email": "ebonymcknight@opticall.com",
                "phone": "+1 (902) 484-2716",
                "address": "617 Bayview Place, Sterling, North Carolina, 7858",
                "about": "Non eiusmod ullamco minim et eu eiusmod ut duis laborum laborum. Pariatur eu exercitation est voluptate eu enim in sit aliquip ad deserunt. Cillum officia mollit incididunt labore irure tempor officia eiusmod duis ex ullamco est culpa. Nostrud culpa veniam ipsum pariatur nostrud occaecat ipsum anim deserunt amet adipisicing duis elit fugiat. Pariatur aliquip eiusmod dolor dolore est ad qui eu incididunt excepteur laborum. Aliquip elit tempor enim laborum qui mollit velit qui sit quis veniam. Magna laboris quis sint amet fugiat.\r\n",
                "registered": "2014-06-15T10:16:47 -02:00",
                "latitude": -15.512737,
                "longitude": -111.397878,
                "tags": [
                  "commodo",
                  "Lorem",
                  "dolore",
                  "dolore",
                  "exercitation",
                  "amet",
                  "irure"
                ],
                "friends": [
                  {
                    "id": 0,
                    "name": "Alicia Johnston"
                  },
                  {
                    "id": 1,
                    "name": "Blanche Gilliam"
                  },
                  {
                    "id": 2,
                    "name": "Jessica Fry"
                  }
                ],
                "greeting": "Hello, Ebony Mcknight! You have 10 unread messages.",
                "favoriteFruit": "banana"
              },
              {
                "_id": "673df45f18a35a5f44b04309",
                "index": 2,
                "guid": "614168a3-4586-4d90-be8f-8be6d6448ba3",
                "isActive": false,
                "balance": "${'$'}1,690.57",
                "picture": "http://placehold.it/32x32",
                "age": 38,
                "eyeColor": "blue",
                "name": "Florine Patton",
                "gender": "female",
                "company": "QOT",
                "email": "florinepatton@qot.com",
                "phone": "+1 (954) 450-3766",
                "address": "536 Charles Place, Franklin, California, 7005",
                "about": "Duis laboris ex aute ipsum laborum amet ad elit irure mollit aliqua eiusmod duis elit. Reprehenderit duis veniam ullamco Lorem culpa sit labore excepteur elit occaecat eiusmod est culpa incididunt. Pariatur sint mollit voluptate amet magna do reprehenderit consectetur eiusmod. Anim velit aliquip do ut aliquip ipsum deserunt et. Pariatur reprehenderit excepteur cupidatat quis et duis aliquip ipsum laboris anim aliquip anim magna eiusmod.\r\n",
                "registered": "2016-02-02T01:58:35 -01:00",
                "latitude": 29.255451,
                "longitude": -104.598785,
                "tags": [
                  "magna",
                  "pariatur",
                  "Lorem",
                  "consectetur",
                  "sunt",
                  "est",
                  "nulla"
                ],
                "friends": [
                  {
                    "id": 0,
                    "name": "Carver Harmon"
                  },
                  {
                    "id": 1,
                    "name": "Rollins Wong"
                  },
                  {
                    "id": 2,
                    "name": "Shelby Livingston"
                  }
                ],
                "greeting": "Hello, Florine Patton! You have 5 unread messages.",
                "favoriteFruit": "apple"
              },
              {
                "_id": "673df45fa3dfad9948af8878",
                "index": 3,
                "guid": "d9a0f891-c442-4f56-ad21-aeeae43dd040",
                "isActive": false,
                "balance": "${'$'}1,340.69",
                "picture": "http://placehold.it/32x32",
                "age": 30,
                "eyeColor": "green",
                "name": "Madeleine Moran",
                "gender": "female",
                "company": "ILLUMITY",
                "email": "madeleinemoran@illumity.com",
                "phone": "+1 (818) 408-2235",
                "address": "494 Powers Street, Williston, Mississippi, 5041",
                "about": "Esse sunt sit deserunt magna anim amet. Anim ut elit sint fugiat quis ad. Et dolore tempor laboris ad ad fugiat elit.\r\n",
                "registered": "2020-03-07T10:07:24 -01:00",
                "latitude": 89.045778,
                "longitude": 17.840976,
                "tags": [
                  "consectetur",
                  "adipisicing",
                  "enim",
                  "mollit",
                  "do",
                  "dolor",
                  "irure"
                ],
                "friends": [
                  {
                    "id": 0,
                    "name": "Thornton Brady"
                  },
                  {
                    "id": 1,
                    "name": "Ashley Newman"
                  },
                  {
                    "id": 2,
                    "name": "Terra Downs"
                  }
                ],
                "greeting": "Hello, Madeleine Moran! You have 8 unread messages.",
                "favoriteFruit": "banana"
              },
              {
                "_id": "673df45ff83ad1120ffa1a83",
                "index": 4,
                "guid": "20935d40-8069-464d-8497-5a69b72451ae",
                "isActive": false,
                "balance": "${'$'}1,154.89",
                "picture": "http://placehold.it/32x32",
                "age": 33,
                "eyeColor": "brown",
                "name": "Sanchez Mullins",
                "gender": "male",
                "company": "XLEEN",
                "email": "sanchezmullins@xleen.com",
                "phone": "+1 (891) 502-2082",
                "address": "963 Pierrepont Street, Bangor, Minnesota, 4386",
                "about": "Adipisicing magna amet eiusmod veniam amet laboris excepteur. Deserunt amet consectetur deserunt irure culpa. Occaecat nostrud labore excepteur sint sint. Nulla quis ut cillum consectetur eu et consectetur duis.\r\n",
                "registered": "2014-01-26T09:17:34 -01:00",
                "latitude": 45.081551,
                "longitude": -21.080718,
                "tags": [
                  "magna",
                  "non",
                  "id",
                  "nisi",
                  "eu",
                  "labore",
                  "cupidatat"
                ],
                "friends": [
                  {
                    "id": 0,
                    "name": "Billie Parrish"
                  },
                  {
                    "id": 1,
                    "name": "Ortiz Lester"
                  },
                  {
                    "id": 2,
                    "name": "Stacy Sargent"
                  }
                ],
                "greeting": "Hello, Sanchez Mullins! You have 9 unread messages.",
                "favoriteFruit": "banana"
              },
              {
                "_id": "673df45f661c0664c5c7cc25",
                "index": 5,
                "guid": "35a84917-3421-4c7d-b350-87adebb1d2ee",
                "isActive": true,
                "balance": "${'$'}1,414.77",
                "picture": "http://placehold.it/32x32",
                "age": 33,
                "eyeColor": "green",
                "name": "Preston Lowe",
                "gender": "male",
                "company": "UBERLUX",
                "email": "prestonlowe@uberlux.com",
                "phone": "+1 (965) 526-2168",
                "address": "442 Beach Place, Bluffview, Idaho, 2698",
                "about": "Laboris tempor non ullamco sit sit voluptate eu deserunt id nostrud ad velit occaecat sit. Laboris fugiat eu veniam duis dolor do excepteur. Aliquip sunt id laborum do.\r\n",
                "registered": "2019-12-28T08:36:12 -01:00",
                "latitude": 30.586004,
                "longitude": 76.748238,
                "tags": [
                  "cupidatat",
                  "qui",
                  "laborum",
                  "et",
                  "et",
                  "ut",
                  "tempor"
                ],
                "friends": [
                  {
                    "id": 0,
                    "name": "Greene Mcfadden"
                  },
                  {
                    "id": 1,
                    "name": "Tate Schneider"
                  },
                  {
                    "id": 2,
                    "name": "Nola Baxter"
                  }
                ],
                "greeting": "Hello, Preston Lowe! You have 4 unread messages.",
                "favoriteFruit": "apple"
              }
            ]
        """.stripIndent())
        }
    }
}

