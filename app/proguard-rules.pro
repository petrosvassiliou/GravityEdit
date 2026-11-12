# GravityEdit ProGuard / R8 configuration
-dontobfuscate
-keepattributes *Annotation*,SourceFile,LineNumberTable,Signature,InnerClasses,EnclosingMethod
-keep class com.gravityedit.app.** { *; }
-keep enum com.gravityedit.app.** { *; }
-dontwarn java.lang.instrument.**
-dontwarn sun.misc.**
