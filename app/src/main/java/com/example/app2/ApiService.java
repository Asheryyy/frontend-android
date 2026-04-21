package com.example.app2;


import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;
public class ApiService {
    @POST("api/Auth/Login")
        // Khớp đúng với Controller bên .NET
    Call<LoginResponse> login(@Body LoginRequest request) {
        return null;
    }
}
