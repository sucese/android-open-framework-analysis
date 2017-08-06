package com.guoxiaoxing.framwork.rxjava;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import com.orhanobut.logger.Logger;

import org.jetbrains.annotations.NotNull;

import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.ObservableOnSubscribe;
import io.reactivex.Observer;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.BiFunction;
import io.reactivex.functions.Consumer;
import io.reactivex.functions.Function;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private static final String TAG = "RxJava";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        findViewById(R.id.btn_opertor_create).setOnClickListener(this);
        findViewById(R.id.btn_opertor_map).setOnClickListener(this);
        findViewById(R.id.btn_opertor_zip).setOnClickListener(this);
        findViewById(R.id.btn_opertor_concat).setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_opertor_create:
                create();
                break;
            case R.id.btn_opertor_map:
                map();
                break;
            case R.id.btn_opertor_zip:
                zip();
                break;
            case R.id.btn_opertor_concat:
                concat();
                break;
            default:
                break;
        }
    }

    private void create() {
        Observable.create(new ObservableOnSubscribe<String>() {
            @Override
            public void subscribe(ObservableEmitter<String> e) throws Exception {
                e.onNext("a");
                e.onNext("b");
                e.onNext("c");
                e.onComplete();
                //onComplete()调用后事件会继续被发送，但是不会被接收
                e.onNext("d");
            }
        }).subscribe(new Observer<String>() {
            @Override
            public void onSubscribe(Disposable d) {
                //d.isDisposed()返回false的时候可以正常接收事件，主动调用d.dispose()会切断事件的接收
                Log.d(TAG, "onSubscribe: " + d.isDisposed());
            }

            @Override
            public void onNext(String s) {
                Log.d(TAG, "onNext: " + s);
            }

            @Override
            public void onError(Throwable e) {
                Log.d(TAG, "onError: " + e.getMessage());
            }

            @Override
            public void onComplete() {
                Log.d(TAG, "onComplete");
            }
        });
    }

    private void map() {
        Observable.create(new ObservableOnSubscribe<String>() {
            @Override
            public void subscribe(ObservableEmitter<String> e) throws Exception {
                e.onNext("a");
                e.onNext("b");
                e.onNext("c");
                e.onComplete();
                //onComplete()调用后事件会继续被发送，但是不会被接收
                e.onNext("d");
            }
        }).map(new Function<String, String>() {
            @Override
            public String apply(String s) throws Exception {
                return s + "_apply";
            }
        }).subscribe(new Consumer<String>() {
            @Override
            public void accept(String o) throws Exception {
                Log.d(TAG, "accept: " + o);

            }
        });
    }

    private void zip() {

        Observable.zip(getStringObservable(), getIntegerObservable(), new BiFunction<String, Integer, String>() {
            @Override
            public String apply(@NotNull String s, @NotNull Integer integer) throws Exception {

                return s + "_" + integer;
            }
        }).subscribe(new Consumer<String>() {
            @Override
            public void accept(String s) throws Exception {
                Log.d(TAG, "accept: " + s);
            }
        });
    }

    private Observable<String> getStringObservable() {
        return Observable.create(new ObservableOnSubscribe<String>() {

            @Override
            public void subscribe(ObservableEmitter<String> e) throws Exception {
                e.onNext("a");
                e.onNext("b");
                e.onNext("c");
                e.onComplete();
                e.onNext("d");
            }
        });
    }

    private Observable<Integer> getIntegerObservable() {
        return Observable.create(new ObservableOnSubscribe<Integer>() {

            @Override
            public void subscribe(ObservableEmitter<Integer> e) throws Exception {
                e.onNext(1);
                e.onNext(2);
                e.onNext(3);
                e.onComplete();
                e.onNext(4);
            }
        });
    }

    private void concat() {
        //按照事件的发出顺序进行连接
        Observable.concat(Observable.just(1, 2, 3), Observable.just(4, 5, 6))
                .subscribe(new Consumer<Integer>() {
                    @Override
                    public void accept(Integer integer) throws Exception {
                        Log.d(TAG, "accept: " + integer);
                    }
                });
    }

}
