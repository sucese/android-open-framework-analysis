# Okhttp源码篇：请求的发送与拦截流程

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

我们还是从这个例子入手

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

我们通过创建OkHttpClient来发起Http请求，每个OkHttpClient都持有自己的线程池与连接池，为了更好的性能，我们通常把OkHttpClient
写成单例，共享使用。


### 1 OkHttpClient.newCall(Request request) 

```java
public class OkHttpClient implements Cloneable, Call.Factory, WebSocket.Factory {
    @Override public Call newCall(Request request) {
      return new RealCall(this, request, false /* for web socket */);
    }
  
}
```
可以看出，真正的请求被包装成了RealCall。我们来看看RealCall的实现。

### 2 RealCall(OkHttpClient client, Request originalRequest, boolean forWebSocket)

```java
final class RealCall implements Call {
    
    RealCall(OkHttpClient client, Request originalRequest, boolean forWebSocket) {
      final EventListener.Factory eventListenerFactory = client.eventListenerFactory();
  
      this.client = client;
      this.originalRequest = originalRequest;
      this.forWebSocket = forWebSocket;
      this.retryAndFollowUpInterceptor = new RetryAndFollowUpInterceptor(client, forWebSocket);
  
      // TODO(jwilson): this is unsafe publication and not threadsafe.
      this.eventListener = eventListenerFactory.create(this);
    }  
}
```
RealCall实现了Call接口，它封装了请求的调用，这个构造函数的逻辑也很简单：赋值外部传入的OkHttpClient、Request与forWebSocket，并
创建了重试与重定向拦截器RetryAndFollowUpInterceptor。

从上面的例子可以看出，Okhttp发送请求有两种方式：

- 同步：execute()
- 异步：enqueue(Callback callback)

### 3 RealCall.enqueue(Callback responseCallback)/RealCall.execute()

异步请求

```java
final class RealCall implements Call {
    
      @Override public void enqueue(Callback responseCallback) {
        synchronized (this) {
          if (executed) throw new IllegalStateException("Already Executed");
          executed = true;
        }
        captureCallStackTrace();
        client.dispatcher().enqueue(new AsyncCall(responseCallback));
      }
}
```
同步请求

```java
final class RealCall implements Call {
    @Override public Response execute() throws IOException {
      synchronized (this) {
        if (executed) throw new IllegalStateException("Already Executed");
        executed = true;
      }
      captureCallStackTrace();
      try {
        client.dispatcher().executed(this);
        Response result = getResponseWithInterceptorChain();
        if (result == null) throw new IOException("Canceled");
        return result;
      } finally {
        client.dispatcher().finished(this);
      }
    }
}
```
从上面实现可以看出，不管是同步请求还是异步请求都是Dispatcher在处理：

- 同步请求：直接执行，并返回请求结果
- 异步请求：构造一个AsyncCall，并将自己加入处理队列中。

AsyncCall本质上是一个Runable，Dispatcher会调度ExecutorService来执行这些Runable。

```java
final class AsyncCall extends NamedRunnable {
    private final Callback responseCallback;

    AsyncCall(Callback responseCallback) {
      super("OkHttp %s", redactedUrl());
      this.responseCallback = responseCallback;
    }

    String host() {
      return originalRequest.url().host();
    }

    Request request() {
      return originalRequest;
    }

    RealCall get() {
      return RealCall.this;
    }

    @Override protected void execute() {
      boolean signalledCallback = false;
      try {
        Response response = getResponseWithInterceptorChain();
        if (retryAndFollowUpInterceptor.isCanceled()) {
          signalledCallback = true;
          responseCallback.onFailure(RealCall.this, new IOException("Canceled"));
        } else {
          signalledCallback = true;
          responseCallback.onResponse(RealCall.this, response);
        }
      } catch (IOException e) {
        if (signalledCallback) {
          // Do not signal the callback twice!
          Platform.get().log(INFO, "Callback failure for " + toLoggableString(), e);
        } else {
          responseCallback.onFailure(RealCall.this, e);
        }
      } finally {
        client.dispatcher().finished(this);
      }
    }
  }

```

从上面代码可以看出，不管是同步请求还是异步请求最后都会通过getResponseWithInterceptorChain()获取Response，只不过异步请求多了个线程调度，异步
执行的过程。

我们先来来看看Dispatcher里的实现。

### 4 Dispatcher.enqueue(AsyncCall call)/Dispatcher.executed(RealCall call)

```java
public final class Dispatcher {
    
      private int maxRequests = 64;
      private int maxRequestsPerHost = 5;
    
      /** Ready async calls in the order they'll be run. */
      private final Deque<AsyncCall> readyAsyncCalls = new ArrayDeque<>();
    
      /** Running asynchronous calls. Includes canceled calls that haven't finished yet. */
      private final Deque<AsyncCall> runningAsyncCalls = new ArrayDeque<>();
    
      /** Running synchronous calls. Includes canceled calls that haven't finished yet. */
      private final Deque<RealCall> runningSyncCalls = new ArrayDeque<>();
 
        synchronized void enqueue(AsyncCall call) {
            
          //正在运行的异步请求不得超过64，同一个host下的异步请求不得超过5个
          if (runningAsyncCalls.size() < maxRequests && runningCallsForHost(call) < maxRequestsPerHost) {
            runningAsyncCalls.add(call);
            executorService().execute(call);
          } else {
            readyAsyncCalls.add(call);
          }
        }
}
```


