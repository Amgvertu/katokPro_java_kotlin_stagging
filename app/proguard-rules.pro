# Сохраняем все data-классы модели (включая вложенные)
-keep class com.katok.pro.model.** { *; }
-keep class com.katok.pro.model.admin.** { *; }

# Сохраняем все поля, даже без аннотаций (для Gson)
-keepclassmembers class * {
    *;
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

# Сохраняем методы PhoneUtils (для форматирования номера)
-keep class com.katok.pro.util.PhoneUtils { *; }