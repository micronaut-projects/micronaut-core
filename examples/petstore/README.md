# Notes on starting the petstore.

`consul` needs to be running on port 8500.

`mongo` needs to be running on port 27017.

`neo4j` needs to be running on port 7687.

`redis` needs to be running on port 6379.

All of those services may easily be started using the default official
Docker images.  A `docker-compose` config file will be available soon.

Set `NEO4J_AUTH=none` to disable authentication.

`pets` needs to be started before `vendors` and `vendors` needs to 
be started before `storefront`.

```
./gradlew pets:run
```

```
./gradlew vendors:run
```

Once those are running the rest may be started individually or as a group.

```
./gradlew comments:run mail:run offers:run storefront:run --parallel
```

Access `consul` at http://localhost:8500/.

Access the app at http://localhost:3000/.