```java
public final class Dispatcher {
    
      /** Ready async calls in the order they'll be run. */
      private final Deque<AsyncCall> readyAsyncCalls = new ArrayDeque<>();
    
      /** Running asynchronous calls. Includes canceled calls that haven't finished yet. */
      private final Deque<AsyncCall> runningAsyncCalls = new ArrayDeque<>();
    
      /** Running synchronous calls. Includes canceled calls that haven't finished yet. */
      private final Deque<RealCall> runningSyncCalls = new ArrayDeque<>();
      
        /** Used by {@code Call#execute} to signal it is in-flight. */
        synchronized void executed(RealCall call) {
          runningSyncCalls.add(call);
        }
}
```
Dispatcher是一个任务调度器，它内部维护了三个双端队列：

- readyAsyncCalls：准备运行的异步请求
- runningAsyncCalls：正在运行的异步请求
- runningSyncCalls：正在运行的同步请求

记得异步请求与同步骑牛，并利用ExecutorService来调度执行AsyncCall。

同步请求就直接把请求添加到正在运行的同步请求队列runningSyncCalls中，异步请求会做个判断：

如果正在运行的异步请求不超过64，而且同一个host下的异步请求不得超过5个则将请求添加到正在运行的同步请求队列中runningAsyncCalls并开始
执行请求，否则就添加到readyAsyncCalls继续等待。

讲完Dispatcher里的实现，我们继续来看getResponseWithInterceptorChain()的实现，这个方法才是真正发起请求并处理请求的地方。

### 5 RealCall.getResponseWithInterceptorChain()

```java
final class RealCall implements Call {
      Response getResponseWithInterceptorChain() throws IOException {
        // Build a full stack of interceptors.
        List<Interceptor> interceptors = new ArrayList<>();
        interceptors.addAll(client.interceptors());
        interceptors.add(retryAndFollowUpInterceptor);
        interceptors.add(new BridgeInterceptor(client.cookieJar()));
        interceptors.add(new CacheInterceptor(client.internalCache()));
        interceptors.add(new ConnectInterceptor(client));
        if (!forWebSocket) {
          interceptors.addAll(client.networkInterceptors());
        }
        interceptors.add(new CallServerInterceptor(forWebSocket));
    
        Interceptor.Chain chain = new RealInterceptorChain(
            interceptors, null, null, null, 0, originalRequest);
        return chain.proceed(originalRequest);
      }
}
```

短短几行代码，完成了对请求的所有处理过程，Interceptor将网络请求、缓存、透明压缩等功能统一了起来，它的实现采用责任链模式，各司其职，
每个功能都是一个Interceptor，上一级处理完成以后传递给下一级，它们最后连接成了一个Interceptor.Chain。它们的功能如下：

- RetryAndFollowUpInterceptor：负责失败重试以及重定向。
- BridgeInterceptor：负责把用户构造的请求转换为发送给服务器的请求，把服务器返回的响应转换为对用户友好的响应。
- CacheInterceptor：负责读取缓存以及更新缓存。
- ConnectInterceptor：负责与服务器建立连接。
- CallServerInterceptor：负责从服务器读取响应的数据。

位置决定功能，位置靠前的先执行，最后一个则复制与服务器通讯。

我们继续来看看RealInterceptorChain里是怎么一级级处理的。

### 6 RealInterceptorChain.proceed(Request request, StreamAllocation streamAllocation, HttpCodec httpCodec,RealConnection connection) 

```java
public final class RealInterceptorChain implements Interceptor.Chain {
    
     public Response proceed(Request request, StreamAllocation streamAllocation, HttpCodec httpCodec,
          RealConnection connection) throws IOException {
        if (index >= interceptors.size()) throw new AssertionError();
    
        calls++;
    
        // If we already have a stream, confirm that the incoming request will use it.
        if (this.httpCodec != null && !this.connection.supportsUrl(request.url())) {
          throw new IllegalStateException("network interceptor " + interceptors.get(index - 1)
              + " must retain the same host and port");
        }
    
        // If we already have a stream, confirm that this is the only call to chain.proceed().
        if (this.httpCodec != null && calls > 1) {
          throw new IllegalStateException("network interceptor " + interceptors.get(index - 1)
              + " must call proceed() exactly once");
        }
    
        // Call the next interceptor in the chain.
        RealInterceptorChain next = new RealInterceptorChain(
            interceptors, streamAllocation, httpCodec, connection, index + 1, request);
        Interceptor interceptor = interceptors.get(index);
        Response response = interceptor.intercept(next);
    
        // Confirm that the next interceptor made its required call to chain.proceed().
        if (httpCodec != null && index + 1 < interceptors.size() && next.calls != 1) {
          throw new IllegalStateException("network interceptor " + interceptor
              + " must call proceed() exactly once");
        }
    
        // Confirm that the intercepted response isn't null.
        if (response == null) {
          throw new NullPointerException("interceptor " + interceptor + " returned null");
        }
    
        return response;
      }
}
```

这个方法比较有意思，在调用proceed方法之后，会继续构建一个新的RealInterceptorChain，调用下一个interceptor来继续请求，直到所有interceptor都处理完毕，将
得到的response返回。

interceptor的执行顺序：RetryAndFollowUpInterceptor -> BridgeInterceptor -> CacheInterceptor -> ConnectInterceptor -> CallServerInterceptor。

我们分别来看看这5个拦截器的intercept()方法。


### 7 RetryAndFollowUpInterceptor.intercept(Chain chain) 

### 8 BridgeInterceptor.intercept(Chain chain) 

### 9 CacheInterceptor.intercept(Chain chain) 

### 10 ConnectInterceptor.intercept(Chain chain) 

### 11 CallServerInterceptor.intercept(Chain chain) 