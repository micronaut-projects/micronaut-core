# Test Micronaut and GraalVM

The problem we want to solve is detect regressions in both Micronaut and GraalVM as soon as they are introduced and
before a new version is released.

## Gitlab CI

We use [Gitlab CI](https://about.gitlab.com/product/continuous-integration/) for running the tests.

We could configure the CI pipeline to run the tests for every commit in Micronaut (configuring a webhook), but we don't
have permissions to configure the webhook in GraalVM repository. Also, running all the test suite takes about 30 minutes,
and it uses a lot of resources. Instead of running all the tests for every commit there are scheduled jobs that run
them every work day if there are new commits in either Micronaut or GraalVM repositories. When that happens, the jobs
trigger a CI build.

### Projects

There are two repositories:

- https://gitlab.com/micronaut-projects/micronaut-graal-tests: The CI pipeline that runs all the tests is defined here.
- https://gitlab.com/micronaut-projects/micronaut-graal-tests-scheduler: Additional repository that keeps track of the commits already processed. The scheduled jobs are configured here, and they are used to trigger the jobs in the previous repository.

### Branch structure

It is possible to test different Micronaut and GraalVM versions. For Micronaut, we always test the current stable version
and the next one (using snapshots in both cases).
In the case of GraalVM we can test up to three different branches at the same time: stable, prerelease and development,
and we always test at least two of them.

Currently, we have:

- Micronaut: `3.2.x-SNAPSHOT` and `3.3.0-SNAPSHOT`
- GraalVM: Stable (`21.3.0`), prerelease (`22.0.0-dev`) and development (`22.1.0-dev`)

These are the current branches:
- `3.2.x-stable`: Micronaut `3.2.x-SNAPSHOT` and GraalVM `21.3.0`. To test that current stable versions of Micronaut work properly with current GraalVM stable version.
- `3.3.x-stable`: Micronaut `3.3.x-SNAPSHOT` and GraalVM `21.3.0`. To test that next Micronaut version works with current GraalVM stable version.
- `3.3.x-prerelease`: Micronaut `3.3.x-SNAPSHOT` and GraalVM `22.0.0-dev` from the prerelease branch. To test the next Micronaut version works with the next GraalVM from the prerelease branch.
- `3.3.x-dev`: Micronaut `3.3.x-SNAPSHOT` and GraalVM `22.1.0-dev` from master branch. To test the next Micronaut version works with the future GraalVM version.

When GraalVM `22.0.0-dev` becomes the next stable version, i.e, `22.0.0` the new branches should be:

- `3.3.x-stable`: Micronaut `3.3.x-SNAPSHOT` and GraalVM `21.2.0`.
- `3.3.x-dev`: Micronaut `3.3.x-SNAPSHOT` and GraalVM `22.1.0-dev` from master branch
- `3.4.x-dev`: Micronaut `3.4.x-SNAPSHOT` and GraalVM `22.1.0-dev` from master branch

Then, approximately a month before the next GraalVM version scheduled for [April 19th 2022](https://www.graalvm.org/release-notes/version-roadmap/)
the GraalVM team will create the next prerelease branch `release/graal-vm/22.1` and we need to create the new branch and a
new scheduled job.

All the configuration is in [this repository](https://gitlab.com/micronaut-projects/micronaut-graal-tests-scheduler) and
the `README` explains how it works. The only thing that we need to modify is the file `should-trigger-the-build.sh` to
use the appropriate Micronaut and GraalVM branches.

**Important:** When adding commits to more than one branch, **always** cherry-pick the changes instead of merging branches. It helps a lot of keeping the branches "clean" without merges.

### CI pipeline

The CI pipeline is configured in four stages:

- `log-commits`: It only contains one job that logs the previous Micronaut and GraalVM commits and the new that triggered the build.
- `build-graal`: One job per JDK version (currently 11 and 17) that clones GraalVM repository and builds it from source code. For the GraalVM stable versions it just downloads it.
- `micronaut`: It contains one job per test application and JDK version. These are Micronaut applications built with GraalVM that we know are compatible and work with GraalVM. Every job builds the native-image for the associated application.
- `test`: There is one job per test application and JDK version. Every job starts the native-image created in the previous stage, run some functional tests and checks the result.

#### Log commits stage

```yaml
log-commits:
  image: alpine:3.8
  stage: log-commits
  script:
    - ./log-commits.sh # <1>
```
- Executes the script to log the commits. An example of the job execution can be found
[here](https://gitlab.com/micronaut-projects/micronaut-graal-tests/-/jobs/1950176930).

#### Build Graal stage

```yaml
.build-graalvm:template: &build-graalvm-template # <1>
  stage: build-graalvm
  dependencies:
    - log-commits
  needs: ["log-commits"]
  artifacts:
    expire_in: 5 days
    paths:
      - $CI_PROJECT_DIR/graal_dist # <3>
  cache:
    key: ${GRAAL_NEW_COMMIT}-${CI_JOB_NAME} # <2>
    paths:
      - $CI_PROJECT_DIR/graal_dist # <2>
  tags: # <4>
    - aws
    - speed2x
    - memory2x

jdk11:build-graalvm:
  <<: *build-graalvm-template # <5>
  script:
    - if [ -d $CI_PROJECT_DIR/graal_dist ]; then exit 0; fi # <6>
    - ./build-graalvm.sh jdk11 # <7>

jdk17:build-graalvm:
  <<: *build-graalvm-template
  script:
    - if [ -d $CI_PROJECT_DIR/graal_dist ]; then exit 0; fi
    - ./build-graalvm.sh jdk17
```

1. Any job starting with a dot is hidden and won't be executed. In this case this line also defines a template named `build-graalvm-template` that other jobs can extend from.
2. Use GraalVM last commit id and the job name as the cache key. This allows to reuse the same GraalVM build from source code if it hasn't changed. The `paths` option defines the output of the GraalVM compilation and that is what will be cached.
3. The `artifact` path defines all the files that are passed automatically to the next stage in the pipeline. They expire automatically (meaning they are removed) after 5 days. Gitlab CI will save the artifacts and download them automatically in the next stage. With this configuration the GraalVM SDK we just built is available to create Micronaut native-images.
4. Tags for this job. Jobs without tags run on Gitlab CI shared runners. All jobs with `aws` tag run on our own custom runners on AWS. Additionally, there are more tags necessary to define in which instance type the job is run. The tags will be explained bellow.
5. Extend from the template and add more configuration.
6. If the cache is present then exit the build and let the rest of the pipeline continue.
7. If not, build GraalVM from source code.



#### Micronaut Stage

The structure of all the jobs in this stage is the same:

```yaml
.micronaut:build-template: &micronaut-build-template # <1>
  stage: micronaut
  image: registry.gitlab.com/micronaut-projects/micronaut-graal-tests/graalvm-builder # <2>
  before_script:
    - export APP_BRANCH=$(echo $CI_BUILD_REF_NAME | sed "s/-dev//" | sed "s/-stable//" | sed "s/-prerelease//") # <3>
  artifacts:
    expire_in: 5 days
  allow_failure: true # <4>
  retry:
    max: 2 # <5>
    when:
      - always

.jdk11:micronaut-build: &jdk11-build # <6>
  <<: *micronaut-build-template
  dependencies:
    - jdk11:build-graalvm
  needs: ["jdk11:build-graalvm"]

.jdk17:micronaut-build: &jdk17-build
  <<: *micronaut-build-template
  dependencies:
    - jdk17:build-graalvm
  needs: ["jdk17:build-graalvm"]

jdk11:basic-app:micronaut-build:
  <<: *jdk11-build # <7>
  artifacts:
    paths:
      - $CI_PROJECT_DIR/micronaut-basic-app/basic-app # <7>
  script:
    - ./build-basic-app.sh # <8>
  tags: # <9>
    - aws
    - speed

jdk17:basic-app:micronaut-build:
  <<: *jdk17-build
  artifacts:
    paths:
      - $CI_PROJECT_DIR/micronaut-basic-app/basic-app
  script:
    - ./build-basic-app.sh
  tags:
    - aws
    - speed
```
1. Common parent template for all jobs in `micronaut` stage.
2. Use a custom [Docker image](https://gitlab.com/micronaut-projects/micronaut-graal-tests/-/tree/3.3.x-dev/docker) for building the native image based on the official GraalVM docker.
3. Remove the suffixes `-dev`,  `-stable` and `-prerelease` from the current branch. The environment variable `APP_BRANCH` is used in every test application build script to check out the appropriate git branch.
4. Allow failures for the jobs in this stage. We don't want that a failure here stops the execution of the rest of the jobs.
5. Retry if there is an error. Sometimes this happens for connectivity issues when downloading the dependencies.
6. Template for building JDK11 applications. Extends from the parent one.
7. Define the artifact that will be saved and passed to the next stage. This is the name of the native image that Micronaut creates.
8. Run the script to build the native image for the specific application.
9. Run the job on custom AWS runners.


#### Test stage

```yaml
.micronaut:test-template: &micronaut-test-template # <1>
  stage: test
  image: frolvlad/alpine-glibc:alpine-3.12 # <2>
  before_script:
    - ./test-before-script.sh # <3>
  timeout: 20m
  retry:
    max: 1

.micronaut:test-distroless-template: &micronaut-test-distroless-template # <4>
  <<: *micronaut-test-template
  image:
    name: gcr.io/distroless/cc-debian10:debug # <5>
    entrypoint: [ "" ]
  before_script:
    - ./test-before-script-distroless.sh # <6>

jdk11:basic-app:test:
  <<: *micronaut-test-distroless-template # <7>
  dependencies:
    - jdk11:basic-app:micronaut-build
  needs: ["jdk11:basic-app:micronaut-build"]
  script:
    - ./test-basic-app.sh # <8>

jdk17:basic-app:test:
  <<: *micronaut-test-distroless-template
  dependencies:
    - jdk17:basic-app:micronaut-build
  needs: ["jdk17:basic-app:micronaut-build"]
  script:
    - ./test-basic-app.sh
```
1. Common parent template for all jobs in `test` stage that build dynamic native images.
2. We use `frolvlad/alpine-glibc:3.12` Docker image to run the native-image applications.
3. Script with common dependencies for all tests: `curl`, `jq` and `libstdc++`.
4. Common parent template for all jobs in `test` stage that build "mostly static" native images.
5. Distroless Docker image for "mostly static" native images.
6. Script that downloads `curl` and `jq`.
7. `basic-app` is generated as "mostly static" native image, so apply that parent template.
8. Run the tests.

### More information

For more information about Gitlab CI see https://docs.gitlab.com/ee/ci/.


### CI Execution

There is a channel in OCI's chat named `micronaut-graal-tests` that gets notifications when jobs are executed.


## Test applications

The tests applications are in the GitHub organization [micronaut-graal-tests](https://github.com/micronaut-graal-tests).
All the applications have similar structure, and they test different Micronaut integrations that we know work properly
with GraalVM.

There are two different branch names strategies in the test applications:

- Same dependencies: These are almost all applications: basic-app, AWS, cache, rabbitmq, redis, schedule,... There is one branch per Micronaut branch: `3.3.x`, `3.2.x`, `3.1.x`,...
- Common structure but different dependencies: This is for applications that use Micronaut Data, Views, MQTT... There is one branch per Micronaut branch and database/view technology/...: `3.3.x_h2`, `3.3.x_mysql`, `3.3.x_postgres`, `3.3.x_thymeleaf`, `3.3.x_handlebars`, `3.3.x_v3`, `3.3.x_v5`,...


## Add a new Micronaut test application

These are the steps to add a new Micronaut-GraalVM test application to the pipeline:

- Create the new test application repository in https://github.com/micronaut-graal-tests.
- Do not use `master` branch. Just add a common README there like the one in `basic-app` or `data-jdbc`.
- Create the appropriate branch for the Micronaut version we want to target, e.g: `3.3.x`.
- Create a `build-native-image.sh` script similar to the one in the other applications.
- Add a README with the `curl` endpoints needed to test the application.

- Create a new branch in https://gitlab.com/micronaut-projects/micronaut-graal-tests and modify `gitlab-ci.yml` file:
    - Add the jobs for the new application in `micronaut` and `test` stages.
    - Create the scripts to build the native-image for the micronaut application and for the tests. If the application needs a Docker dependency make sure it is configured properly and define the appropriate environment variables for the CI Environment.
    - Commit the changes (a).
    - Remove/comment out the rest of the test applications, so they are not executed, and we don't waste time and resources running them.
    - Commit the changes (b).
    - Push the branch and wait for the build.
    - If something fails, fix commit (c) and repeat.
    - Once it passes, cherry-pick and squash the commits (a) and (c) that add the new application into the appropriate CI branch.
    - Push the changes *without* triggering a build `git push -o ci.skip`
    - When appropriate, cherry-pick the commit to the necessary branches. For example, if we are testing this initially in `3.3.x-stable` branch, then cherry-pick the commit that adds the new test applications to branches `3.3.x-dev` and `3.3.x-prerelease`.


## AWS Custom runners

We use custom AWS runners with auto-scaling configured. Everything is based on the official documentation https://docs.gitlab.com/runner/configuration/runner_autoscale_aws/.

This kind of configuration only needs one instance running 24x7 that will handle the auto-scaling of the rest of the instances used to run the tests. This instance doesn't need to be too powerful and at this moment it is a `t3a.small` (2 vCPU and 2 GB RAM).

It is important to note that the auto-scaling uses Docker Machine under the hood to start the new instances. Docker Machine is not maintained anymore so the Gitlab team created a fork that they maintain with critical fixes. At this moment we are using the latest version available at https://gitlab.com/gitlab-org/ci-cd/docker-machine/-/releases.

### Runners

At this moment we have three different runners depending on the needs of the task. Every job that should run on our custom runners needs to have the tag `aws` and additionally one of the following:

- `speed`: When a job running on Gitlab CI custom runners takes a lot of time or fails because of memory constraints, this is the first tag we need to add. A job with this tag will use a `c5a.xlarge` EC2 instance (4 vCPU and 8 GB RAM).
- `memory`: If a job still fails with the previous runner because it needs more memory, then we use this tag. It uses a `t3a.xlarge` EC2 instances (4 vCPU and 16 GB RAM).
- `speed2x` and `memory2x`: These to tags need to be combined and a job with them will use a `c5a.2xlarge` EC2 instance (8 vCPU and 16 GB RAM). At this moment only the jobs that build GraalVM from source code use this instance.


## Additional scripts to manage applications

There is [another project](https://gitlab.com/micronaut-projects/upgrade-micronaut-version) in the same Gitlab organization that contain bash utility scripts to upgrade the different versions in all the test applications:

- `create-new-branch.sh`: Create a new branch based off other one. Used when there is a new Micronaut minor or major version.
- `upgrade-gradle-plugin-version.sh`: Upgrade the Micronaut application Gradle plugin version.
- `upgrade-gradle-version.sh`: Upgrade Gradle Wrapper version.
- `upgrade-micronaut-data-version.sh`: Upgrade Micronaut Data version.
- `upgrade-micronaut-version.sh`:  Upgrade Micronaut version.
- `upgrade-shadow-plugin-version.sh`:  Upgrade Shadow plugin version.

## Upgrade strategy for modules

### Netty in core

Before upgrading Netty to a new version in Micronaut core we need to make sure it works with GraalVM. In the past there has been issues and regressions introduced by Netty.
Use the [basic-app](https://github.com/micronaut-graal-tests/micronaut-basic-app) for the test. Upgrade Netty in core, publish a local snapshot and use it in the application. Make sure the endpoints documented in the application works (specially `hello` and HTTP-client related).

### Liquibase and Flyway

Before upgrading versions of Liquibase and Flyway in the modules is necessary to make sure that they work with GraalVM. This is more important in Flyway because in Micronaut Flyway we have a few [GraalVM substitutions](https://github.com/micronaut-projects/micronaut-flyway/tree/master/flyway/src/main/java/io/micronaut/flyway/graalvm) from some internal Flyway classes. In the past, there has been issues in different Flyway versions because they team modified a constructor or added/removed a method.
