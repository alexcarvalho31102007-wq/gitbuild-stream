package com.example.compiler.zip

object StandardGradleWrapper {

    fun getWrapperProperties(): String {
        return """
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-8.7-bin.zip
networkTimeout=10000
validateDistributionUrl=true
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
""".trimIndent()
    }

    fun getGradlewScript(): String {
        return """#!/bin/sh
APP_BASE_NAME=${'$'}(basename "${'$'}0")
APP_HOME=${'$'}(cd "${'$'}(dirname "${'$'}0")" && pwd)

# Use system gradle if wrapper jar is missing
if [ -f "${'$'}APP_HOME/gradle/wrapper/gradle-wrapper.jar" ]; then
    exec java -jar "${'$'}APP_HOME/gradle/wrapper/gradle-wrapper.jar" "${'$'}@"
else
    exec gradle "${'$'}@"
fi
""".trimIndent()
    }
}
