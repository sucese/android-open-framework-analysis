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

## flatMap

[flatMap](http://reactivex.io/documentation/operators/flatmap.html)将一个Observable的多个事件序列转换为多个Observable，在将这些Observables合并为一个Observable，注意
它并不保证合并后事件保持原有的顺序。

<img src="https://github.com/guoxiaoxing/android-open-framwork-analysis/raw/master/art/rxjava/operator_flatMap.png"/>

```java
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
```

输出打印

<img src="https://github.com/guoxiaoxing/android-open-framwork-analysis/blob/c0a9689a008515dc04e7d6f99f29345753210c7b/art/rxjava/log_operator_flatmap.png"/>

## concatMap

[concatMap](http://reactivex.io/documentation/operators/flatmap.html)将一个Observable的多个事件序列转换为多个Observable，在将这些Observables合并为一个Observable，注意
它保证合并后事件保持原有的顺序。

<img src="https://github.com/guoxiaoxing/android-open-framwork-analysis/raw/master/art/rxjava/operator_flatMap.png"/>

```java
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
```
输出打印

<img src="https://github.com/guoxiaoxing/android-open-framwork-analysis/raw/master/art/rxjava/operator_distinct.png"/>

## distinct

[distinct](http://reactivex.io/documentation/operators/distinct.html)用于去掉重复的事件。

```java
Observable.just(1, 1, 2, 3, 3, 4, 5)
        .distinct()
        .subscribe(new Consumer<Integer>() {
            @Override
            public void accept(Integer integer) throws Exception {
                Log.d(TAG, "accept: " + integer);
            }
        });
```

<img src="https://github.com/guoxiaoxing/android-open-framwork-analysis/raw/master/art/rxjava/log_operator_distinct.png"/>


输出打印

<img src="https://github.com/guoxiaoxing/android-open-framwork-analysis/raw/master/art/rxjava/log_operator_concatMap.png"/>

## filter

[filter](http://reactivex.io/documentation/operators/filter.html)按照给定的条件对事件进行过滤。

<img src="https://github.com/guoxiaoxing/android-open-framwork-analysis/raw/master/art/rxjava/operator_filter.png"/>

```java
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
```

输出打印

<img src="https://github.com/guoxiaoxing/android-open-framwork-analysis/raw/master/art/rxjava/log_operator_filter.png"/>

## buffer

[buffer]()用来拆分事件流，它有个方法buffer(int count, int skip)，它按照步长skip将事件流分组，每组最大不超过count。

<img src="https://github.com/guoxiaoxing/android-open-framwork-analysis/raw/master/art/rxjava/operator_buffer.png"/>

```java
Observable.just(1, 2, 3, 4, 5)
        .buffer(3, 1)
        .subscribe(new Consumer<List<Integer>>() {
            @Override
            public void accept(List<Integer> integers) throws Exception {
                Log.d(TAG, "accept: integers.size() " + integers.size());
                for(Integer integer : integers){
                    Log.d(TAG, "accept: " + integer);
                }
            }
        });
```
输出打印

skip = 1

<img src="https://github.com/guoxiaoxing/android-open-framwork-analysis/raw/master/art/rxjava/log_operator_buffer_1.png"/>

skip = 2

<img src="https://github.com/guoxiaoxing/android-open-framwork-analysis/raw/master/art/rxjava/log_operator_buffer_2.png"/>

skip = 3

<img src="https://github.com/guoxiaoxing/android-open-framwork-analysis/raw/master/art/rxjava/log_operator_buffer_3.png"/>

## timer

[timer](http://reactivex.io/documentation/operators/timer.html)用来实现定时任务，它默认在新线程中开启。

<img src="https://github.com/guoxiaoxing/android-open-framwork-analysis/raw/master/art/rxjava/operator_timer.png"/>

```java
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
```

输出打印

<img src="https://github.com/guoxiaoxing/android-open-framwork-analysis/raw/master/art/rxjava/log_operator_timer.png"/>


## interval

[interval](http://reactivex.io/documentation/operators/interval.html)根据指定的间隔创建周期任务。

<img src="https://github.com/guoxiaoxing/android-open-framwork-analysis/raw/master/art/rxjava/operator_interval.png"/>

```java
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
```

输出打印

<img src="https://github.com/guoxiaoxing/android-open-framwork-analysis/raw/master/art/rxjava/log_operator_interval.png"/>

## skip

[skip](http://reactivex.io/documentation/operators/skip.html)跳过指定个数的任务。

<img src="https://github.com/guoxiaoxing/android-open-framwork-analysis/raw/master/art/rxjava/operator_skip.png"/>

```java
Observable.just(1, 2, 3, 4, 5)
        .skip(2)
        .subscribe(new Consumer<Integer>() {
            @Override
            public void accept(Integer integer) throws Exception {
                Log.d(TAG, "accept: " + integer);
            }
        });
```

输出打印

<img src="https://github.com/guoxiaoxing/android-open-framwork-analysis/raw/master/art/rxjava/log_operator_skip.png"/>

## take

[take](http://reactivex.io/documentation/operators/take.html)表示最大接收指定的事件数量。

<img src="https://github.com/guoxiaoxing/android-open-framwork-analysis/raw/master/art/rxjava/operator_take.png"/>

```java
Observable.just(1, 2, 3, 4, 5)
        .take(2)
        .subscribe(new Consumer<Integer>() {
            @Override
            public void accept(Integer integer) throws Exception {
                Log.d(TAG, "accept: " + integer);
            }
        });
```
输出打印

<img src="https://github.com/guoxiaoxing/android-open-framwork-analysis/raw/master/art/rxjava/log_operator_take.png"/>

## single

single只会接收一个事件，当然它也没有onNext()回调方法。

```java
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
```

输出打印

<img src="https://github.com/guoxiaoxing/android-open-framwork-analysis/raw/master/art/rxjava/log_operator_single.png"/>

## debounce

[debounce](http://reactivex.io/documentation/operators/debounce.html)去除发送频率过来的事件。

<img src="https://github.com/guoxiaoxing/android-open-framwork-analysis/raw/master/art/rxjava/operator_debounce.png"/>

```java
Observable.create(new ObservableOnSubscribe<Integer>() {

    @Override
    public void subscribe(ObservableEmitter<Integer> e) throws Exception {

        e.onNext(1);
        Thread.sleep(300);//1经过300毫秒后再次发送2，间隔小于500，舍弃

        e.onNext(2);
        Thread.sleep(600);//2经过600毫秒后再次发送2，间隔大于500，保留

        e.onNext(3);
        Thread.sleep(400);//3经过400毫秒后再次发送2，间隔小于500，舍弃

        e.onNext(4);
        Thread.sleep(700);//4经过700毫秒后再次发送5，间隔大于500，保留

        e.onNext(5);
        Thread.sleep(200);//5后续没有再发送事件，当然也不存在频率过快的判定，保留

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
```

输出打印

<img src="https://github.com/guoxiaoxing/android-open-framwork-analysis/raw/master/art/rxjava/log_operator_debounce.png"/>

## defer

[defer](http://reactivex.io/documentation/operators/defer.html)是一个懒加载的操作符，只有当发生订阅的时候才会产生一个Observable，不订阅就不会产生Observable。

<img src="https://github.com/guoxiaoxing/android-open-framwork-analysis/raw/master/art/rxjava/operator_defer.png"/>

```java
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
```

输出打印

<img src="https://github.com/guoxiaoxing/android-open-framwork-analysis/raw/master/art/rxjava/log_operator_defer.png"/>

## last

[last](http://reactivex.io/documentation/operators/last.html)取出符合条件的最后一项。

<img src="https://github.com/guoxiaoxing/android-open-framwork-analysis/raw/master/art/rxjava/operator_last.png"/>

```java
Observable.just(1, 2,3)
        .last(4)
        .subscribe(new Consumer<Integer>() {
            @Override
            public void accept(Integer integer) throws Exception {
                Log.d(TAG, "accept: " + integer);
            }
        });
```

输出打印

<img src="https://github.com/guoxiaoxing/android-open-framwork-analysis/raw/master/art/rxjava/log_operator_last.png"/>

## merge

[merge](http://reactivex.io/documentation/operators/merge.html)用来合并多个Observable，它并不保证合并后的顺序。

<img src="https://github.com/guoxiaoxing/android-open-framwork-analysis/raw/master/art/rxjava/operator_merge.png"/>

```java
Observable.merge(Observable.just(1, 2, 3), Observable.just(4, 5))
        .subscribe(new Consumer<Integer>() {
            @Override
            public void accept(Integer integer) throws Exception {
                Log.d(TAG, "accept: " + integer);
            }
        });
```

输出打印

<img src="https://github.com/guoxiaoxing/android-open-framwork-analysis/raw/master/art/rxjava/log_operator_merge.png"/>

## reduce

[reduce](http://reactivex.io/documentation/operators/reduce.html)根据指定的函数来处理事件序列，最终返回处理的结果。

<img src="https://github.com/guoxiaoxing/android-open-framwork-analysis/raw/master/art/rxjava/operator_reduce.png"/>

```java
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
```

输出打印

<img src="https://github.com/guoxiaoxing/android-open-framwork-analysis/raw/master/art/rxjava/log_operator_reduce.png"/>

## scan

[scan](http://reactivex.io/documentation/operators/scan.html)根据指定的函数来处理事件序列，返回每步处理的结果。

<img src="https://github.com/guoxiaoxing/android-open-framwork-analysis/raw/master/art/rxjava/operator_scan.png"/>

```java
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
```

输出打印

<img src="https://github.com/guoxiaoxing/android-open-framwork-analysis/raw/master/art/rxjava/log_operator_scan.png"/>
