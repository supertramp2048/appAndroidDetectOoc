package com.socialapp.core.network;

import com.socialapp.feature.factcheck.PredictionResponse;

import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.http.Header;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.Part;

public interface FactCheckApiService {
    @Multipart
    @POST("verify")
    Call<PredictionResponse> verifyImage(
            @Header("X-API-Key") String apiKey,
            @Part MultipartBody.Part file,
            @Part("caption") RequestBody caption,
            @Part("mode") RequestBody mode
    );
}
