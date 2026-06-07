package com.example.myapplication;

import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Header;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.Part;

public interface ApiService {
    @Multipart
    @POST("verify")
    Call<PredictionResponse> verifyImage(
        @Header("X-API-Key") String apiKey,
        @Part MultipartBody.Part file,
        @Part("caption") RequestBody caption,
        @Part("mode") RequestBody mode
    );
}
