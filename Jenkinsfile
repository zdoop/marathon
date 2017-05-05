#!/usr/bin/env groovy

ansiColor('gnome-terminal') {
  node('JenkinsMarathonCI-Debian8-2017-04-27') {
    stage("Checkout") {
      checkout scm
    }
    stage("Build and Test") {
     sh """sudo -E sbt clean \
       coverage testWithCoverageReport \
       integration:test\
       """
    }
  }
}
