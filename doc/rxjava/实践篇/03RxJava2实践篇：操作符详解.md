# RxJava实践篇：操作符

**关于作者**

>郭孝星，非著名程序员，主要从事Android平台基础架构与中间件方面的工作，欢迎交流技术方面的问题，可以去我的[Github](https://github.com/guoxiaoxing)提交Issue或者发邮件至guoxiaoxingse@163.com与我联系。


## create

[create](http://reactivex.io/documentation/operators/create.html)用于闯将一个呗观察者对象Obserable。

<img src="https://github.com/guoxiaoxing/android-open-framwork-analysis/raw/master/art/rxjava/operator_create.png"/>

```java
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
```

输出打印

<img src="https://github.com/guoxiaoxing/android-open-framwork-analysis/raw/master/art/rxjava/log_operator_create.png"/>

## map

[map](http://reactivex.io/documentation/operators/map.html)将Obervable发出的事件都按照map操作符指定的函数进行变化，其实也就是将Obserable按照
某种函数关系转换为另一种Observable。

<img src="https://github.com/guoxiaoxing/android-open-framwork-analysis/raw/master/art/rxjava/operator_map.png"/>

```java
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
```

输出打印

<img src="https://github.com/guoxiaoxing/android-open-framwork-analysis/raw/master/art/rxjava/log_operator_map.png"/>

## zip

[zip]()用于将事件两两合并，最终查收的事件数以事件少的哪个为准，一个事件只能被使用一次，组合的顺序会按照事件发送的顺序为准。

<img src="https://github.com/guoxiaoxing/android-open-framwork-analysis/raw/master/art/rxjava/operator_zip.png"/>


```java
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
```

输出打印

<img src="https://github.com/guoxiaoxing/android-open-framwork-analysis/raw/master/art/rxjava/log_operator_zip.png"/>

## concat

[concat](http://reactivex.io/documentation/operators/concat.html)将两个Obervable连接成一个Obervable，发出事件的顺序按照原有Obervable的顺序不变。

<img src="https://github.com/guoxiaoxing/android-open-framwork-analysis/raw/master/art/rxjava/operator_concat.png"/>

```java
//按照事件的发出顺序进行连接
Observable.concat(Observable.just(1, 2, 3), Observable.just(4, 5, 6))
        .subscribe(new Consumer<Integer>() {
            @Override
            public void accept(Integer integer) throws Exception {
                Log.d(TAG, "accept: " + integer);
            }
        });
```

输出打印

<img src="https://github.com/guoxiaoxing/android-open-framwork-analysis/raw/master/art/rxjava/log_operator_concat.png"/>