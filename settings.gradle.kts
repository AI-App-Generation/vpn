pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()

        maven(url = "https://jitpack.io")
        maven(url = "https://artifact.bytedance.com/repository/pangle")
        maven(url = "https://dl-maven-android.mintegral.com/repository/mbridge_android_sdk_oversea")

        maven {
            url = uri("https://maven.pkg.github.com/binary-ad/BBL-Module-Ads")
            credentials {
                username = (extra["GITHUB_ACTOR"] as? String) ?: System.getenv("GH_ACTOR") ?: ""
                password = (extra["GITHUB_TOKEN"] as? String) ?: System.getenv("GH_TOKEN") ?: ""
            }
        }

        maven {
            url = uri("https://maven.pkg.github.com/Binary-Bridge-Labs/ExecutionEngineSDK")
            credentials {
                username = (extra["gpr.fe_user"] as? String) ?: System.getenv("GH_ACTOR") ?: ""
                password = (extra["gpr.fe_key"] as? String) ?: System.getenv("GH_TOKEN") ?: ""
            }
        }
    }
}

rootProject.name = "VeilVPN"
include(":app")
include(":vpnLib")
