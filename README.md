# Android Open Framwork Analysis

**关于作者**

>郭孝星，非著名程序员，主要从事Android平台基础架构与中间件方面的工作，热爱编程，技术栈主要涉及以下几个方面
>
>- Android/Linux
>- Java/Kotlin/JVM
>- Python
>- JavaScript/React/ReactNative
>- DataStructure/Algorithm
>
>文章首发于[Github](https://github.com/guoxiaoxing)，后续也会同步在[简书](http://www.jianshu.com/users/66a47e04215b/latest_articles)与
[CSDN](http://blog.csdn.net/allenwells)等博客平台上。文章中如果有什么问题，欢迎发邮件与我交流，邮件可发至guoxiaoxingse@163.com。

本系列文章主要分析Android平台各类主流开源框架的原理与实践。

|库                                       |描述                                            |
|:----------------------------------------|:----------------------------------------------|
|[okio](https://github.com/square/okio)|A modern I/O API for Java
|[okhttp](https://github.com/square/okhttp)|An HTTP+HTTP/2 client for Android and Java applications
|[retrofit](https://github.com/square/retrofit)|Type-safe HTTP client for Android and Java by Square, Inc
|[gson](https://github.com/google/gson)|A Java serialization/deserialization library to convert Java Objects into JSON and back
|[fresco](https://github.com/facebook/fresco)|An Android library for managing images and the memory they use
|[realm-java](https://github.com/realm/realm-java)|Realm is a mobile database: a replacement for SQLite & ORMs
|[RxJava](https://github.com/ReactiveX/RxJava)|A library for composing asynchronous and event-based programs using observable sequences for the Java VM
|[react-native](https://github.com/facebook/react-native)|A framework for building native apps with React
|[DiskLruCache](https://github.com/JakeWharton/DiskLruCache)|Java implementation of a Disk-based LRU cache which specifically targets Android compatibility
|[leakcanary](https://github.com/square/leakcanary)|A memory leak detection library for Android and Java

**okhttp**

- [01Okhttp源码篇：源码概览](https://github.com/guoxiaoxing/android-open-framwork-analysis/blob/master/doc/okhttp/01Okhttp源码篇：源码概览.md)
- [02Okhttp源码篇：请求流程](https://github.com/guoxiaoxing/android-open-framwork-analysis/blob/master/doc/okhttp/02Okhttp源码篇：请求流程.md)
- [03Okhttp源码篇：缓存机制](https://github.com/guoxiaoxing/android-open-framwork-analysis/blob/master/doc/okhttp/03Okhttp源码篇：缓存机制.md)
- [04Okhttp源码篇：连接机制](https://github.com/guoxiaoxing/android-open-framwork-analysis/blob/master/doc/okhttp/04Okhttp源码篇：连接机制.md)
- [05Okhttp源码篇：编解码机制](https://github.com/guoxiaoxing/android-open-framwork-analysis/blob/master/doc/okhttp/05Okhttp源码篇：编解码机制.md)
- [06Okhttp源码篇：HTTP协议](https://github.com/guoxiaoxing/android-open-framwork-analysis/blob/master/doc/okhttp/06Okhttp源码篇：HTTP协议.md)
- [07Okhttp源码篇：WebSocket协议](https://github.com/guoxiaoxing/android-open-framwork-analysis/blob/master/doc/okhttp/07Okhttp源码篇：WebSocket协议.md)