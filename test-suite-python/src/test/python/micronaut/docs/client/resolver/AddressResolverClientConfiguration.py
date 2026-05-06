class AddressResolverClientConfiguration:

    # tag::properties[]
    @staticmethod
    def serviceConfiguration() -> dict[str, object]:
        return {
            "micronaut.http.services.foo.urls[0]": "https://api.example.com",  # <1>
            "micronaut.http.services.foo.address-resolver-group-name": "custom",  # <2>
        }
    # end::properties[]
