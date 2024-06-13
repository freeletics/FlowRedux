#!/usr/bin/env kotlin

@file:DependsOn("com.freeletics.gradle:scripts-formatting:0.14.0")

import com.freeletics.gradle.scripts.KtLintCli

KtLintCli().main(args)
