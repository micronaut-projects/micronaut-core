package io.micronaut.docs.server.functional

// tag::imports[]
import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.server.HttpApplication
import io.micronaut.http.server.netty.NettyEmbeddedServer
import io.micronaut.http.server.netty.configuration.NettyHttpServerConfiguration
// end::imports[]

/**
 * Routes declared in the main method. Each example returns the context, so that the spec can stop it.
 */
class MainApplication {

    // tag::main[]
    static void main(String[] args) {
        start(args)
    }

    static ApplicationContext start(String... args) {
        HttpApplication.run(args) { routes ->
            routes.GET("/hello/{name}") { request, vars -> // <1>
                HttpResponse.ok("Hello " + vars.getString("name")).contentType(MediaType.TEXT_PLAIN_TYPE)
            }
        }
    }
    // end::main[]

    // tag::beans[]
    static ApplicationContext startWithBeans(String... args) {
        HttpApplication.run(args) { routes, beans -> // <1>
            ItemRepository items = beans.getBean(ItemRepository) // <2>
            routes.GET("/items/{id}") { request, vars -> HttpResponse.ok(items.find(vars.getLong("id"))) }
            routes.POST("/items").body(Item).handle { request, vars, Item item -> HttpResponse.created(items.save(item)) }
        }
    }
    // end::beans[]

    // tag::server[]
    static ApplicationContext startOnNetty(String... args) {
        HttpApplication.build(args) // <1>
            .server(NettyEmbeddedServer) // <2>
            .configure(NettyHttpServerConfiguration) { netty -> netty.maxHeaderSize = 16384 } // <3>
            .property("micronaut.server.idle-timeout", "5m") // <4>
            .routes { routes ->
                routes.GET("/ping") { request, vars -> HttpResponse.ok("pong").contentType(MediaType.TEXT_PLAIN_TYPE) }
            }
            .start()
    }
    // end::server[]
}
