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
1. Setup environmental variables

    ```
    MAVENPUB_USER=(Username)
    MAVENPUB_PASS=(Password)
    MAVENPUB_URL=(Maven Target URL)
    ```
2. Run Gradle task

    ```
    gradle upload
    ```