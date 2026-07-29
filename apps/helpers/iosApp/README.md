# Digibuddy Helpers iOS host

Open `DigibuddyHelpers.xcodeproj` in Xcode. The **Build Kotlin framework** phase calls the repository Gradle Wrapper and links the static `DigibuddyHelpers` framework from `:apps:helpers`.

The placeholder bundle identifier `com.digibuddy.helpers` is isolated in `Configuration/BundleIdentifiers.xcconfig`. Select a local development team before installing on a physical device or creating a signed archive.

The root source logo is copied without modification and displayed with aspect-fit behavior. No square app icon is generated from the rectangular logo.
