package com.guoxiaoxing.retrofit.demo;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

import java.util.List;

import retrofit2.Call;
import retrofit2.Retrofit;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    private void createRetrofitApi() {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://api.github.com/")
                .build();
        Api api = retrofit.create(Api.class);
        Call<List<Repo>> repos = api.listRepos("octocat");
    }
}
