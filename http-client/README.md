# Micronaut HTTP Client

This module provides the Micronaut HTTP client API along with a default lightweight implementation.

Default implementation:
- The JDK-based HTTP client (module: http-client-jdk) is now exposed transitively via the API of this module.
- Applications that depend only on "micronaut-http-client" will have a working client by default using the JDK implementation.

Opting into Netty:
- If you require the Netty-based client, add an explicit dependency on "micronaut-http-client-netty" in your application.
- When Netty classes are present, the Netty client beans are enabled via @Requires and will be used automatically.
- You do not need to exclude the JDK client; the environment will select the appropriate implementation.

Migration note:
- If your application previously relied on Netty being pulled in implicitly via "micronaut-http-client", you must now explicitly add "micronaut-http-client-netty" to continue using Netty.
- Keeping only "micronaut-http-client" will use the JDK client by default.

GraalVM:
- The JDK client is reflection-free and is suitable for native images.
