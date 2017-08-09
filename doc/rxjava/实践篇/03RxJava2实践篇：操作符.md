# RxJava2实践篇：操作符

**关于作者**

>郭孝星，非著名程序员，主要从事Android平台基础架构与中间件方面的工作，欢迎交流技术方面的问题，可以去我的[Github](https://github.com/guoxiaoxing)提交Issue或者发邮件至guoxiaoxingse@163.com与我联系。

RxJava中操作符基于责任链模式设计而成，它操作一个Obervable，并返回一个新的Observable，所以我们可以链式调用操作符。

[RxJava操作符官方文档](http://reactivex.io/documentation/operators.html)

## 一 创建型Observable

- Create — create an Observable from scratch(擦伤) by calling observer methods programmatically
- Defer — do not create the Observable until the observer subscribes(订阅), and create a fresh Observable for each observer
- Empty/Never/Throw — create Observables that have very precise(精确的) and limited behavior(行为)
- From — convert(皈依者) some other object or data structure(结构) into an Observable
- Interval — create an Observable that emits(发出) a sequence(序列) of integers(整数) spaced by a particular time interval
- Just — convert an object or a set of objects into an Observable that emits that or those objects
- Range — create an Observable that emits a range of sequential(连续的) integers
- Repeat — create an Observable that emits a particular item or sequence of items repeatedly
- Start — create an Observable that emits the return value of a function
- Timer — create an Observable that emits a single item after a given delay

### create

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

### defer

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

### timer

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

## 二 转换型Observable

- Buffer — periodically(定期地) gather items from an Observable into bundles(束) and emit these bundles rather than emitting the items one at a time
- FlatMap — transform the items emitted by an Observable into Observables, then flatten(击败) the emissions(发射) from those into a single Observable
- GroupBy — divide an Observable into a set of Observables that each emit(发出) a different group of items from the original Observable, organized by key
- Map — transform(改变) the items emitted by an Observable by applying a function to each item
- Scan — apply a function to each item emitted by an Observable, sequentially(从而), and emit each successive(连续的) value
- Window — periodically(定期地) subdivide(把…再分) items from an Observable into Observable windows and emit these windows rather than emitting the items one at a time

### buffer

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

### map

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

<img src="https://github.com/guoxiaoxing/android-open-framwork-analysis/raw/master/art/rxjava/log_operator_flatmap.png"/>

## concatMap

[concatMap](http://reactivex.io/documentation/operators/flatmap.html)将一个Observable的多个事件序列转换为多个Observable，在将这些Observables合并为一个Observable，注意
它保证合并后事件保持原有的顺序。

<img src="https://github.com/guoxiaoxing/android-open-framwork-analysis/raw/master/art/rxjava/operator_concatMap.png"/>

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

<img src="https://github.com/guoxiaoxing/android-open-framwork-analysis/raw/master/art/rxjava/log_operator_concatMap.png"/>

### scan

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

## 三 过滤型Observable

- Debounce — only emit an item from an Observable if a particular timespan(时间间隔) has passed without it emitting another item
- Distinct — suppress(抑制) duplicate(复制的) items emitted by an Observable
- ElementAt — emit only item n emitted by an Observable
- Filter — emit only those items from an Observable that pass a predicate(断定为…) test
- First — emit(发出) only the first item, or the first item that meets a condition, from an Observable
- IgnoreElements — do not emit any items from an Observable but mirror its termination(结束) notification(通知)
- Last — emit only the last item emitted by an Observable
- Sample — emit the most recent item emitted by an Observable within periodic(周期的) time intervals
- Skip — suppress(抑制) the first n items emitted by an Observable
- SkipLast — suppress the last n items emitted by an Observable
- Take — emit only the first n items emitted by an Observable
- TakeLast — emit(发出) only the last n items emitted by an Observable

### distinct

[distinct](http://reactivex.io/documentation/operators/distinct.html)用于去掉重复的事件。

<img src="https://github.com/guoxiaoxing/android-open-framwork-analysis/raw/master/art/rxjava/operator_distinct.png"/>

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


输出打印

<img src="https://github.com/guoxiaoxing/android-open-framwork-analysis/raw/master/art/rxjava/log_operator_distinct.png"/>

### filter

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

### skip

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

### take

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

### debounce

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



## 四 组合型Observable

- And/Then/When — combine sets of items emitted by two or more Observables by means of Pattern and Plan intermediaries
- CombineLatest — when an item is emitted by either of two Observables, combine the latest item emitted by each Observable via a specified(规定的) function and emit items based on the results of this function
- Join — combine items emitted by two Observables whenever an item from one Observable is emitted during a time window defined(定义) according to an item emitted by the other Observable
- Merge — combine multiple Observables into one by merging(合并) their emissions(发射)
- StartWith — emit(发出) a specified(规定的) sequence(序列) of items before beginning to emit the items from the source Observable
- Switch — convert(转变) an Observable that emits Observables into a single Observable that emits the items emitted by the most-recently-emitted of those Observables
- Zip — combine the emissions of multiple Observables together via a specified function and emit single items for each combination(结合) based on the results of this function

### zip

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

### merge

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

## 五 错误处理型Observable

## 六 条件判断型Observable

## 七 集合操作型Observable

- Average — calculates(计算) the average of numbers emitted by an Observable and emits this average
- Concat — emit the emissions(发射) from two or more Observables without interleaving(交错) them
- Count — count the number of items emitted by the source Observable and emit only this value
- Max — determine, and emit, the maximum-valued item emitted by an Observable
- Min — determine, and emit, the minimum-valued item emitted by an Observable
- Reduce — apply a function to each item emitted by an Observable, sequentially(从而), and emit the final value
- Sum — calculate the sum of numbers emitted by an Observable and emit this sum

### concat

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

### reduce

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


## 八 连接型Observable

- Connect — instruct a connectable Observable to begin emitting(发出) items to its subscribers(订阅)
- Publish — convert(转变) an ordinary Observable into a connectable Observable
- RefCount — make a Connectable Observable behave like an ordinary Observable
- Replay — ensure(保证) that all observers see the same sequence(序列) of emitted items, even if they subscribe after the Observable has begun emitting items

## 九 其他Observable

- Delay — shift(转移) the emissions(发射) from an Observable forward in time by a particular amount(数量)
- Do — register an action to take upon a variety of Observable lifecycle(生活周期) events
- Materialize/Dematerialize — represent both the items emitted(发出) and the notifications(通知) sent as emitted items, or reverse(颠倒) this process
- ObserveOn — specify(指定) the scheduler(安排) on which an observer will observe this Observable
- Serialize — force an Observable to make serialized(序列化) calls and to be well-behaved(很乖的)
- Subscribe — Observable向观察者发送事件序列
- SubscribeOn — specify the scheduler an Observable should use when it is subscribed(订阅) to
= TimeInterval — convert(转变) an Observable that emits items into one that emits indications(指示) of the amount of time elapsed(消逝) between those emissions
- Timeout — mirror the source Observable, but issue an error notification if a particular period of time elapses without any emitted items
- Timestamp — attach(依附) a timestamp(时间戳) to each item emitted(发出) by an Observable
- Using — create a disposable(可任意处理的) resource that has the same lifespan(寿命) as the Observable

