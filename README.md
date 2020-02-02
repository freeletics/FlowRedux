# FlowRedux

[![CircleCI](https://circleci.com/gh/freeletics/FlowRedux.svg?style=svg)](https://circleci.com/gh/freeletics/FlowFedux)
[![Download](https://maven-badges.herokuapp.com/maven-central/com.freeletics.flowredux/flowredux/badge.svg) ](https://maven-badges.herokuapp.com/maven-central/com.freeletics.flowredux/flowredux)

Building kotlin multiplatform StateMachine made easy with DSL and coroutines.

## Usage

To be done.

## Dependency

```groovy
implementation 'com.freeletics.flowredux:flowredux-multiplatform:0.2.1'
implementation 'com.freeletics.flowredux:dsl-multiplatform:0.2.1'
```

### JVM only
```groovy
implementation 'com.freeletics.flowredux:dsl:0.2.1'
```

### Native binaries
```groovy
implementation 'com.freeletics.flowredux:flowredux-iosx64:0.2.1'
implementation 'com.freeletics.flowredux:flowredux-iosarm64:0.2.1'
implementation 'com.freeletics.flowredux:flowredux-iosarm32:0.2.1'
implementation 'com.freeletics.flowredux:flowredux-watchosx86:0.2.1'
implementation 'com.freeletics.flowredux:flowredux-watchosarm64:0.2.1'
implementation 'com.freeletics.flowredux:flowredux-watchosarm32:0.2.1'
implementation 'com.freeletics.flowredux:flowredux-tvosx64:0.2.1'
implementation 'com.freeletics.flowredux:flowredux-tvosxarm64:0.2.1'

implementation 'com.freeletics.flowredux:dsl-iosx64:0.2.1'
implementation 'com.freeletics.flowredux:dsl-iosarm64:0.2.1'
implementation 'com.freeletics.flowredux:dsl-iosarm32:0.2.1'
implementation 'com.freeletics.flowredux:dsl-watchosx86:0.2.1'
implementation 'com.freeletics.flowredux:dsl-watchosarm64:0.2.1'
implementation 'com.freeletics.flowredux:dsl-watchosarm32:0.2.1'
implementation 'com.freeletics.flowredux:dsl-tvosx64:0.2.1'
implementation 'com.freeletics.flowredux:dsl-tvosxarm64:0.2.1'
```

### JavaScript
No javascript version release yet but its on our TODO list.


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

JVM
```groovy
implementation 'com.freeletics.flowredux:flowredux:0.2.2-SNAPSHOT'
implementation 'com.freeletics.flowredux:flowredux-dsl:0.2.2-SNAPSHOT'
```
