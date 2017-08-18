# RxJava实践篇：RxJava2概述

**关于作者**

>郭孝星，非著名程序员，主要从事Android平台基础架构与中间件方面的工作，欢迎交流技术方面的问题，可以去我的[Github](https://github.com/guoxiaoxing)提交Issue或者发邮件至guoxiaoxingse@163.com与我联系。

>A library for composing asynchronous and event-based programs using observable sequences for the Java VM.

要更深入的理解RxJava/RxAndriod的实践原理，我们就要先理解ReactiveX相关基础理论。

>ReactiveX是Reactive Extensions的缩写，它是一个可以使用可观察的数据流进行异步编程的编程接口，它结合了观察者模式，迭代器模式与函数式编程的精华。

-[ReactiveX官方网站](http://reactivex.io/)

说到ReactiveX我们不得不提一下函数式编程，ReactiveX就是函数式编程的一种良好实践。

>函数式编程同命令式编程，面向对象编程一样，也是编程范式的一种，说的是一种编程方法，将函数视为一等公民，将所有计算都描述为一种表达式
求值，函数可以在任何地方定义和调用，就像我们定义变量那样，我们还可以对函数进行任意组合。

更多关于函数式编程的讨论可以参见：

- [函数式编程初探](http://www.ruanyifeng.com/blog/2012/04/functional_programming.html)
- [什么是函数式编程思维](https://www.zhihu.com/question/28292740)

ReactiveX的优点：

- 扩展了观察者模式用于支持数据与事件序列，添加了一些操作符用于组合和操作这些序列，而无需去关注底层的机制，例如：线程、同步、线程安全、并发数据结构与非阻塞IO等。
- 函数式编程风格，对可观察数据流使用无副作用的输入输出函数，避免了程序里错综复杂的状态。
- 解决了多层回调的痛点，我们通常对于异步的实现就是"接口+回调"的方式，这在单层异步里是可用的，但是面对嵌套的异步组合就显得十分笨拙。😄

```
那一天人们又回想起了都多层回调与迷之缩进支配的恐惧。☹️
```


## 附录

- [Rxjava官方文档](http://reactivex.io/intro.html)
- [ReactiveX官方网站](http://reactivex.io/RxJava/2.x/javadoc/overview-summary.html)
- [ReactiveX官方文档中文版](https://mcxiaoke.gitbooks.io/rxdocs/content/Intro.html)