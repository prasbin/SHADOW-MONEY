# ProGuard rules for SHADOW MONEY

# Add project-specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in /Users/User/Library/Android/sdk/gradle/maven_repo/android-sdk/platforms/android-36/proguard-android-optimize.txt

# Keep our Compose classes
-keep class com.prasbin.shadowmoney.** { *; }

# Keep data classes for Room
-keepclassmembers class * extends androidx.room.RoomDatabase {
    <fields>;
}

# Keep generated code
-keep class * extends androidx.room.r { *; }
-keep class * extends androidx.room.g { *; }

# Keep DataStore classes
-keep class * extends androidx.datastore.preferences.* { *; }

# Keep navigation classes
-keep class com.prasbin.shadowmoney.presentation.navigation.** { *; }

# Keep theme classes
-keep class com.prasbin.shadowmoney.presentation.theme.** { *; }
