#!/usr/bin/env kotlin

@file:DependsOn("com.freeletics.gradle:scripts-formatting:0.9.0")

import com.freeletics.gradle.scripts.KtLintCli

KtLintCli().main(args)
