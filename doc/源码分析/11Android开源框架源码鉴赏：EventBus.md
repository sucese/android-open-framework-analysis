# Android开源框架源码鉴赏：EventBus

**关于作者**

>郭孝星，程序员，吉他手，主要从事Android平台基础架构方面的工作，欢迎交流技术方面的问题，可以去我的[Github](https://github.com/guoxiaoxing)提issue或者发邮件至guoxiaoxingse@163.com与我交流。

EventBus是一个Android/Java平台基于订阅与发布的通信框架，可以用于Activities, Fragments, Threads, Services等组件的通信，也可以用于多线程通信。

EventBus在应用里的应用是十分广泛的，那么除了EventBus这种应用通信方式外，还有哪些手段呢？🤔

- BroadcastReceiver/LocalBroadcastReceiver：跨域广播和局域广播，跨域广播可以用来做跨进程通信。局域广播也是基于Handler实现，可以用来在应用内通信。
- Handler：这个方式的弊端在于通信消息难以管理。
- 接口回调：接口回调的好处是比较清晰明显，但是如果涉及到大量页面的跳转或者通信场景比较复杂，这种方式就变得难以维护，耦合较高。