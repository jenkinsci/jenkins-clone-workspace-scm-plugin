#!/usr/bin/env groovy

/* `buildPlugin` step provided by: https://github.com/jenkins-infra/pipeline-library */
buildPlugin(
  // Container agents start faster and are easier to administer
  useContainerAgent: true,
  // Show failures on all configurations
  failFast: false,
  // Test Java 11 with default version, Java 17 with more recent LTS
  configurations: [
    [platform: 'windows', jdk: '17', jenkins: '2.375.1'],
    [platform: 'linux',   jdk: '11'],
  ]
)
