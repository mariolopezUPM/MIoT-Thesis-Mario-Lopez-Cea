package com.example.tfmapp.REST;


import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class ServiceGenerator {

    private static final String BASE_URI = "https://demo.thingsboard.io/api/"; //base uri
    private static Retrofit.Builder builder = new Retrofit.Builder()
            .baseUrl(BASE_URI)
            .client(new OkHttpClient.Builder().addInterceptor((
                    new HttpLoggingInterceptor()).setLevel(HttpLoggingInterceptor.Level.BODY)).build())
            .addConverterFactory(GsonConverterFactory.create());

    // function to get the retrofic intance
    public static <S> S createService(Class<S> serviceClass){
        Retrofit adapter = builder.build();
        return adapter.create(serviceClass);
    }
}
