package com.socialapp.core.di;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.NonNull;

import com.socialapp.BuildConfig;
import com.socialapp.core.network.AuthInterceptor;
import com.socialapp.core.network.sim.MockNetworkInterceptor;
import com.socialapp.core.network.FactCheckApiService;
import com.socialapp.core.network.SocialAppApiService;
import com.socialapp.core.prefetch.BatchExperimentRunner;
import com.socialapp.core.prefetch.MetricsLogger;
import com.socialapp.core.prefetch.PrefetchDemoPreferences;
import com.socialapp.core.prefetch.PrefetchEngine;
import com.socialapp.core.prefetch.ReplayEngine;
import com.socialapp.core.prefetch.profile.ProfileManager;
import com.socialapp.core.prefetch.profile.ProfileStore;
import com.socialapp.core.security.KeystoreHelper;
import com.socialapp.core.session.SessionManager;
import com.socialapp.core.telemetry.AndroidNavigationObserver;
import com.socialapp.core.telemetry.NetworkTelemetry;
import com.socialapp.core.telemetry.PerformanceTelemetry;
import com.socialapp.core.telemetry.TelemetryCollector;
import com.socialapp.core.ui.blocks.adapter.BlockWarmupEngine;
import com.socialapp.core.ui.blocks.adapter.UnifiedBlockViewPool;
import com.socialapp.core.ui.blocks.editor.EditorWebViewPool;
import com.socialapp.core.ui.blocks.layout.BlockLayoutCache;
import com.socialapp.core.ui.blocks.layout.BlockPrecomputer;
import com.socialapp.core.ui.blocks.layout.TextPaintRegistry;
import com.socialapp.core.ui.blocks.parser.ArticleAstCache;

import java.io.File;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class AppContainer {

    private final Context applicationContext;
    private final KeystoreHelper keystoreHelper;
    private final SessionManager sessionManager;
    private final PrefetchDemoPreferences prefetchDemoPreferences;
    private final PrefetchEngine prefetchEngine;
    private final ProfileManager profileManager;
    private final MetricsLogger metricsLogger;
    private final ReplayEngine replayEngine;
    private final BatchExperimentRunner batchRunner;
    private final AndroidNavigationObserver navigationObserver;
    private final PerformanceTelemetry performanceTelemetry;

    // Pillar 4.1 Unified Block Rendering singletons
    private final TextPaintRegistry textPaintRegistry;
    private final BlockLayoutCache blockLayoutCache;
    private final BlockPrecomputer blockPrecomputer;
    private final UnifiedBlockViewPool unifiedBlockViewPool;
    private final BlockWarmupEngine blockWarmupEngine;
    private final ArticleAstCache articleAstCache;
    private final EditorWebViewPool editorWebViewPool;

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
        this.prefetchDemoPreferences = new PrefetchDemoPreferences(this.applicationContext);
        this.prefetchEngine = new PrefetchEngine(this.prefetchDemoPreferences);
        if (BuildConfig.PROFILE_FIXTURES) {
            this.prefetchEngine.setCurrentPolicy(PrefetchEngine.Policy.P0_NONE, false);
        }
        this.profileManager = new ProfileManager(prefetchEngine,
                new ProfileStore(() -> new File(applicationContext.getFilesDir(), ProfileStore.LIVE_FILE_NAME)),
                Executors.newSingleThreadExecutor(), new Handler(Looper.getMainLooper())::post,
                message -> Log.w("NavigationProfile", message));
        this.metricsLogger = new MetricsLogger(this.applicationContext);
        this.performanceTelemetry = new PerformanceTelemetry(metricsLogger.getWriter(),
                metricsLogger.outputFile(PerformanceTelemetry.FILE_NAME), SystemClock::elapsedRealtime);
        this.navigationObserver = new AndroidNavigationObserver(
                new TelemetryCollector(SystemClock::elapsedRealtime, (runId, sessionId, replaySessionId) ->
                        prefetchEngine.setCacheSession(sessionId, sessionId == null ? null
                                : metricsLogger.captureWasteSink(runId, sessionId, replaySessionId))),
                event -> {
                    profileManager.onNavigationEvent(event);
                    metricsLogger.recordNavigation(event);
                });
        this.replayEngine = new ReplayEngine(this.applicationContext, this.navigationObserver);
        this.batchRunner = new BatchExperimentRunner(this.applicationContext);

        this.textPaintRegistry = new TextPaintRegistry();
        this.blockLayoutCache = new BlockLayoutCache();
        this.blockPrecomputer = new BlockPrecomputer(this.textPaintRegistry, this.blockLayoutCache);
        this.unifiedBlockViewPool = new UnifiedBlockViewPool();
        this.blockWarmupEngine = new BlockWarmupEngine(this.blockPrecomputer, this.unifiedBlockViewPool);
        this.articleAstCache = new ArticleAstCache();
        this.editorWebViewPool = new EditorWebViewPool(this.applicationContext);

        NetworkTelemetry networkTelemetry = new NetworkTelemetry(SystemClock::elapsedRealtime, performanceTelemetry);

        this.socialAppOkHttpClient = new OkHttpClient.Builder()
                .eventListenerFactory(networkTelemetry)
                .addInterceptor(new MockNetworkInterceptor(networkTelemetry::markSimulated))
                .addInterceptor(new AuthInterceptor(this.sessionManager))
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build();

        this.factCheckOkHttpClient = new OkHttpClient.Builder()
                .eventListenerFactory(networkTelemetry)
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

    public PerformanceTelemetry getPerformanceTelemetry() {
        return performanceTelemetry;
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

    @NonNull
    public PrefetchDemoPreferences getPrefetchDemoPreferences() {
        return prefetchDemoPreferences;
    }

    public PrefetchEngine getPrefetchEngine() {
        return prefetchEngine;
    }

    public ProfileManager getProfileManager() {
        return profileManager;
    }

    public MetricsLogger getMetricsLogger() {
        return metricsLogger;
    }

    public ReplayEngine getReplayEngine() {
        return replayEngine;
    }

    public BatchExperimentRunner getBatchRunner() {
        return batchRunner;
    }

    @NonNull
    public TextPaintRegistry getTextPaintRegistry() {
        return textPaintRegistry;
    }

    @NonNull
    public BlockLayoutCache getBlockLayoutCache() {
        return blockLayoutCache;
    }

    @NonNull
    public BlockPrecomputer getBlockPrecomputer() {
        return blockPrecomputer;
    }

    @NonNull
    public UnifiedBlockViewPool getUnifiedBlockViewPool() {
        return unifiedBlockViewPool;
    }

    @NonNull
    public BlockWarmupEngine getBlockWarmupEngine() {
        return blockWarmupEngine;
    }

    @NonNull
    public ArticleAstCache getArticleAstCache() {
        return articleAstCache;
    }

    @NonNull
    public EditorWebViewPool getEditorWebViewPool() {
        return editorWebViewPool;
    }

    public AndroidNavigationObserver getNavigationObserver() {
        return navigationObserver;
    }
}
