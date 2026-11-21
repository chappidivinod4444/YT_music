# The extractor and its JavaScript engine use reflection: keep them whole.
-keep class org.schabi.newpipe.extractor.** { *; }
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.classfile.** { *; }
-keep class org.jsoup.** { *; }
-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*

# Optional desktop-only classes the libraries mention but never use on Android.
-dontwarn java.beans.**
-dontwarn javax.script.**
-dontwarn jdk.dynalink.**
-dontwarn org.mozilla.javascript.tools.**
-dontwarn javax.annotation.**
-dontwarn org.jetbrains.annotations.**
-dontwarn com.google.errorprone.annotations.**
-dontwarn org.slf4j.**
-dontwarn edu.umd.cs.findbugs.annotations.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn java.lang.invoke.StringConcatFactory
-dontwarn org.jspecify.**
-dontwarn com.google.re2j.**
-dontwarn javax.lang.model.**
-dontwarn kotlin.annotations.jvm.**
