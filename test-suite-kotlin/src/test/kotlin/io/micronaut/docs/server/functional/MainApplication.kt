package io.micronaut.docs.server.functional

// tag::imports[]
import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.server.HttpApplication
import io.micronaut.http.server.netty.NettyEmbeddedServer
import io.micronaut.http.server.netty.configuration.NettyHttpServerConfiguration
// end::imports[]

// Routes declared in the main function. Each example returns the context, so that the test can stop it.

// tag::main[]
fun main(args: Array<String>) {
    start(*args)
}

fun start(vararg args: String): ApplicationContext =
    HttpApplication.run(arrayOf(*args)) { routes ->
        routes.GET("/hello/{name}") { request, vars -> // <1>
            HttpResponse.ok("Hello " + vars.getString("name")).contentType(MediaType.TEXT_PLAIN_TYPE)
        }
    }
// end::main[]

// tag::beans[]
fun startWithBeans(vararg args: String): ApplicationContext =
    HttpApplication.run(arrayOf(*args)) { routes, beans -> // <1>
        val items = beans.getBean(ItemRepository::class.java) // <2>
        routes.GET("/items/{id}") { request, vars -> HttpResponse.ok(items.find(vars.getLong("id"))) }
        routes.POST("/items").body(Item::class.java).handle { request, vars, item -> HttpResponse.created(items.save(item)) }
    }
// end::beans[]

// tag::server[]
fun startOnNetty(vararg args: String): ApplicationContext =
    HttpApplication.build(*args) // <1>
        .server(NettyEmbeddedServer::class.java) // <2>
        .configure(NettyHttpServerConfiguration::class.java) { netty -> netty.maxHeaderSize = 16384 } // <3>
        .property("micronaut.server.idle-timeout", "5m") // <4>
        .routes { routes ->
            routes.GET("/ping") { request, vars -> HttpResponse.ok("pong").contentType(MediaType.TEXT_PLAIN_TYPE) }
        }
        .start()
// end::server[]
