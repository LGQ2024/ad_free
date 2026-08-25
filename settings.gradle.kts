pluginManagement {
    repositories {
        google {
            url = uri("https://redirector.gvt1.com/edgedl/android/maven2")
            content {
                includeGroupByRegex("androidx\\..*")
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google\\.android.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google {
            url = uri("https://redirector.gvt1.com/edgedl/android/maven2")
            content {
                includeGroupByRegex("androidx\\..*")
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google\\.android.*")
            }
        }
        mavenCentral()
    }
}

rootProject.name = "Jingwang"
include(":app")
