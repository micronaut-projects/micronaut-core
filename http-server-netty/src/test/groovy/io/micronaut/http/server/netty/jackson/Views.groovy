package io.micronaut.http.server.netty.jackson

class Views {
    static class Public {}

    static class Internal extends Public {}

    static class Admin extends Internal {}
}
