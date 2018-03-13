# Notes on starting the petstore.

## Running petstore with [docker-compose](https://docs.docker.com/compose/)
* Build the project: 
```
./gradlew build
```
* Build the Docker container images: 
```
docker-compose build
```
* Launch the containers: 
```
docker-compose up -d
```
note: `-d` runs them in the detached mode: it will run containers in the background and print new container names. You can omit this parameter if you wish to run the containers in the foreground
* [Open the petstore](#access-the-petstore)

## DataSources
`consul` needs to be running on port 8500.

`mongo` needs to be running on port 27017.

`neo4j` needs to be running on port 7687.

`redis` needs to be running on port 6379.

Set `NEO4J_AUTH=none` to disable authentication.

If you prefer to run just the data sources via docker-compose run:
 ```
docker-compose up consul mongodb neo4j redis
 ```

## Running the Applications via gradle
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
./gradlew comments:run mail:run offers:run storefront:run frontend:react:start --parallel
```

# Open the petstore Application
Access `consul` at [http://localhost:8500/](http://localhost:8500/).

Access the app at [http://localhost:3000/](http://localhost:3000/).
