# Android Open Framwork Analysis

本系列文章主要分析各类开源框架的实现，当然这些被选中的框架也是我们在技术选型中使用最广泛的框架。

注：以下都是应用非常广泛的开源框架，可以作为大家技术选型的参考。另外，由于精力有限，不可能对每个框架都做细致的分析
，这些会挑选笔者有过深入实践，理解相对比较深入的框架进行分析。

## Networking

网络请求框架方面我们用的是khttp+etrofit，当然也有同学反映Retrofit使用起来不是很简便，这个时候你可以使用okhttp-OkGo，chuck等
这些封装库来简化调用过程。

- okhttp: An HTTP+HTTP/2 client for Android and Java applications
- retrofit: Type-safe HTTP client for Android and Java by Square, Inc.

[okhttp](https://github.com/square/okhttp)
[retrofit](https://github.com/square/retrofit)

## Image Loader

图片加载框架方面当然是功能强大的Fresco。

[fresco](https://github.com/facebook/fresco): https://github.com/facebook/fresco

## O/R Mapping

数据库方面，由于Realm高效易用的特性，以及它对各大平台的支持，使得它成为数据库框架方面非常好的选择。

[realm-java](https://github.com/realm/realm-java): Realm is a mobile database: a replacement for SQLite & ORMs

## Dependency Injection

依赖注入是为了解耦，常见的依赖注入框架有：

- ButterKnife: Bind Android views and callbacks to fields and methods
- Dagger: A fast dependency injector for Android and Java

[ButterKnife](https://github.com/JakeWharton/butterknife)

[Dagger](https://github.com/google/dagger)

## RxJava

RxJava系列主要用来完成异步编程，对于Android开发而言常用的框架有：

- RxJava: A library for composing asynchronous and event-based programs using observable sequences for the Java VM.
- RxAndroid: RxJava bindings for Android

[RxJava](https://github.com/ReactiveX/RxJava)

[RxAndroid](https://github.com/ReactiveX/RxAndroid)

## JSON

JSON序列化与反序列化也是非常常见的操作，JSON解析常见的框架有：

- gson：A Java serialization/deserialization library to convert Java Objects into JSON and back
- fastjson: A fast JSON parser/generator for Java 

[gson](https://github.com/google/gson)

## JavaScript

随着前端技术的发展，跨平台开发技术也逐渐成熟起来，常见的跨平台框架有：

- React Native: A framework for building native apps with React
- Weex: A framework for building Mobile cross-platform UI

我们公司目前使用的React Native。

[react-native](https://github.com/facebook/react-native)

## Android Plugin

Android的插件化技术也是近两年很火热的技术，涌现了很多优秀的框架。主流的插件化框架有：

- DynamicAPK
- AndroidDynamicLoader
- dynamic-load-apk
- android-pluginmgr
- DroidPlugin

[DroidPlugin](https://github.com/DroidPluginTeam/DroidPlugin)：A plugin framework on android,Run any third-party apk without installation, modification or repackage

## Android Hot Fix

客户端并不能像前端那样即写即发，所以热修复技术就显得非常有价值，尤其是在遭遇重大线上bug的时候，主流的热修复框架有：

- Dexposed
- AndFix
- Nuwa
- HotFix
- DroidFix
- AnoleFix
- Amigo
- Tinker
