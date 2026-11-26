# Preserve generic signatures and annotations required by Retrofit & Gson
-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*

# Preserve Gson DTO models and any class with @SerializedName fields
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keep class com.socialapp.core.network.dto.** { *; }
-keep class com.socialapp.feature.factcheck.ChatRequest { *; }
-keep class com.socialapp.feature.factcheck.ChatResponse { *; }
-keep class com.socialapp.feature.factcheck.ChatResponse$* { *; }
-keep class com.socialapp.feature.factcheck.PredictionResponse { *; }
-keep class com.socialapp.feature.factcheck.PredictionResponse$* { *; }

# Preserve Retrofit service interfaces
-keep,allowobfuscation,allowshrinking interface com.socialapp.core.network.SocialAppApiService { *; }
-keep,allowobfuscation,allowshrinking interface com.socialapp.core.network.FactCheckApiService { *; }

# Preserve WebView JavascriptInterface methods
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keep class com.socialapp.core.ui.blocks.editor.WebAppInterfaceProxy { *; }
-keep class com.socialapp.feature.compose.WebAppInterface { *; }

# Preserve custom Views instantiated via XML or ViewPools
-keep public class * extends android.view.View {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

# Preserve androidx.profileinstaller startup initializer
-keep class androidx.profileinstaller.** { *; }
