# Test Micronaut and GraalVM

The problem we want to solve is detect regressions in both Micronaut and GraalVM as soon as they are introduced and 
before a new version is released.

## Gitlab CI

We use [Gitlab CI](https://about.gitlab.com/product/continuous-integration/) for running the tests. Travis is not used because of the limitations it creates on memory usage and the lack of functionality for pipeline-like flows.

We could configure the CI pipeline to run the tests for every commit in Micronaut (configuring a webhook), but we don't
have permissions to configure the webhook in GraalVM repository. So, instead of running all the tests for every commit
there is a scheduled job that runs every hour and checks if there are new commits in both repositories. In case there
are new commits, it triggers a CI build.

All the configuration is in [this repository](https://gitlab.com/micronaut-projects/micronaut-graal-tests-scheduler) and
the README explains how it works. We don't need to modify this repository because everything is already setup. The only
change that needs to be done is defining the Micronaut branch we monitor for new commits
[here](https://gitlab.com/micronaut-projects/micronaut-graal-tests-scheduler/blob/master/should-trigger-the-build.sh#L3).

### CI pipeline

The main repository that is contains all the CI configuration and that is triggered is https://gitlab.com/micronaut-projects/micronaut-graal-tests.

The CI pipeline is configured in four stages:

- `log-commits`: It only contains one job that logs the previous Micronaut and Graal commits and the new that triggered
the build.
- `build-graal`: One job that clones GraalVM repository and builds it from source code. 
- `micronaut`: It contains one job per test application. These are Micronaut applications build with GraalVM that we
know are compatible and work with GraalVM. Every job builds the native-image for the associated application.
- `test`: There is one job per test application. Every job starts the native-image created in the previous stage, run
some functional tests and checks the result.

#### Log commits stage

```yaml
log-commits:
  image: alpine:3.8
  stage: log-commits
  script:
    - ./log-commits.sh # <1>
```
1.- Execute the script to log the commits. An example of the job execution can be found
[here](https://gitlab.com/micronaut-projects/micronaut-graal-tests/-/jobs/353511757).

#### Build Graal stage

```yaml
build-graal:
  stage: build-graal
  cache:
    key: ${GRAAL_LAST_COMMIT} # <1>
    paths:
      - $CI_PROJECT_DIR/graal/graal/sdk # <1>
      - $CI_PROJECT_DIR/graal/graal/vm # <1>
  artifacts:
    expire_in: 5 days # <2>
    paths:
      - $CI_PROJECT_DIR/graal/graal/sdk # <3>
      - $CI_PROJECT_DIR/graal/graal/vm # <3>
  script:
    - if [ -d $CI_PROJECT_DIR/graal ]; then exit 0; fi # <4>
    - ./build-graal.sh # <5>
```
1.- Use the GraalVM commit as a cache key. This allows to reuse the same GraalVM built from source code if it hasn't
change. The `paths` defined are the output of GraalVM compilation.   
2.- Define an `artifact` that is passed to the next stages and it expires automatically after 5 days. Gitlab CI will
save it and the artifact will be available for the jobs in the next stage. With this the JDK we just built is available
to create Micronaut native-images.  
3.- The `paths` for the artifact are the same as in the `cache`.  
4.- If the cache is present then exit the built and let the rest of the pipeline continue.  
5.- If not, build GraalVM from source code.  


#### Micronaut Stage

The structure of all the jobs in this stage is the same:

```yaml
.micronaut:build-template: &micronaut-build-template  # <1>
  stage: micronaut
  artifacts:
    expire_in: 5 days
  allow_failure: true
  retry:
    max: 2
    when:
      - runner_system_failure

micronaut:build-basic-app:
  <<: *micronaut-build-template # <2>
  artifacts:
    paths:
      - $CI_PROJECT_DIR/micronaut-basic-app/basic-app # <3>
  script:
    - ./build-micronaut-basic-app.sh # <4>
```
1.- Common template for all jobs in `micronaut` stage.   
2.- Apply the common template.  
3.- Define the native-image executable as an `artifact` so it is available in the next stage for the test.  
4.- Execute the script to build the native-image for the test application.

#### Test stage

```yaml
.micronaut:test-template: &micronaut-test-template # <1>
  stage: test
  image: frolvlad/alpine-glibc # <2>

micronaut:test-basic-app:
  <<: *micronaut-test-template # <3>
  dependencies:
    - micronaut:build-basic-app # <4>
  script:
    - ./test-basic-app.sh # <5>
```
1.- Common template for all jobs in `test` stage.  
2.- We use `frolvlad/alpine-glibc` Docker image to run the native-image applications.  
3.- Apply the common template.  
4.- This job depends on the previous one in the `micronaut` stage.  
5.- Execute the script to run the functional tests.

#### More information

For more information about Gitlab CI see https://docs.gitlab.com/ee/ci/.


### Test applications

The tests applications are in the Github organization [micronaut-graal-tests](https://github.com/micronaut-graal-tests).
All the applications have the same structure and they test different Micronaut integrations that we know work properly
with GraalVM.
All the test applications:
- Use the latest snapshot Micronaut version. It's important that this version is for the same branch defined in
["scheduler" repository](https://gitlab.com/micronaut-projects/micronaut-graal-tests-scheduler/blob/master/should-trigger-the-build.sh#L3).
Check [this](https://github.com/micronaut-graal-tests/micronaut-basic-app/blob/master/gradle.properties#L1).
- Provide a `build-native-image.sh` script to create the native image. This script is executed in the `micronaut` stage.
More info [here](https://github.com/micronaut-graal-tests/micronaut-basic-app/blob/master/build-native-image.sh).


## Add a new Micronaut test application

These are the steps to add a new Micronaut-GraalVM test application to the pipeline:

- Create the new test application reposiory in https://github.com/micronaut-graal-tests. Make sure that it uses the
latest Micronaut.
snapshot and it provides the script `build-native-image.sh` as explained before.
- Add a README with the curl endpoints needed to test the application.
- Create a new branch in https://github.com/micronaut-graal-tests and modify `gitlab-ci.yml` file:
    - Add the jobs for the new application in `micronaut` and `test` stages.
    - Create the scripts to build the native-image for the micronaut application and for the tests. If the application
    needs a Docker dependency make sure it is configured properly and there is an specific `application.yml` file with
    the configuration. Check [gitlab-ci.yml configuration](https://gitlab.com/micronaut-projects/micronaut-graal-tests/blob/198e827338a99a06bf89ce06bafdcf72183b9663/.gitlab-ci.yml#L230-232)
    and an example, for example for [RabbitMQ](https://gitlab.com/micronaut-projects/micronaut-graal-tests/blob/198e827338a99a06bf89ce06bafdcf72183b9663/application-micronaut-rabbitmq-graal.yml).
    - Comment out the other jobs in those stages so they are not executed and we don't waste time running them.
    - Modify the GraalVM cache key with a value of a previous run. We do this to avoid building GraalVM for this
     particular branch and again waste time on something that we now it works.
    - Push the branch and wait for the build.
    - If it fails fix it.
    - Once it pass cherry-pick the commit that adds the new application into `master` branch, push it and delete the
    branch created for the application.
