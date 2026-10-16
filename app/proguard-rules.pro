# SproutPet ProGuard Rules

# 保留数据模型（Gson 反序列化需要）
-keep class com.sproutpet.pet.data.model.** { *; }
-keep class com.sproutpet.core.network.model.** { *; }

# Gson
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Retrofit
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Hilt
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }

# Rive
-keep class app.rive.** { *; }
-dontwarn app.rive.**

# Lottie
-dontwarn com.airbnb.lottie.**

# MMKV
-keep class com.tencent.mmkv.** { *; }

# 保留枚举（Room TypeConverter 需要）
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# 保留 Parcelable
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}
