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

# Firestore maps documents onto these POJOs via reflection (DocumentSnapshot.toObject).
# Without this, R8 renames the classes/getters in release builds and every toObject call
# throws at runtime - e.g. the cashier Redeem screen crashed on open.
-keep class com.example.adminloyalty.models.** { *; }

# Keep file/line info so release crash stack traces stay readable.
-keepattributes SourceFile,LineNumberTable
