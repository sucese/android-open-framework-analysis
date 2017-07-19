package com.guoxiaoxing.framwork.rxjava;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

import com.orhanobut.logger.Logger;

import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.ObservableOnSubscribe;
import io.reactivex.Observer;
import io.reactivex.disposables.Disposable;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    private void printMessage() {

        Observer<String> observer = new Observer<String>() {
            @Override
            public void onSubscribe(Disposable d) {
                Logger.d("onSubscribe：" + d.toString());
            }

            @Override
            public void onNext(String s) {
                Logger.d("onNext：" + s);
            }

            @Override
            public void onError(Throwable e) {
                Logger.d("onError：" + e.getMessage());
            }

            @Override
            public void onComplete() {
                Logger.d("onComplete：");
            }
        };

        Observable observable = Observable.create(new ObservableOnSubscribe() {
            @Override
            public void subscribe(ObservableEmitter e) throws Exception {
                e.
            }
        })

    }
}
