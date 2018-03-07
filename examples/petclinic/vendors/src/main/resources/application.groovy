micronaut.application.name="vendors"
consul.client.defaultZone='${CONSUL_HOST:localhost}:${CONSUL_PORT:8500}'
vendors.api.version="v1"
hibernate {
    hbm2ddl {
        auto = "create-drop"
    }
}