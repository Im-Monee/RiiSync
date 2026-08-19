pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Repository di Shizuku (Rikka)
        maven { url = uri("https://maven.aliyun.com/repository/public") } // mirror opzionale, puoi rimuoverlo
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "RiiSync"
include(":app")
