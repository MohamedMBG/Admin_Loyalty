# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile
# Firestore maps documents onto POJOs by reflecting over field and getter names
# (DocumentSnapshot.toObject). R8 renames them, which turns every deserialized
# model into empty fields, so keep the model package intact.
-keep class com.example.adminloyalty.models.** { *; }
-keepclassmembers class com.example.adminloyalty.models.** {
    <init>();
}

# Firestore needs generic signatures and its own property annotations at runtime.
-keepattributes Signature
-keepattributes *Annotation*

# Keep stack traces in release crash reports readable; the line numbers are
# resolved against app/build/outputs/mapping/release/mapping.txt.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
