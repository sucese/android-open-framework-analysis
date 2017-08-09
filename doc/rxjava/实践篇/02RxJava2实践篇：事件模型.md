# RxJava2实践篇：事件模型

**关于作者**

>郭孝星，非著名程序员，主要从事Android平台基础架构与中间件方面的工作，欢迎交流技术方面的问题，可以去我的[Github](https://github.com/guoxiaoxing)提交Issue或者发邮件至guoxiaoxingse@163.com与我联系。

我们先来回顾一下RxJava的定义。

>A library for composing asynchronous and event-based programs using observable sequences for the Java VM

> 一个在 Java VM 上使用可观测的序列来组成异步的、基于事件的程序的库。

可以看出它是一套基于事件的发送与消费的库。