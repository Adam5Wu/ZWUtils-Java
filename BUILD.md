# Environment Setup and Building
## Prerequisites
* Oracle JDK 1.8
* Gradle 2.10+
* (Recommended) Ubuntu 14+
* (Recommended) Eclipse Mars

## Building
### Gradle commandline

```
gradle build
```

### Eclipse
Import as Gradle project using official Buildship plug-in.

# Publishing
The following maven publishing is supported via Gradle.

## Authenticated publishing to private maven repository
1. Setup control variables

    ```
    publish.secrets:
    user=(username)
    pass=(password)
    mavenServer=(maven url)
    ```
2. Run Gradle task

    ```
    gradle upload
    ```