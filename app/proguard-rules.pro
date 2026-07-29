# Add project specific ProGuard rules here.

# NanoHTTPD
-keep class fi.iki.elonen.** { *; }
-dontwarn fi.iki.elonen.**

# Support library
-keep class android.support.v7.** { *; }
-dontwarn android.support.v7.**

# XmlPull
-keep class org.xmlpull.v1.** { *; }
-dontwarn org.xmlpull.v1.**
