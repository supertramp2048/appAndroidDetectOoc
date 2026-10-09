package com.socialapp.core.di;

import android.content.Context;

import androidx.annotation.NonNull;

import com.socialapp.BuildConfig;
import com.socialapp.core.network.AuthInterceptor;
import com.socialapp.core.network.FactCheckApiService;
import com.socialapp.core.network.SocialAppApiService;
import com.socialapp.core.security.KeystoreHelper;
import com.socialapp.core.session.SessionManager;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class AppContainer {

    private final Context applicationContext;
    private final KeystoreHelper keystoreHelper;
    private final SessionManager sessionManager;

    private final OkHttpClient socialAppOkHttpClient;
    private final OkHttpClient factCheckOkHttpClient;

    private final Retrofit socialAppRetrofit;
    private final Retrofit factCheckRetrofit;

    private final SocialAppApiService socialAppApiService;
    private final FactCheckApiService factCheckApiService;

    public AppContainer(@NonNull Context context) {
        this.applicationContext = context.getApplicationContext();

        this.keystoreHelper = new KeystoreHelper(this.applicationContext);
        this.sessionManager = new SessionManager(this.applicationContext, this.keystoreHelper);

        HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor();
        loggingInterceptor.setLevel(BuildConfig.DEBUG ? HttpLoggingInterceptor.Level.BODY : HttpLoggingInterceptor.Level.NONE);

        this.socialAppOkHttpClient = new OkHttpClient.Builder()
                .addInterceptor(new AuthInterceptor(this.sessionManager))
                .addInterceptor(loggingInterceptor)
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build();

        this.factCheckOkHttpClient = new OkHttpClient.Builder()
                .addInterceptor(loggingInterceptor)
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build();

        this.socialAppRetrofit = new Retrofit.Builder()
                .baseUrl(BuildConfig.BASE_URL.endsWith("/") ? BuildConfig.BASE_URL : BuildConfig.BASE_URL + "/")
                .client(this.socialAppOkHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        this.factCheckRetrofit = new Retrofit.Builder()
                .baseUrl(BuildConfig.FACTCHECK_BASE_URL.endsWith("/") ? BuildConfig.FACTCHECK_BASE_URL : BuildConfig.FACTCHECK_BASE_URL + "/")
                .client(this.factCheckOkHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        this.socialAppApiService = this.socialAppRetrofit.create(SocialAppApiService.class);
        this.factCheckApiService = this.factCheckRetrofit.create(FactCheckApiService.class);
    }

    public Context getApplicationContext() {
        return applicationContext;
    }

    public KeystoreHelper getKeystoreHelper() {
        return keystoreHelper;
    }

    public SessionManager getSessionManager() {
        return sessionManager;
    }

    public SocialAppApiService getSocialAppApiService() {
        return socialAppApiService;
    }

    public FactCheckApiService getFactCheckApiService() {
        return factCheckApiService;
    }
}
