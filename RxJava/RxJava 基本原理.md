
#一 基本概念

RxJava是一个在 Java VM 上使用可观测的序列来组成异步的、基于事件的程序的库。

>A library for composing asynchronous and event-based programs using observable sequences for the Java VM

RxJava使用扩展的观察者模式来实现异步操作，它有4个基本概念：

- Observable：被观察者
- Observer：观察者
- subscribe：订阅
- event：事件

Observable 和 Observer 通过 subscribe() 方法实现订阅关系，从而 Observable 可以在需要的时候发出事件来通知 Observer。

RxJava实现了3种回调方法：

- onNext()：事件队列中的事件到来，Observable 会通知 Observer。
- onCompleted(): 事件队列完结。RxJava 不仅把每个事件单独处理，还会把它们看做一个队列。RxJava 规定，当不会再有新的 
onNext() 发出时，需要触发 onCompleted() 方法作为标志。
- onError(): 事件队列异常。在事件处理过程中出异常时，onError() 会被触发，同时队列自动终止，不允许再有事件发出。

在一个正确运行的事件序列中, onCompleted() 和 onError() 有且只有一个，并且是事件序列中的最后一个。需要注意的是
onCompleted() 和 onError() 二者也是互斥的，即在队列中调用了其中一个，就不应该再调用另一个。


