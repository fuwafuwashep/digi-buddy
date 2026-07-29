# Digibuddy iOS host

Open `Digibuddy.xcodeproj` in Xcode. The `Build Kotlin framework` phase calls the repository Gradle Wrapper and links the
static `DigibuddyCustomer` framework.

The placeholder bundle identifier is isolated in `Configuration/BundleIdentifiers.xcconfig`. A developer team must be
selected locally before installing on a physical device or creating a signed archive.

The checked-in source logo is included as an unmodified resource and the Compose screen uses a separate unmodified copy.
No app icon set or branded splash artwork is generated in this foundation phase because the rectangular source logo is not
an appropriate square icon. A later asset phase should create padded, platform-specific derivatives without stretching it.
