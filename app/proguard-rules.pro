# Add project specific ProGuard rules here.
-keepclassmembers class * extends androidx.room.RoomDatabase {
    abstract <methods>;
}

-keep class com.aggregatorx.app.data.model.** { *; }
-keepclassmembers class com.aggregatorx.app.data.model.** { *; }

-dontwarn org.jsoup.**
-keep class org.jsoup.** { *; }
