# Builds and view description for Jenkins

## Project structure

 * [firmware](firmware) - builds and views for IDU 7000
 * [nms](nms) - builds and views for core and web NMS 
 * [rt-router](rt-router) - builds and views for the router firmware
 * [vars](vars) - shared librarys

## How to start using this repo

 * create a pipeline using Jenkinsfile from this project
 * launch the pipeline

## Required Jenkins plugins

* [Git plugin](https://wiki.jenkins.io/display/JENKINS/Git+Plugin)
* [Job DSL plugin](https://github.com/jenkinsci/job-dsl-plugin)