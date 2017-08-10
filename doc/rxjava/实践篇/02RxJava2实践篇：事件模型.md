# RxJava2实践篇：事件模型

**关于作者**

>郭孝星，非著名程序员，主要从事Android平台基础架构与中间件方面的工作，欢迎交流技术方面的问题，可以去我的[Github](https://github.com/guoxiaoxing)提交Issue或者发邮件至guoxiaoxingse@163.com与我联系。

我们先来回顾一下RxJava的定义。

>A library for composing asynchronous and event-based programs using observable sequences for the Java VM

> 一个在 Java VM 上使用可观测的序列来组成异步的、基于事件的程序的库。

可以看出它是一套基于事件的发送与消费的库。

RxJava2依然以观察者模式为基本骨架，有两种观察者模式：

- Observable（被观察者）/ Observer（观察者）：不支持背压
- Flowable（被观察者）/ Subscriber（观察者）：支持背压

>背压（Backpressure）指的是在异步场景中，被观察者发送事件的速度远快于观察者的处理速度，观察者通知上游被观察者降低发送速度的策略。