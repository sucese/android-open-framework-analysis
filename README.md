# Android Open Framwork Analysis

本系列文章主要分析各类开源框架的实现，当然这些被选中的框架也是我们在技术选型中使用最广泛的框架。

注：以下都是应用非常广泛的开源框架，所以可以作为大家技术选型的参考。

## Networking

网络请求框架方面我们用的是khttp+etrofit，当然也有同学反映Retrofit使用起来不是很简便，这个时候你可以使用okhttp-OkGo，chuck等
这些封装库来简化调用过程。

[okhttp](https://github.com/square/okhttp): An HTTP+HTTP/2 client for Android and Java applications
[retrofit](https://github.com/square/retrofit): Type-safe HTTP client for Android and Java by Square, Inc.

## Image Loader

图片加载框架方面当然是功能强大的Fresco。

[fresco](https://github.com/facebook/fresco): https://github.com/facebook/fresco

## O/R Mapping

数据库方面，由于Realm高效易用的特性，以及它对各大平台的支持，使得它成为数据库框架方面非常好的选择。

[realm-java](https://github.com/realm/realm-java): Realm is a mobile database: a replacement for SQLite & ORMs

## Dependency Injection

依赖注入我们使用最多的便是View注入框架ButterKnife以及依赖注入框架Dagger。

[ButterKnife](https://github.com/JakeWharton/butterknife): Bind Android views and callbacks to fields and methods

[Dagger](https://github.com/google/dagger): A fast dependency injector for Android and Java

## RxJava

RxJava系列主要用来完成异步编程。

[RxJava](https://github.com/ReactiveX/RxJava): A library for composing asynchronous and event-based programs using observable sequences for the Java VM.

[RxAndroid](https://github.com/ReactiveX/RxAndroid): RxJava bindings for Android

## JSON

在JSON解析上我们主要用的是gson，当然fastjson等其他框架也是不错的选择。

[gson](https://github.com/google/gson)：A Java serialization/deserialization library to convert Java Objects into JSON and back

## JavaScript

随着前端技术的发展，跨平台开发技术也逐渐成熟起来，常见的跨平台框架有：

- React Native
- Weex

我们公司目前使用的React Native。

[react-native](https://github.com/facebook/react-native): A framework for building native apps with React

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
