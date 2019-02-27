package io.micronaut.docs.websockets

import io.micronaut.context.annotation.Requires
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Produces
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.security.annotation.Secured
import io.micronaut.security.rules.SecurityRule

import javax.annotation.Nullable

@Requires(property = "spec.name", value = "websockets")
@Secured(SecurityRule.IS_ANONYMOUS)
@Controller
class HomeController {

    private final String serverUrl

    HomeController(EmbeddedServer embeddedServer) {
        this.serverUrl = embeddedServer.getHost() + ":" + embeddedServer.getPort();
    }

    @Produces(MediaType.TEXT_HTML)
    @Get
    String index(@Nullable String jwt) {
        """<!DOCTYPE html>
                <html lang="en">
                <head>
                <meta charset="utf-8">
                <title>WebSockets Demo</title>
	<link rel="stylesheet" href="/assets/style.css">
        </head>
<body>
	<div id="page-wrapper">
		<h1>WebSockets Demo</h1>
                <div id="status">Connecting...</div>
		<ul id="messages"></ul>
                <form id="message-form" action="#" method="post">
                <textarea id="message" placeholder="Write your message here..." required></textarea>
			<button type="submit">Send Message</button>
                <button type="button" id="close">Close Connection</button>
		</form>
                </div>
	<script type="application/javascript">
        var serverUrl = "${serverUrl}";
        var jwt = "${jwt}"; 
        </script>
	<script src="/assets/app.js"></script>
        </body>
</html>"""

    }
}
