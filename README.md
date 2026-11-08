# tie-spring-boot-starter

[![Maven Central](https://img.shields.io/maven-central/v/io.github.tie-integration-engine/tie-spring-boot-starter)](https://central.sonatype.com/artifact/io.github.tie-integration-engine/tie-spring-boot-starter)

`tie-spring-boot-starter` is a module of [TIE](https://github.com/tie-integration-engine/tie), a stateless Java library for file
integrations: the optional Spring Boot 4 auto-configuration.

This repository is a git submodule of [tie-integration-engine/tie](https://github.com/tie-integration-engine/tie),
which holds the parent pom, the examples and the documentation. The module builds from there:

```shell
git clone --recurse-submodules https://github.com/tie-integration-engine/tie.git
cd tie
./mvnw -pl tie-spring-boot-starter -am verify
```

See the [wiki](https://github.com/tie-integration-engine/tie/wiki) for the documentation. Licensed under the MIT License.
