package com.guoxiaoxing.okhttp.demo;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private OkHttpClient okHttpClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        findViewById(R.id.btn_async_request).setOnClickListener(this);

        okHttpClient = new OkHttpClient.Builder().build();
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_async_request:
                for (int i = 0; i < 100; i++) {
                    asyncRequest("http://www.baidu.com");
                }
                break;
        }
    }

    private void syncRequest(String url) throws IOException {
        Request request = new Request.Builder()
                .url(url)
                .build();
        OkHttpClient okHttpClient = new OkHttpClient.Builder()
                .build();
        Response response = okHttpClient.newCall(request).execute();
    }

    private void asyncRequest(String url) {
        Request request = new Request.Builder()
                .url(url)
                .build();
        okHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {

            }
        });
    }
}
