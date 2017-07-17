# Okhttp源码篇：Okhttp源码概览

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

>An HTTP+HTTP/2 client for Android and Java applications.

官方网站：https://github.com/square/okhttp

源码版本：3.8.0

跟以往一样，我们先来看个例子，从例子入手，逐步分析Okhttp的实现。

**举例**

```java
/**
 * 发送Get请求-异步
 */
private fun getAsynchronization(url: String) {
    val okhttpClient: OkHttpClient = OkHttpClient.Builder().build()
    val request: Request = Request.Builder()
            .url(url)
            .build()
    okhttpClient.newCall(request).enqueue(object : Callback {

        override fun onFailure(call: Call?, e: IOException?) {
            Log.d(App.TAG, e.toString())
        }

        override fun onResponse(call: Call?, response: Response?) {
            Log.d(App.TAG, response?.body()?.string())
        }
    })
}

/**
 * 发送Get请求-同步
 */
private fun getSynchronization(url: String) {
    val okhttpClient: OkHttpClient = OkHttpClient.Builder().build()
    val request: Request = Request.Builder()
            .url(url)
            .build()
    val response: Response = okhttpClient.newCall(request).execute()
}
```

从这个例子我们可以看出，和我们打交道的有这么几个角色：

- OkHttpClient
- RequestBody
- Request
- Response

我们来分别介绍下这几个角色以及与它们相关的类与接口。

OkHttpClient

>通信的客户端，用来统一管理发起请求与解析响应。

Call

>Call是HTTP请求的抽象描述，具体实现类是RealCall，它由CallFactory创建。


```java
public interface Call extends Cloneable {
    
  //返回当前请求
  Request request();

  //同步请求方法，此方法会阻塞当前线程知道请求结果放回
  Response execute() throws IOException;

  //异步请求方法，此方法会将请求添加到队列中，然后等待请求返回
  void enqueue(Callback responseCallback);

  //取消请求
  void cancel();

  //请求是否在执行，当execute()或者enqueue(Callback responseCallback)执行后该方法返回true
  boolean isExecuted();

  //请求是否被取消
  boolean isCanceled();

  //创建一个新的一模一样的请求
  Call clone();

  interface Factory {
    Call newCall(Request request);
  }
}
```

Interceptor

>Interceptor是请求拦截器，负责拦截并处理请求，它将网络请求、缓存、透明压缩等功能都统一起来，每个功能都是一个Interceptor，所有的
Interceptor最终连接成一个Interceptor.Chain。典型的责任链模式实现。

- RetryAndFollowUpInterceptor：负责失败重试以及重定向。
- BridgeInterceptor：负责把用户构造的请求转换为发送给服务器的请求，把服务器返回的响应转换为对用户友好的响应。
- CacheInterceptor：负责读取缓存以及更新缓存。
- ConnectInterceptor：负责与服务器建立连接。
- CallServerInterceptor：负责从服务器读取响应的数据。

```java
public interface Interceptor {
  Response intercept(Chain chain) throws IOException;

  interface Chain {
    Request request();

    Response proceed(Request request) throws IOException;
    
    //返回Request执行后返回的连接
    @Nullable Connection connection();
  }
}

```

Connection

