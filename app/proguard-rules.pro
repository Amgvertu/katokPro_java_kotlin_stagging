# Сохраняем все data-классы модели (включая вложенные)
-keep class com.katok.pro.model.** { *; }
-keep class com.katok.pro.model.admin.** { *; }
-keep class com.katok.pro.network.** { *; }

# Сохраняем все поля, даже без аннотаций (для Gson)
-keepclassmembers class * {
    *;
    @com.google.gson.annotations.SerializedName <fields>;
}

# Для Retrofit и Gson
-keepattributes Signature, InnerClasses, EnclosingMethod
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}

# Для OkHttp
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**

# Для Gson (важно!)
-keep class com.google.gson.** { *; }
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer
-keepclassmembers class * {
  public <init>();
}
-keep class kotlin.** { *; }
-keep class kotlinx.** { *; }
-keepclassmembers class kotlin.Metadata {
    public *;
}

# Сохраняем методы PhoneUtils (для форматирования номера)
-keep class com.katok.pro.util.PhoneUtils { *; }

# Сохраняем атрибуты аннотаций (необходимо для корректной работы Gson)
-keepattributes *Annotation*
-keepattributes Exception
-keepattributes SourceFile,LineNumberTable

# Отключаем предупреждения о Gson
-dontwarn com.google.gson.**

#FireBase and FCM
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-keep class com.google.firebase.messaging.** { *; }
-keep class com.google.firebase.iid.** { *; }
-keep class com.katok.pro.services.KatokFirebaseMessagingService { *; }

-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
-keepattributes Signature