package com.guoxiaoxing.framwork.rxjava;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.ObservableOnSubscribe;
import io.reactivex.Observer;
import io.reactivex.Scheduler;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;
import io.reactivex.schedulers.Schedulers;

public class ThreadActivity extends AppCompatActivity implements View.OnClickListener {

    private static final String TAG = "ThreadActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_thread);

        findViewById(R.id.btn_thread_normal).setOnClickListener(this);
        findViewById(R.id.btn_thread_new_thread).setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_thread_normal:
                normal();
                break;
            case R.id.btn_thread_new_thread:
                newThread();
                break;
        }
    }

    private void normal() {
        Observable.create(new ObservableOnSubscribe<Integer>() {
            @Override
            public void subscribe(ObservableEmitter<Integer> e) throws Exception {
                Log.d(TAG, "subscribe() - thread: " + Thread.currentThread().getName());
                e.onNext(1);
            }
        }).subscribe(new Observer<Integer>() {
            @Override
            public void onSubscribe(Disposable d) {
                Log.d(TAG, "onSubscribe() - thread: " + Thread.currentThread().getName());
            }

            @Override
            public void onNext(Integer integer) {
                Log.d(TAG, "onNext() - thread: " + Thread.currentThread().getName());
            }

            @Override
            public void onError(Throwable e) {
                Log.d(TAG, "onError() - thread: " + Thread.currentThread().getName());
            }

            @Override
            public void onComplete() {
                Log.d(TAG, "onComplete() - thread: " + Thread.currentThread().getName());
            }
        });
    }

    private void newThread() {
        Observable.create(new ObservableOnSubscribe<Integer>() {
            @Override
            public void subscribe(ObservableEmitter<Integer> e) throws Exception {
                Log.d(TAG, "subscribe() - thread: " + Thread.currentThread().getName());
                e.onNext(1);
                e.onComplete();
            }
        }).subscribeOn(Schedulers.newThread())//将Observable运行在新线程
                .observeOn(Schedulers.io())//Observer将运行在Android主线程
                .doOnNext(new Consumer<Integer>() {
                    @Override
                    public void accept(Integer integer) throws Exception {
                        Log.d(TAG, "accept() - thread: " + Thread.currentThread().getName());
                    }
                })
                .observeOn(AndroidSchedulers.mainThread())//线程可以多次切换
                .subscribe(new Observer<Integer>() {
                    @Override
                    public void onSubscribe(Disposable d) {
                        Log.d(TAG, "onSubscribe() - thread: " + Thread.currentThread().getName());
                    }

                    @Override
                    public void onNext(Integer integer) {
                        Log.d(TAG, "onNext() - thread: " + Thread.currentThread().getName());
                    }

                    @Override
                    public void onError(Throwable e) {
                        Log.d(TAG, "onError() - thread: " + Thread.currentThread().getName());
                    }

                    @Override
                    public void onComplete() {
                        Log.d(TAG, "onComplete() - thread: " + Thread.currentThread().getName());
                    }
                });
    }
}
