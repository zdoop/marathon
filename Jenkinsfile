#!/usr/bin/env groovy

ansiColor('gnome-terminal') {
  node('JenkinsMarathonCI-Debian8-2017-04-25') {
    stage("Checkout") {
      checkout scm
      sh "/usr/local/bin/amm scripts/install_mesos.sc"
    }
    stage("Build and Test") {
     sh """sudo sbt clean \
        coverage testWithCoverageReport \
        integration:test \
        serial-integration:test \
        scapegoat"""
    }
    stage("Package") {
     sh """sudo rm -f target/packages/* && sudo sbt clean packageAll"""
    }
  }
}
