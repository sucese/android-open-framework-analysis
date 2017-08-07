package com.guoxiaoxing.framwork.rxjava;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.AndroidException;
import android.util.Log;
import android.view.View;

import com.orhanobut.logger.Logger;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.ObservableOnSubscribe;
import io.reactivex.ObservableSource;
import io.reactivex.Observer;
import io.reactivex.Scheduler;
import io.reactivex.Single;
import io.reactivex.SingleObserver;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.BiFunction;
import io.reactivex.functions.Consumer;
import io.reactivex.functions.Function;
import io.reactivex.functions.Predicate;
import io.reactivex.schedulers.Schedulers;

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
        findViewById(R.id.btn_opertor_flatmap).setOnClickListener(this);
        findViewById(R.id.btn_opertor_concatmap).setOnClickListener(this);
        findViewById(R.id.btn_opertor_distinct).setOnClickListener(this);
        findViewById(R.id.btn_opertor_filter).setOnClickListener(this);
        findViewById(R.id.btn_opertor_buffer).setOnClickListener(this);
        findViewById(R.id.btn_opertor_timer).setOnClickListener(this);
        findViewById(R.id.btn_opertor_interval).setOnClickListener(this);
        findViewById(R.id.btn_opertor_skip).setOnClickListener(this);
        findViewById(R.id.btn_opertor_take).setOnClickListener(this);
        findViewById(R.id.btn_opertor_single).setOnClickListener(this);
        findViewById(R.id.btn_opertor_denounce).setOnClickListener(this);
        findViewById(R.id.btn_opertor_defer).setOnClickListener(this);
        findViewById(R.id.btn_opertor_last).setOnClickListener(this);
        findViewById(R.id.btn_opertor_merge).setOnClickListener(this);
        findViewById(R.id.btn_opertor_reduce).setOnClickListener(this);
        findViewById(R.id.btn_opertor_scan).setOnClickListener(this);
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
            case R.id.btn_opertor_flatmap:
                flatMap();
                break;
            case R.id.btn_opertor_concatmap:
                concatMap();
                break;
            case R.id.btn_opertor_distinct:
                distinct();
                break;
            case R.id.btn_opertor_filter:
                filter();
                break;
            case R.id.btn_opertor_buffer:
                buffer();
                break;
            case R.id.btn_opertor_timer:
                timer();
                break;
            case R.id.btn_opertor_interval:
                interval();
                break;
            case R.id.btn_opertor_skip:
                skip();
                break;
            case R.id.btn_opertor_take:
                take();
                break;
            case R.id.btn_opertor_single:
                single();
                break;
            case R.id.btn_opertor_denounce:
                debounce();
                break;
            case R.id.btn_opertor_defer:
                defer();
                break;
            case R.id.btn_opertor_last:
                last();
                break;
            case R.id.btn_opertor_merge:
                merge();
                break;
            case R.id.btn_opertor_reduce:
                reduce();
                break;
            case R.id.btn_opertor_scan:
                scan();
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

    private void flatMap() {
        Observable.create(new ObservableOnSubscribe<String>() {

            @Override
            public void subscribe(ObservableEmitter<String> e) throws Exception {

                e.onNext("a");
                e.onNext("b");
                e.onNext("c");

            }
        }).flatMap(new Function<String, ObservableSource<String>>() {
            @Override
            public ObservableSource<String> apply(String s) throws Exception {
                List<String> list = new ArrayList<>();
                for (int i = 0; i < 3; i++) {
                    list.add(s + "_" + i);
                }
                //加上随机时间的延迟，观察事件序列的返回顺序
                int delayTime = (int) (1 + Math.random() * 10);
                return Observable.fromIterable(list).delay(delayTime, TimeUnit.MILLISECONDS);
            }
        }).subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Consumer<String>() {
                    @Override
                    public void accept(String s) throws Exception {
                        Log.d(TAG, "accept: " + s);
                    }
                });
    }

    private void concatMap() {
        Observable.create(new ObservableOnSubscribe<String>() {

            @Override
            public void subscribe(ObservableEmitter<String> e) throws Exception {

                e.onNext("a");
                e.onNext("b");
                e.onNext("c");

            }
        }).concatMap(new Function<String, ObservableSource<String>>() {
            @Override
            public ObservableSource<String> apply(String s) throws Exception {
                List<String> list = new ArrayList<>();
                for (int i = 0; i < 3; i++) {
                    list.add(s + "_" + i);
                }
                //加上随机时间的延迟，观察事件序列的返回顺序
                int delayTime = (int) (1 + Math.random() * 10);
                return Observable.fromIterable(list).delay(delayTime, TimeUnit.MILLISECONDS);
            }
        }).subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Consumer<String>() {
                    @Override
                    public void accept(String s) throws Exception {
                        Log.d(TAG, "accept: " + s);
                    }
                });
    }

    private void distinct() {
        Observable.just(1, 1, 2, 3, 3, 4, 5)
                .distinct()
                .subscribe(new Consumer<Integer>() {
                    @Override
                    public void accept(Integer integer) throws Exception {
                        Log.d(TAG, "accept: " + integer);
                    }
                });
    }

    private void filter() {
        Observable.just(-1, 4, 2, 7, 4, 3, 9)
                .filter(new Predicate<Integer>() {
                    @Override
                    public boolean test(Integer integer) throws Exception {
                        return integer > 3;
                    }
                }).subscribe(new Consumer<Integer>() {
            @Override
            public void accept(Integer integer) throws Exception {
                Log.d(TAG, "accept: " + integer);
            }
        });
    }

    private void buffer() {

        Observable.just(1, 2, 3, 4, 5)
                .buffer(3, 1)
                .subscribe(new Consumer<List<Integer>>() {
                    @Override
                    public void accept(List<Integer> integers) throws Exception {
                        Log.d(TAG, "accept: integers.size() " + integers.size());
                        for (Integer integer : integers) {
                            Log.d(TAG, "accept: " + integer);
                        }
                    }
                });

    }

    private void timer() {
        Log.d(TAG, "time: " + System.currentTimeMillis());
        //从触发任务到任务开启间隔了1秒
        Observable.timer(1, TimeUnit.SECONDS)
                .subscribe(new Consumer<Long>() {
                    @Override
                    public void accept(Long aLong) throws Exception {
                        Log.d(TAG, "accept: " + aLong);
                        Log.d(TAG, "time: " + System.currentTimeMillis());
                    }
                });
    }

    private void interval() {
        Log.d(TAG, "time: " + System.currentTimeMillis());
        //延时2秒手，每隔1秒触发一次任务
        Observable.interval(2, 1, TimeUnit.SECONDS)
                .subscribe(new Consumer<Long>() {
                    @Override
                    public void accept(Long aLong) throws Exception {
                        Log.d(TAG, "accept: " + aLong);
                        Log.d(TAG, "time: " + System.currentTimeMillis());
                    }
                });
    }

    private void skip() {
        Observable.just(1, 2, 3, 4, 5)
                .skip(2)
                .subscribe(new Consumer<Integer>() {
                    @Override
                    public void accept(Integer integer) throws Exception {
                        Log.d(TAG, "accept: " + integer);
                    }
                });
    }

    private void take() {
        Observable.just(1, 2, 3, 4, 5)
                .take(2)
                .subscribe(new Consumer<Integer>() {
                    @Override
                    public void accept(Integer integer) throws Exception {
                        Log.d(TAG, "accept: " + integer);
                    }
                });
    }

    private void single() {
        Single.just(1).subscribe(new SingleObserver<Integer>() {
            @Override
            public void onSubscribe(Disposable d) {
                Log.d(TAG, "onSubscribe: " + d.isDisposed());
            }

            @Override
            public void onSuccess(Integer integer) {
                Log.d(TAG, "onSuccess: " + integer);
            }

            @Override
            public void onError(Throwable e) {

            }
        });
    }

    private void debounce() {
        Observable.create(new ObservableOnSubscribe<Integer>() {

            @Override
            public void subscribe(ObservableEmitter<Integer> e) throws Exception {

                Thread.sleep(600);
                e.onNext(1);

                Thread.sleep(300);
                e.onNext(2);

                Thread.sleep(600);
                e.onNext(3);

                Thread.sleep(400);
                e.onNext(4);

                Thread.sleep(700);
                e.onNext(5);

                e.onComplete();
            }
        }).debounce(500, TimeUnit.MILLISECONDS)
                .subscribe(new Consumer<Integer>() {
                    @Override
                    public void accept(Integer integer) throws Exception {
                        Log.d(TAG, "time: " + System.currentTimeMillis());
                        Log.d(TAG, "accept: " + integer);
                    }
                });
    }

    private void defer() {
        Observable.defer(new Callable<ObservableSource<Integer>>() {
            @Override
            public ObservableSource<Integer> call() throws Exception {
                return Observable.just(1, 2, 3, 4, 5);
            }
        }).subscribe(new Observer<Integer>() {
            @Override
            public void onSubscribe(Disposable d) {
                Log.d(TAG, "onSubscribe: " + d.isDisposed());
            }

            @Override
            public void onNext(Integer integer) {
                Log.d(TAG, "onNext: " + integer);
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

    private void last(){
        Observable.just(1, 2,3)
                .last(4)
                .subscribe(new Consumer<Integer>() {
                    @Override
                    public void accept(Integer integer) throws Exception {
                        Log.d(TAG, "accept: " + integer);
                    }
                });
    }

    private void merge(){
        Observable.merge(Observable.just(1, 2, 3), Observable.just(4, 5))
                .subscribe(new Consumer<Integer>() {
                    @Override
                    public void accept(Integer integer) throws Exception {
                        Log.d(TAG, "accept: " + integer);
                    }
                });
    }

    private void reduce(){
        Observable.just(1, 2, 3)
                .reduce(new BiFunction<Integer, Integer, Integer>() {
                    @Override
                    public Integer apply(Integer integer, Integer integer2) throws Exception {
                        return integer + integer2;
                    }
                }).subscribe(new Consumer<Integer>() {
            @Override
            public void accept(Integer integer) throws Exception {
                Log.d(TAG, "accept: " + integer);
            }
        });
    }

    private void scan(){
        Observable.just(1, 2, 3)
                .scan(new BiFunction<Integer, Integer, Integer>() {
                    @Override
                    public Integer apply(Integer integer, Integer integer2) throws Exception {
                        return integer + integer2;
                    }
                }).subscribe(new Consumer<Integer>() {
            @Override
            public void accept(Integer integer) throws Exception {
                Log.d(TAG, "accept: " + integer);
            }
        });
    }
}

