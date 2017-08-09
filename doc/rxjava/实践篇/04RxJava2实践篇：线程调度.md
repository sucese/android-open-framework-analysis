# RxJava2实践篇：应用场景

**关于作者**

>郭孝星，非著名程序员，主要从事Android平台基础架构与中间件方面的工作，欢迎交流技术方面的问题，可以去我的[Github](https://github.com/guoxiaoxing)提交Issue或者发邮件至guoxiaoxingse@163.com与我联系。

上一篇我们讲了操作符的变换与组合，这一篇我们来讲一下线程调度，还是先贴一下RxJava的事件模型图。

事件的上下游并不总是运行在同一个线程中，一般的，我们通常希望在子线程中处理费时操作，在主线程中更新UI。

默认情况下事件上游在哪个线程中发送事件，事件下游就在哪个线程接收事件。

例如：

```java
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
```

输出打印

<img src="https://github.com/guoxiaoxing/android-open-framwork-analysis/raw/master/art/rxjava/thread_normal.png"/>

可以看出它们都工作在主线程中。

RxJava内部使用线程池来维护线程，拥有较高的效率。

RxJava指定线程

- subscribeOn()：指定Observable所在线程。
- observeOn()：指定Observer所在线程。

RxJava内置线程模型

- Schedulers.single()：返回一个默认的，共享的，单线程的Scheduler实例，它通常用在那些有较强执行顺序要求的后台线程。
- Schedulers.newThread()：返回一个默认的，共享的Scheduler实例，它创建一个常规的线程。
- Schedulers.trampoline()：返回一个默认的，共享的Scheduler实例，它以FIFO的方式来执行任务。
- Schedulers.io()：返回一个默认的，共享的Scheduler实例，它代表IO操作的线程，通常用于网络，读写文件等IO密集型操作。
- Schedulers.computation()：返回一个默认的，共享的Scheduler实例，它通常用来执行复杂的计算操作。
- AndroidSchedulers.mainThread()：Android主线程


