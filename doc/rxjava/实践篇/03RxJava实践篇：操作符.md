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
        e.onNext("1");
        e.onNext("2");
        e.onNext("3");
        e.onComplete();
        //onComplete()调用后事件会继续被发送，但是不会被接收
        e.onNext("4");
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

[map](http://reactivex.io/documentation/operators/map.html)

<img src="https://github.com/guoxiaoxing/android-open-framwork-analysis/raw/master/art/rxjava/operator_map.png"/>

```java
Observable.create(new ObservableOnSubscribe<String>() {
    @Override
    public void subscribe(ObservableEmitter<String> e) throws Exception {
        e.onNext("1");
        e.onNext("2");
        e.onNext("3");
        e.onComplete();
        //onComplete()调用后事件会继续被发送，但是不会被接收
        e.onNext("4");
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

输出打印

<img src="https://github.com/guoxiaoxing/android-open-framwork-analysis/raw/master/art/rxjava/log_operator_map.png"/>
```