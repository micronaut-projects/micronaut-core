# Micronaut CLI

# Building locally

    1. clone Micronaut from github: https://github.com/micronaut-projects/micronaut-core.git
    2. from Micronaut root, run './gradlew cli:pTML' (PublishToMavenLocal)
    3. clone Micronaut profiles from github: https://github.com/micronaut-projects/micronaut-profiles.git
    4. from micronaut-profiles root, run './gradlew pTML'
    5. go back to micronaut root i.e. ~/micronaut-core/cli/build/bin
    6. run ./mn from here - now you are ready!

TIP: You can install the the Micronaut CLI anywhere you want. Simply set the environment variable APP_HOME and copy the jar cli-1.0.0-SNAPSHOT.jar (from build/libs) into that directory. Also copy mn and mn.bat into somewhere in your system path (found in build/bin). Now you can run Micronaut CLI wherever you want!
