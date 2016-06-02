# Automated Building and Publishing

## GitLab-CI
GitLab-CI can be used for quick building and publishing.

### Basic Environment Setup
1. Prepare building environments, follow [build environment setup instructions](BUILD.md).
2. Prepare `~/publish.env.ZWUtils-Java` using the following template:

    ```shell
    MAVENPUB_USER=(Username)
    MAVENPUB_PASS=(Password)
    MAVENPUB_URL=(Maven Target URL)
    ```

### Configure GitLab runner
1. The runner must have the following tags:

    ```
    java, gradle
    ```

### Repository Configurations
1. Automated building will happen on all branches EXCEPT `master` and those start with `test-` 
2. Automated publishing will ONLY happen on the `master` branch
