# FlowRedux

[![CircleCI](https://circleci.com/gh/freeletics/RxRedux.svg?style=svg)](https://circleci.com/gh/freeletics/FlowFedux)
[![Download](https://maven-badges.herokuapp.com/maven-central/com.freeletics.flowredux/flowredux/badge.svg) ](https://maven-badges.herokuapp.com/maven-central/com.freeletics.flowredux/flowredux)

This project is still WIP.

Should you use it in production? No!

## Description

A Redux store implementation entirely based on Kotlin Coroutine's Flow type (inspired by [redux-observable](https://redux-observable.js.org))
that helps to isolate side effects. RxRedux is (kind of) a replacement for RxJava's `.scan()` operator. 

![RxRedux In a Nutshell](https://raw.githubusercontent.com/freeletics/RxRedux/master/docs/rxredux.png)

This project is an experimental port of [RxRedux](https://github.com/freeletics/RxRedux).

## Dependency

This project is in a very early stage.

#### Snapshot
Latest snapshot (directly published from master branch from Travis CI):

```groovy
allprojects {
    repositories {
        // Your repositories.
        // ...
        // Add url to snapshot repository
        maven {
            url "https://oss.sonatype.org/content/repositories/snapshots/"
        }
    }
}

```

```groovy
implementation 'com.freeletics.flowredux:flowredux:0.0.1-SNAPSHOT'
```
