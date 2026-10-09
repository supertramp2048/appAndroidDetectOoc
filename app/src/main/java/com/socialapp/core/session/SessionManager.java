package com.socialapp.core.session;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.gson.Gson;
import com.socialapp.core.navigation.Navigator;
import com.socialapp.core.network.dto.UserResource;
import com.socialapp.core.security.KeystoreHelper;

public class SessionManager {

    private static final String PREF_NAME = "SocialAppSession";
    private static final String KEY_TOKEN = "encrypted_token";
    private static final String KEY_USER = "encrypted_user";

    public static class SessionState {
        public final boolean isLoggedIn;
        @Nullable
        public final UserResource user;
        @Nullable
        public final String token;

        public SessionState(boolean isLoggedIn, @Nullable UserResource user, @Nullable String token) {
            this.isLoggedIn = isLoggedIn;
            this.user = user;
            this.token = token;
        }
    }

    private final SharedPreferences prefs;
    private final KeystoreHelper keystoreHelper;
    private final Gson gson;
    private final MutableLiveData<SessionState> sessionLiveData = new MutableLiveData<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private String cachedToken;
    private UserResource cachedUser;

    public SessionManager(@NonNull Context context, @NonNull KeystoreHelper keystoreHelper) {
        this.prefs = context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        this.keystoreHelper = keystoreHelper;
        this.gson = new Gson();
        loadSession();
    }

    private synchronized void loadSession() {
        String encryptedToken = prefs.getString(KEY_TOKEN, null);
        String encryptedUser = prefs.getString(KEY_USER, null);

        this.cachedToken = keystoreHelper.decrypt(encryptedToken);
        String userJson = keystoreHelper.decrypt(encryptedUser);

        if (userJson != null && !userJson.trim().isEmpty()) {
            try {
                this.cachedUser = gson.fromJson(userJson, UserResource.class);
            } catch (Exception e) {
                this.cachedUser = null;
            }
        } else {
            this.cachedUser = null;
        }

        notifySessionChange();
    }

    public synchronized void saveSession(@NonNull String token, @NonNull UserResource user) {
        this.cachedToken = token;
        this.cachedUser = user;

        String encryptedToken = keystoreHelper.encrypt(token);
        String encryptedUser = keystoreHelper.encrypt(gson.toJson(user));

        prefs.edit()
                .putString(KEY_TOKEN, encryptedToken)
                .putString(KEY_USER, encryptedUser)
                .apply();

        notifySessionChange();
    }

    public synchronized void updateUser(@NonNull UserResource user) {
        this.cachedUser = user;
        String encryptedUser = keystoreHelper.encrypt(gson.toJson(user));
        prefs.edit().putString(KEY_USER, encryptedUser).apply();
        notifySessionChange();
    }

    public synchronized void logout() {
        this.cachedToken = null;
        this.cachedUser = null;

        prefs.edit()
                .remove(KEY_TOKEN)
                .remove(KEY_USER)
                .apply();

        notifySessionChange();
    }

    public void requireLogin(@NonNull Runnable action, @NonNull Context context) {
        if (isLoggedIn()) {
            action.run();
        } else {
            Navigator.navigateTo(context, Navigator.ROUTE_LOGIN);
        }
    }

    public void handleGlobalUnauthorized() {
        mainHandler.post(this::logout);
    }

    private void notifySessionChange() {
        boolean loggedIn = cachedToken != null && !cachedToken.trim().isEmpty();
        SessionState state = new SessionState(loggedIn, cachedUser, cachedToken);
        if (Looper.myLooper() == Looper.getMainLooper()) {
            sessionLiveData.setValue(state);
        } else {
            sessionLiveData.postValue(state);
        }
    }

    @NonNull
    public LiveData<SessionState> getSessionLiveData() {
        return sessionLiveData;
    }

    public synchronized boolean isLoggedIn() {
        return cachedToken != null && !cachedToken.trim().isEmpty();
    }

    @Nullable
    public synchronized String getAuthToken() {
        return cachedToken;
    }

    @Nullable
    public synchronized UserResource getCurrentUser() {
        return cachedUser;
    }
}
