# Automated Building and Publishing

## GitLab-CI
GitLab-CI can be used for quick building and publishing.

### Basic Environment Setup
1. Prepare building environments, follow [build environment setup instructions](BUILD.md).
2. Prepare `~/publish.secrets.ZWUtils-Java` using the following template:

    ```shell
    user=(username)
    pass=(password)
    mavenServer=(maven url)
    ```

### Configure GitLab runner
1. The runner must have the following tags:

    ```
    java, gradle
    ```

### Repository Configurations
1. Automated building will happen on all branches EXCEPT `master` and those start with `test-` 
2. Automated publishing will ONLY happen on the `master` branch
