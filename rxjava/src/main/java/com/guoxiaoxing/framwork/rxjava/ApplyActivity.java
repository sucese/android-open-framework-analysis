package com.guoxiaoxing.framwork.rxjava;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;

import com.google.gson.Gson;
import com.guoxiaoxing.framwork.rxjava.model.NetworkModel;

import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.ObservableOnSubscribe;
import io.reactivex.functions.Consumer;
import io.reactivex.functions.Function;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class ApplyActivity extends AppCompatActivity implements View.OnClickListener {

    private static final String TAG = "RxJava";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_operator);
    }

    @Override
    public void onClick(View v) {

    }

    private void applyNetwork() {

        Observable.create(new ObservableOnSubscribe<Response>() {

            @Override
            public void subscribe(ObservableEmitter<Response> e) throws Exception {
                Request request = new Request.Builder()
                        .url("")
                        .build();
                Call call = new OkHttpClient().newCall(request);
                //同步请求
                Response response = call.execute();
                e.onNext(response);
            }
        }).map(new Function<Response, NetworkModel>() {

            @Override
            public NetworkModel apply(Response response) throws Exception {

                return new Gson().fromJson(response.body().string(), NetworkModel.class);
            }
        }).subscribe(new Consumer<NetworkModel>() {
            @Override
            public void accept(NetworkModel networkModel) throws Exception {

            }
        });
    }
}
