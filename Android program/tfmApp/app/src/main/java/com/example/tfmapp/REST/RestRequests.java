package com.example.tfmapp.REST;


import com.google.gson.JsonObject;


import org.json.JSONObject;

import retrofit2.*;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Headers;
import retrofit2.http.POST;

public interface RestRequests {


    @Headers({"Accept: application/json"})
    @GET("plugins/telemetry/DEVICE/299b5640-2d59-11ef-9229-f3aa570680fb/values/timeseries?keys=humidity,temperature")
    Call<JsonObject> getLastMonitoring(@Header("X-Authorization") String token);

    @Headers({"Accept: application/json"})
    @GET("plugins/telemetry/DEVICE/299b5640-2d59-11ef-9229-f3aa570680fb/values/timeseries?keys=latitude,longitude")
    Call<JsonObject> getLastLocation(@Header("X-Authorization") String token);

    @Headers({"Accept: application/json"})
    @GET("v1/rdtiahf5espbzqxczttn/attributes")
    Call<JsonObject> getAttributes(@Header("X-Authorization") String token);

    @Headers("Content-Type: application/json")
    @POST("v1/rdtiahf5espbzqxczttn/attributes")
    Call<JsonObject> setAttributes(@Body JsonObject attributes, @Header("Authorization") String authHeader);


}
