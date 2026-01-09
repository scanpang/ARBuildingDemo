# ARCore
-keep class com.google.ar.** { *; }
-keep class com.google.ar.core.** { *; }

# ML Kit
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# Keep model classes
-keep class com.example.arbuildingdemo.models.** { *; }
