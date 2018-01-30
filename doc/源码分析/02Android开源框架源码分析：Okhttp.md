# Android开源框架源码分析：Okhttp

**关于作者**

>郭孝星，程序员，吉他手，主要从事Android平台基础架构方面的工作，欢迎交流技术方面的问题，可以去我的[Github](https://github.com/guoxiaoxing)提issue或者发邮件至guoxiaoxingse@163.com与我交流。

**文章目录**

- 一 请求与响应流程
    - 1.1 请求的封装
    - 1.2 请求的发送
    - 1.3 请求的调度
- 二 拦截器
    - 2.1 RetryAndFollowUpInterceptor
    - 2.2 BridgeInterceptor
    - 2.3 CacheInterceptor
    - 2.4 ConnectInterceptor
    - 2.5 CallServerInterceptor
- 三 连接机制
    - 3.1 建立连接
    - 3.2 连接池
- 四 缓存机制
    - 4.1 缓存策略
    - 4.2 缓存管理

在Android刀耕火种的哪个年代，我们做网络请求通常会选用HttpURLConnection或者Apache HTTP Client，这两者均支持HTTPS、流的上传和下载、配置超时和连接池等特性，但随着业务场景的负责化以及
对流量消耗的优化需求，Okhttp应运而生，自诞生起，口碑就一直很好。

但是，大家都说好，好在哪里？既然这么好，它的设计理念和实现思路有哪些值得我们学习的地方？🤔

今天就带着这些问题，一探究竟。

>An HTTP+HTTP/2 client for Android and Java applications.

官方网站：https://github.com/square/okhttp

源码版本：3.9.1

在正式分析源码之前，我们先来看个简单的小例子，从例子入手，逐步分析Okhttp的实现。

👉 举例

```java
OkHttpClient okHttpClient = new OkHttpClient.Builder()
        .build();
Request request = new Request.Builder()
        .url(url)
        .build();
okHttpClient.newCall(request).enqueue(new Callback() {
    @Override
    public void onFailure(Call call, IOException e) {
    }

    @Override
    public void onResponse(Call call, Response response) throws IOException {

    }
});
```

在上面的例子中，我们构建了一个客户端OkHttpClient和一个请求Request，然后调用newCall()方法将请求发送了出去。从这个小例子中，我们可以发现
OkHttpClient相当于是个上下文或者说是大管家，它接到我们给的任务以后，将具体的工作分发到各个子系统中去完成。

Okhttp的子系统层级结构图如下所示：

👉 点击图片查看大图

<img src="https://github.com/guoxiaoxing/android-open-framwork-analysis/raw/master/art/okhttp/okhttp_structure.png" width="600"/>

- 网络配置层：利用Builder模式配置各种参数，例如：超时时间、拦截器等，这些参数都会由Okhttp分发给各个需要的子系统。
- 重定向层：负责重定向。
- Header拼接层：负责把用户构造的请求转换为发送给服务器的请求，把服务器返回的响应转换为对用户友好的响应。
- HTTP缓存层：负责读取缓存以及更新缓存。
- 连接层：连接层是一个比较复杂的层级，它实现了网络协议、内部的拦截器、安全性认证，连接与连接池等功能，但这一层还没有发起真正的连接，它只是做了连接器一些参数的处理。
- 数据响应层：负责从服务器读取响应的数据。

在整个Okhttp的系统中，我们还要理解以下几个关键角色：

- OkHttpClient：通信的客户端，用来统一管理发起请求与解析响应。
- Call：Call是一个接口，它是HTTP请求的抽象描述，具体实现类是RealCall，它由CallFactory创建。
- Request：请求，封装请求的具体信息，例如：url、header等。
- RequestBody：请求体，用来提交流、表单等请求信息。
- Response：HTTP请求的响应，获取响应信息，例如：响应header等。
- ResponseBody：HTTP请求的响应体，被读取一次以后就会关闭，所以我们重复调用responseBody.string()获取请求结果是会报错的。
- Interceptor：Interceptor是请求拦截器，负责拦截并处理请求，它将网络请求、缓存、透明压缩等功能都统一起来，每个功能都是一个Interceptor，所有的Interceptor最
终连接成一个Interceptor.Chain。典型的责任链模式实现。
- StreamAllocation：用来控制Connections与Streas的资源分配与释放。
- RouteSelector：选择路线与自动重连。
- RouteDatabase：记录连接失败的Route黑名单。

我们首先来分析连接的请求与响应流程，这样我们就可以对整个Okhttp系统有一个整体的认识。

## 一 请求与响应流程

Okhttp的整个请求与响应的流程就是Dispatcher不断从Request Queue里取出请求（Call），根据是否已经存存缓存，从内存缓存或者服务器获取请求的数据，请求分为同步和异步两种，同步请求通过
调用Call.exectute()方法直接返回当前请求的Response，异步请求调用Call.enqueue()方法将请求（AsyncCall）添加到请求队列中去，并通过回调（Callback）获取服务器返回的结果。

一图胜千言，我们来看一下整个的流程图，如下所示：

👉 点击图片查看大图

<img src="https://github.com/guoxiaoxing/android-open-framwork-analysis/raw/master/art/okhttp/request_and_response_structure.png"/>

读者仔细看一下这个流程图，是不是很像计算机网络的OSI七层模型，Okhttp正式采用这种思路，利用拦截器Interceptor将整套框架纵向分层，简化了设计逻辑，提升了框架扩展性。

通过上面的流程图，我们可以知道在整个请求与响应流程中，以下几点是我们需要重点关注的：

- Dispatcher是如何进行请求调度的？
- 各个拦截器是如何实现的？
- 连接与连接池是如何建立和维护的？

带着以上问题，我们去源码中一探究竟。

我们先来看一下具体的函数调用链，请求与响应的序列图如下所示：

👉 点击图片查看大图

<img src="https://github.com/guoxiaoxing/android-open-framwork-analysis/raw/master/art/okhttp/request_and_response_sequence.png"/>

上述序列图可以帮助我们理解整个请求与响应流程的具体细节，我们首先来看一下一个请求和如何被封装并发出的。

### 1.1 请求的封装

请求是由Okhttp发出，真正的请求都被封装了在了接口Call的实现类RealCall中，如下所示：

Call接口如下所示：

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

RealCall的构造方法如下所示：

```java
final class RealCall implements Call {
    
  private RealCall(OkHttpClient client, Request originalRequest, boolean forWebSocket) {
    //我们构建的OkHttpClient，用来传递参数
    this.client = client;
    this.originalRequest = originalRequest;
    //是不是WebSocket请求，WebSocket是用来建立长连接的，后面我们会说。
    this.forWebSocket = forWebSocket;
    //构建RetryAndFollowUpInterceptor拦截器
    this.retryAndFollowUpInterceptor = new RetryAndFollowUpInterceptor(client, forWebSocket);
  }
}
```
RealCall实现了Call接口，它封装了请求的调用，这个构造函数的逻辑也很简单：赋值外部传入的OkHttpClient、Request与forWebSocket，并
创建了重试与重定向拦截器RetryAndFollowUpInterceptor。

### 1.2 请求的发送

RealCall将请求分为两种：

- 同步请求
- 异步请求

异步请求只是比同步请求多了个Callback，分别调用的方法如下所示：

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

### 1.3 请求的调度

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
      
      /** Used by {@code Call#execute} to signal it is in-flight. */
      synchronized void executed(RealCall call) {
        runningSyncCalls.add(call);
      }

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
Dispatcher是一个任务调度器，它内部维护了三个双端队列：

- readyAsyncCalls：准备运行的异步请求
- runningAsyncCalls：正在运行的异步请求
- runningSyncCalls：正在运行的同步请求

记得异步请求与同步骑牛，并利用ExecutorService来调度执行AsyncCall。

同步请求就直接把请求添加到正在运行的同步请求队列runningSyncCalls中，异步请求会做个判断：

如果正在运行的异步请求不超过64，而且同一个host下的异步请求不得超过5个则将请求添加到正在运行的同步请求队列中runningAsyncCalls并开始
执行请求，否则就添加到readyAsyncCalls继续等待。

讲完Dispatcher里的实现，我们继续来看getResponseWithInterceptorChain()的实现，这个方法才是真正发起请求并处理请求的地方。

### 1.4 请求的处理

```java
final class RealCall implements Call {
      Response getResponseWithInterceptorChain() throws IOException {
        // Build a full stack of interceptors.
        List<Interceptor> interceptors = new ArrayList<>();
        //这里可以看出，我们自定义的Interceptor会被优先执行
        interceptors.addAll(client.interceptors());
        //添加重试和重定向烂机器
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

- RetryAndFollowUpInterceptor：负责重定向。
- BridgeInterceptor：负责把用户构造的请求转换为发送给服务器的请求，把服务器返回的响应转换为对用户友好的响应。
- CacheInterceptor：负责读取缓存以及更新缓存。
- ConnectInterceptor：负责与服务器建立连接。
- CallServerInterceptor：负责从服务器读取响应的数据。

位置决定功能，位置靠前的先执行，最后一个则复制与服务器通讯，请求从RetryAndFollowUpInterceptor开始层层传递到CallServerInterceptor，每一层
都对请求做相应的处理，处理的结构再从CallServerInterceptor层层返回给RetryAndFollowUpInterceptor，最红请求的发起者获得了服务器返回的结果。

以上便是Okhttp整个请求与响应的具体流程，可以发现拦截器才是Okhttp核心功能所在，我们来逐一分析每个拦截器的实现。

## 二 拦截器

从上面的流程可以看出，各个环节都是由相应的拦截器进行处理，所有的拦截器（包括我们自定义的）都实现了Interceptor接口，如下所示：

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
Okhttp内置的拦截器如下所示：

- RetryAndFollowUpInterceptor：负责失败重试以及重定向。
- BridgeInterceptor：负责把用户构造的请求转换为发送给服务器的请求，把服务器返回的响应转换为对用户友好的响应。
- CacheInterceptor：负责读取缓存以及更新缓存。
- ConnectInterceptor：负责与服务器建立连接。
- CallServerInterceptor：负责从服务器读取响应的数据。

我们继续来看看RealInterceptorChain里是怎么一级级处理的。

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

这个方法比较有意思，在调用proceed方法之后，会继续构建一个新的RealInterceptorChain对象，调用下一个interceptor来继续请求，直到所有interceptor都处理完毕，将
得到的response返回。

每个拦截器的方法都遵循这样的规则：

```java
@Override public Response intercept(Chain chain) throws IOException {
    Request request = chain.request();
    //1 Request阶段，该拦截器在Request阶段负责做的事情

    //2 调用RealInterceptorChain.proceed()，其实是在递归调用下一个拦截器的intercept()方法
    response = ((RealInterceptorChain) chain).proceed(request, streamAllocation, null, null);

    //3 Response阶段，完成了该拦截器在Response阶段负责做的事情，然后返回到上一层的拦截器。
    return response;     
    }
  }
```
从上面的描述可知，Request是按照interpretor的顺序正向处理，而Response是逆向处理的。这参考了OSI七层模型的原理。上面我们也提到过。CallServerInterceptor相当于最底层的物理层，
请求从上到逐层包装下发，响应从下到上再逐层包装返回。很漂亮的设计。

interceptor的执行顺序：RetryAndFollowUpInterceptor -> BridgeInterceptor -> CacheInterceptor -> ConnectInterceptor -> CallServerInterceptor。

## 2.1 RetryAndFollowUpInterceptor

RetryAndFollowUpInterceptor负责失败重试以及重定向。

```java
public final class RetryAndFollowUpInterceptor implements Interceptor {
    
    private static final int MAX_FOLLOW_UPS = 20;
    
     @Override public Response intercept(Chain chain) throws IOException {
        Request request = chain.request();
    
        //1. 构建一个StreamAllocation对象，StreamAllocation相当于是个管理类，维护了
        //Connections、Streams和Calls之间的管理，该类初始化一个Socket连接对象，获取输入/输出流对象。
        streamAllocation = new StreamAllocation(
            client.connectionPool(), createAddress(request.url()), callStackTrace);
    
        //重定向次数
        int followUpCount = 0;
        Response priorResponse = null;
        while (true) {
          if (canceled) {
            streamAllocation.release();
            throw new IOException("Canceled");
          }
    
          Response response = null;
          boolean releaseConnection = true;
          try {
            //2. 继续执行下一个Interceptor，即BridgeInterceptor
            response = ((RealInterceptorChain) chain).proceed(request, streamAllocation, null, null);
            releaseConnection = false;
          } catch (RouteException e) {
            //3. 抛出异常，则检测连接是否还可以继续。
            if (!recover(e.getLastConnectException(), false, request)) {
              throw e.getLastConnectException();
            }
            releaseConnection = false;
            continue;
          } catch (IOException e) {
            // 和服务端建立连接失败
            boolean requestSendStarted = !(e instanceof ConnectionShutdownException);
            if (!recover(e, requestSendStarted, request)) throw e;
            releaseConnection = false;
            continue;
          } finally {
            //检测到其他未知异常，则释放连接和资源
            if (releaseConnection) {
              streamAllocation.streamFailed(null);
              streamAllocation.release();
            }
          }
    
          //构建响应体，这个响应体的body为空。
          if (priorResponse != null) {
            response = response.newBuilder()
                .priorResponse(priorResponse.newBuilder()
                        .body(null)
                        .build())
                .build();
          }
    
          //4。根据响应码处理请求，返回Request不为空时则进行重定向处理。
          Request followUp = followUpRequest(response);
    
          if (followUp == null) {
            if (!forWebSocket) {
              streamAllocation.release();
            }
            return response;
          }
    
          closeQuietly(response.body());
    
          //重定向的次数不能超过20次
          if (++followUpCount > MAX_FOLLOW_UPS) {
            streamAllocation.release();
            throw new ProtocolException("Too many follow-up requests: " + followUpCount);
          }
    
          if (followUp.body() instanceof UnrepeatableRequestBody) {
            streamAllocation.release();
            throw new HttpRetryException("Cannot retry streamed HTTP body", response.code());
          }
    
          if (!sameConnection(response, followUp.url())) {
            streamAllocation.release();
            streamAllocation = new StreamAllocation(
                client.connectionPool(), createAddress(followUp.url()), callStackTrace);
          } else if (streamAllocation.codec() != null) {
            throw new IllegalStateException("Closing the body of " + response
                + " didn't close its backing stream. Bad interceptor?");
          }
    
          request = followUp;
          priorResponse = response;
        }
      }
      
    
 
}
```
我们先来说说StreamAllocation这个类的作用，这个类协调了三个实体类的关系：

- Connections：连接到远程服务器的物理套接字，这个套接字连接可能比较慢，所以它有一套取消机制。
- Streams：定义了逻辑上的HTTP请求/响应对，每个连接都定义了它们可以携带的最大并发流，HTTP/1.x每次只可以携带一个，HTTP/2每次可以携带多个。
- Calls：定义了流的逻辑序列，这个序列通常是一个初始请求以及它的重定向请求，对于同一个连接，我们通常将所有流都放在一个调用中，以此来统一它们的行为。

我们再来看看整个方法的流程：

1. 构建一个StreamAllocation对象，StreamAllocation相当于是个管理类，维护了Connections、Streams和Calls之间的管理，该类初始化一个Socket连接对象，获取输入/输出流对象。
2. 继续执行下一个Interceptor，即BridgeInterceptor
3. 抛出异常，则检测连接是否还可以继续，以下情况不会重试：

- 客户端配置出错不再重试
- 出错后，request body不能再次发送
- 发生以下Exception也无法恢复连接：
  - ProtocolException：协议异常
  - InterruptedIOException：中断异常
  - SSLHandshakeException：SSL握手异常
  - SSLPeerUnverifiedException：SSL握手未授权异常
- 没有更多线路可以选择
4。根据响应码处理请求，返回Request不为空时则进行重定向处理，重定向的次数不能超过20次。

最后是根据响应码来处理请求头，由followUpRequest()方法完成，具体如下所示：

```java
public final class RetryAndFollowUpInterceptor implements Interceptor {
      private Request followUpRequest(Response userResponse) throws IOException {
        if (userResponse == null) throw new IllegalStateException();
        Connection connection = streamAllocation.connection();
        Route route = connection != null
            ? connection.route()
            : null;
        int responseCode = userResponse.code();
    
        final String method = userResponse.request().method();
        switch (responseCode) {
          //407，代理认证
          case HTTP_PROXY_AUTH:
            Proxy selectedProxy = route != null
                ? route.proxy()
                : client.proxy();
            if (selectedProxy.type() != Proxy.Type.HTTP) {
              throw new ProtocolException("Received HTTP_PROXY_AUTH (407) code while not using proxy");
            }
            return client.proxyAuthenticator().authenticate(route, userResponse);
          //401，未经认证
          case HTTP_UNAUTHORIZED:
            return client.authenticator().authenticate(route, userResponse);
          //307，308
          case HTTP_PERM_REDIRECT:
          case HTTP_TEMP_REDIRECT:
            // "If the 307 or 308 status code is received in response to a request other than GET
            // or HEAD, the user agent MUST NOT automatically redirect the request"
            if (!method.equals("GET") && !method.equals("HEAD")) {
              return null;
            }
            // fall-through
          //300，301，302，303
          case HTTP_MULT_CHOICE:
          case HTTP_MOVED_PERM:
          case HTTP_MOVED_TEMP:
          case HTTP_SEE_OTHER:
              
            //客户端在配置中是否允许重定向
            if (!client.followRedirects()) return null;
    
            String location = userResponse.header("Location");
            if (location == null) return null;
            HttpUrl url = userResponse.request().url().resolve(location);
    
            // url为null，不允许重定向
            if (url == null) return null;
    
            //查询是否存在http与https之间的重定向
            boolean sameScheme = url.scheme().equals(userResponse.request().url().scheme());
            if (!sameScheme && !client.followSslRedirects()) return null;
    
            // Most redirects don't include a request body.
            Request.Builder requestBuilder = userResponse.request().newBuilder();
            if (HttpMethod.permitsRequestBody(method)) {
              final boolean maintainBody = HttpMethod.redirectsWithBody(method);
              if (HttpMethod.redirectsToGet(method)) {
                requestBuilder.method("GET", null);
              } else {
                RequestBody requestBody = maintainBody ? userResponse.request().body() : null;
                requestBuilder.method(method, requestBody);
              }
              if (!maintainBody) {
                requestBuilder.removeHeader("Transfer-Encoding");
                requestBuilder.removeHeader("Content-Length");
                requestBuilder.removeHeader("Content-Type");
              }
            }
    
            // When redirecting across hosts, drop all authentication headers. This
            // is potentially annoying to the application layer since they have no
            // way to retain them.
            if (!sameConnection(userResponse, url)) {
              requestBuilder.removeHeader("Authorization");
            }
    
            return requestBuilder.url(url).build();
          //408，超时
          case HTTP_CLIENT_TIMEOUT:
            // 408's are rare in practice, but some servers like HAProxy use this response code. The
            // spec says that we may repeat the request without modifications. Modern browsers also
            // repeat the request (even non-idempotent ones.)
            if (userResponse.request().body() instanceof UnrepeatableRequestBody) {
              return null;
            }
    
            return userResponse.request();
    
          default:
            return null;
        }
      }    
}
```
重定向会涉及到一些网络编程的知识，这里如果没有完成理解，你只要知道RetryAndFollowUpInterceptor的作用就是处理了一些连接异常以及重定向就可以了。我们接着来看看下一个BridgeInterceptor。

## 2.2 BridgeInterceptor

BridgeInterceptor就跟它的名字那样，它是一个连接桥，它负责把用户构造的请求转换为发送给服务器的请求，把服务器返回的响应转换为对用户友好的响应。
转换的过程就是添加一些服务端需要的header信息。

```java
public final class BridgeInterceptor implements Interceptor {
    @Override public Response intercept(Chain chain) throws IOException {
        Request userRequest = chain.request();
        Request.Builder requestBuilder = userRequest.newBuilder();
    
        RequestBody body = userRequest.body();
        if (body != null) {
          //1 进行Header的包装
          MediaType contentType = body.contentType();
          if (contentType != null) {
            requestBuilder.header("Content-Type", contentType.toString());
          }
    
          long contentLength = body.contentLength();
          if (contentLength != -1) {
            requestBuilder.header("Content-Length", Long.toString(contentLength));
            requestBuilder.removeHeader("Transfer-Encoding");
          } else {
            requestBuilder.header("Transfer-Encoding", "chunked");
            requestBuilder.removeHeader("Content-Length");
          }
        }
    
        if (userRequest.header("Host") == null) {
          requestBuilder.header("Host", hostHeader(userRequest.url(), false));
        }
    
        if (userRequest.header("Connection") == null) {
          requestBuilder.header("Connection", "Keep-Alive");
        }
    
        //这里有个坑：如果你在请求的时候主动添加了"Accept-Encoding: gzip" ，transparentGzip=false，那你就要自己解压，如果
        // 你没有吹解压，或导致response.string()乱码。
        // If we add an "Accept-Encoding: gzip" header field we're responsible for also decompressing
        // the transfer stream.
        boolean transparentGzip = false;
        if (userRequest.header("Accept-Encoding") == null && userRequest.header("Range") == null) {
          transparentGzip = true;
          requestBuilder.header("Accept-Encoding", "gzip");
        }
    
        //创建OkhttpClient配置的cookieJar
        List<Cookie> cookies = cookieJar.loadForRequest(userRequest.url());
        if (!cookies.isEmpty()) {
          requestBuilder.header("Cookie", cookieHeader(cookies));
        }
    
        if (userRequest.header("User-Agent") == null) {
          requestBuilder.header("User-Agent", Version.userAgent());
        }
    
        Response networkResponse = chain.proceed(requestBuilder.build());
    
        //解析服务器返回的Header，如果没有这事cookie，则不进行解析
        HttpHeaders.receiveHeaders(cookieJar, userRequest.url(), networkResponse.headers());
    
        Response.Builder responseBuilder = networkResponse.newBuilder()
            .request(userRequest);
    
        //判断服务器是否支持gzip压缩，如果支持，则将压缩提交给Okio库来处理
        if (transparentGzip
            && "gzip".equalsIgnoreCase(networkResponse.header("Content-Encoding"))
            && HttpHeaders.hasBody(networkResponse)) {
          GzipSource responseBody = new GzipSource(networkResponse.body().source());
          Headers strippedHeaders = networkResponse.headers().newBuilder()
              .removeAll("Content-Encoding")
              .removeAll("Content-Length")
              .build();
          responseBuilder.headers(strippedHeaders);
          responseBuilder.body(new RealResponseBody(strippedHeaders, Okio.buffer(responseBody)));
        }
    
        return responseBuilder.build();
      }
}
```

就跟它的名字描述的那样，它是一个桥梁，负责把用户构造的请求转换为发送给服务器的请求，把服务器返回的响应转换为对用户友好的响应。
在Request阶段配置用户信息，并添加一些请求头。在Response阶段，进行gzip解压。

这个方法主要是针对Header做了一些处理，这里主要提一下"Accept-Encoding", "gzip"，关于它有以下几点需要注意：

- 开发者没有添加Accept-Encoding时，自动添加Accept-Encoding: gzip
- 自动添加Accept-Encoding，会对request，response进行自动解压
- 手动添加Accept-Encoding，不负责解压缩
- 自动解压时移除Content-Length，所以上层Java代码想要contentLength时为-1
- 自动解压时移除 Content-Encoding
- 自动解压时，如果是分块传输编码，Transfer-Encoding: chunked不受影响。

BridgeInterceptor主要就是针对Header做了一些处理，我们接着来看CacheInterceptor。

## 2.3 CacheInterceptor

我们知道为了节省流量和提高响应速度，Okhttp是有自己的一套缓存机制的，CacheInterceptor就是用来负责读取缓存以及更新缓存的。

```java
public final class CacheInterceptor implements Interceptor {
    
     @Override public Response intercept(Chain chain) throws IOException {
         
        //1. 读取候选缓存，具体如何读取的我们下面会讲。
        Response cacheCandidate = cache != null
            ? cache.get(chain.request())
            : null;
    
        long now = System.currentTimeMillis();
    
        //2. 创建缓存策略，强制缓存、对比缓存等，关于缓存策略我们下面也会讲。
        CacheStrategy strategy = new CacheStrategy.Factory(now, chain.request(), cacheCandidate).get();
        Request networkRequest = strategy.networkRequest;
        Response cacheResponse = strategy.cacheResponse;
    
        if (cache != null) {
          cache.trackResponse(strategy);
        }
    
        if (cacheCandidate != null && cacheResponse == null) {
          closeQuietly(cacheCandidate.body());
        }
    
        //3. 根据策略，不使用网络，又没有缓存的直接报错，并返回错误码504。
        if (networkRequest == null && cacheResponse == null) {
          return new Response.Builder()
              .request(chain.request())
              .protocol(Protocol.HTTP_1_1)
              .code(504)
              .message("Unsatisfiable Request (only-if-cached)")
              .body(Util.EMPTY_RESPONSE)
              .sentRequestAtMillis(-1L)
              .receivedResponseAtMillis(System.currentTimeMillis())
              .build();
        }
    
        //4. 根据策略，不使用网络，有缓存的直接返回。
        if (networkRequest == null) {
          return cacheResponse.newBuilder()
              .cacheResponse(stripBody(cacheResponse))
              .build();
        }
    
        Response networkResponse = null;
        try {
          //5. 前面两个都没有返回，继续执行下一个Interceptor，即ConnectInterceptor。
          networkResponse = chain.proceed(networkRequest);
        } finally {
          //如果发生IO异常，则释放掉缓存
          if (networkResponse == null && cacheCandidate != null) {
            closeQuietly(cacheCandidate.body());
          }
        }
    
        //6. 接收到网络结果，如果响应code式304，则使用缓存，返回缓存结果。
        if (cacheResponse != null) {
          if (networkResponse.code() == HTTP_NOT_MODIFIED) {
            Response response = cacheResponse.newBuilder()
                .headers(combine(cacheResponse.headers(), networkResponse.headers()))
                .sentRequestAtMillis(networkResponse.sentRequestAtMillis())
                .receivedResponseAtMillis(networkResponse.receivedResponseAtMillis())
                .cacheResponse(stripBody(cacheResponse))
                .networkResponse(stripBody(networkResponse))
                .build();
            networkResponse.body().close();
    
            cache.trackConditionalCacheHit();
            cache.update(cacheResponse, response);
            return response;
          } else {
            closeQuietly(cacheResponse.body());
          }
        }
    
        //7. 读取网络结果。
        Response response = networkResponse.newBuilder()
            .cacheResponse(stripBody(cacheResponse))
            .networkResponse(stripBody(networkResponse))
            .build();
    
        //8. 对数据进行缓存。
        if (cache != null) {
          if (HttpHeaders.hasBody(response) && CacheStrategy.isCacheable(response, networkRequest)) {
            // Offer this request to the cache.
            CacheRequest cacheRequest = cache.put(response);
            return cacheWritingResponse(cacheRequest, response);
          }
    
          if (HttpMethod.invalidatesCache(networkRequest.method())) {
            try {
              cache.remove(networkRequest);
            } catch (IOException ignored) {
              // The cache cannot be written.
            }
          }
        }
    
        //9. 返回网络读取的结果。
        return response;
      }
}
```

整个方法的流程如下所示：

1. 读取候选缓存，具体如何读取的我们下面会讲。
2. 创建缓存策略，强制缓存、对比缓存等，关于缓存策略我们下面也会讲。
3. 根据策略，不使用网络，又没有缓存的直接报错，并返回错误码504。
4. 根据策略，不使用网络，有缓存的直接返回。
5. 前面两个都没有返回，继续执行下一个Interceptor，即ConnectInterceptor。
6. 接收到网络结果，如果响应code式304，则使用缓存，返回缓存结果。
7. 读取网络结果。
8. 对数据进行缓存。
9. 返回网络读取的结果。

我们再接着来看ConnectInterceptor。

### 2.4 ConnectInterceptor

在RetryAndFollowUpInterceptor里初始化了一个StreamAllocation对象，我们说在这个StreamAllocation对象里初始化了一个Socket对象用来做连接，但是并没有
真正的连接，等到处理完hader和缓存信息之后，才调用ConnectInterceptor来进行真正的连接

```java
public final class ConnectInterceptor implements Interceptor {
    
      @Override public Response intercept(Chain chain) throws IOException {
        RealInterceptorChain realChain = (RealInterceptorChain) chain;
        Request request = realChain.request();
        StreamAllocation streamAllocation = realChain.streamAllocation();
    
        boolean doExtensiveHealthChecks = !request.method().equals("GET");
        //创建输出流
        HttpCodec httpCodec = streamAllocation.newStream(client, doExtensiveHealthChecks);
        //建立连接
        RealConnection connection = streamAllocation.connection();
    
        return realChain.proceed(request, streamAllocation, httpCodec, connection);
      }
}
```

ConnectInterceptor在Request阶段建立连接，处理方式也很简单，创建了两个对象：

- HttpCodec：用来编码HTTP requests和解码HTTP responses
- RealConnection：连接对象，负责发起与服务器的连接。

这里事实上包含了连接、连接池等一整套的Okhttp的连接机制，我们放在下面单独讲，先来继续看最后一个Interceptor：CallServerInterceptor。

### 2.5 CallServerInterceptor

CallServerInterceptor负责从服务器读取响应的数据。

```java
public final class CallServerInterceptor implements Interceptor {
    
    @Override public Response intercept(Chain chain) throws IOException {
        
        //这些对象在前面的Interceptor都已经创建完毕
        RealInterceptorChain realChain = (RealInterceptorChain) chain;
        HttpCodec httpCodec = realChain.httpStream();
        StreamAllocation streamAllocation = realChain.streamAllocation();
        RealConnection connection = (RealConnection) realChain.connection();
        Request request = realChain.request();
    
        long sentRequestMillis = System.currentTimeMillis();
        //1. 写入请求头 
        httpCodec.writeRequestHeaders(request);
    
        Response.Builder responseBuilder = null;
        if (HttpMethod.permitsRequestBody(request.method()) && request.body() != null) {
          // If there's a "Expect: 100-continue" header on the request, wait for a "HTTP/1.1 100
          // Continue" response before transmitting the request body. If we don't get that, return what
          // we did get (such as a 4xx response) without ever transmitting the request body.
          if ("100-continue".equalsIgnoreCase(request.header("Expect"))) {
            httpCodec.flushRequest();
            responseBuilder = httpCodec.readResponseHeaders(true);
          }
    
          //2 写入请求体
          if (responseBuilder == null) {
            // Write the request body if the "Expect: 100-continue" expectation was met.
            Sink requestBodyOut = httpCodec.createRequestBody(request, request.body().contentLength());
            BufferedSink bufferedRequestBody = Okio.buffer(requestBodyOut);
            request.body().writeTo(bufferedRequestBody);
            bufferedRequestBody.close();
          } else if (!connection.isMultiplexed()) {
            // If the "Expect: 100-continue" expectation wasn't met, prevent the HTTP/1 connection from
            // being reused. Otherwise we're still obligated to transmit the request body to leave the
            // connection in a consistent state.
            streamAllocation.noNewStreams();
          }
        }
    
        httpCodec.finishRequest();
    
        //3 读取响应头
        if (responseBuilder == null) {
          responseBuilder = httpCodec.readResponseHeaders(false);
        }
    
        Response response = responseBuilder
            .request(request)
            .handshake(streamAllocation.connection().handshake())
            .sentRequestAtMillis(sentRequestMillis)
            .receivedResponseAtMillis(System.currentTimeMillis())
            .build();
    
        //4 读取响应体
        int code = response.code();
        if (forWebSocket && code == 101) {
          // Connection is upgrading, but we need to ensure interceptors see a non-null response body.
          response = response.newBuilder()
              .body(Util.EMPTY_RESPONSE)
              .build();
        } else {
          response = response.newBuilder()
              .body(httpCodec.openResponseBody(response))
              .build();
        }
    
        if ("close".equalsIgnoreCase(response.request().header("Connection"))
            || "close".equalsIgnoreCase(response.header("Connection"))) {
          streamAllocation.noNewStreams();
        }
    
        if ((code == 204 || code == 205) && response.body().contentLength() > 0) {
          throw new ProtocolException(
              "HTTP " + code + " had non-zero Content-Length: " + response.body().contentLength());
        }
    
        return response;
      }
}
```

我们通过ConnectInterceptor已经连接到服务器了，接下来我们就是写入请求数据以及读出返回数据了。整个流程：

1. 写入请求头 
2. 写入请求体 
3. 读取响应头 
4. 读取响应体 

这篇文章就到这里，后续的文章我们会来分析Okhttp的缓存机制、连接机制、编辑吗机制等实现。

## 三 连接机制

连接的创建是在StreamAllocation对象统筹下完成的，我们前面也说过它早在RetryAndFollowUpInterceptor就被创建了，StreamAllocation对象
主要用来管理两个关键角色：

- RealConnection：真正建立连接的对象，利用Socket建立连接。
- ConnectionPool：连接池，用来管理和复用连接。

在里初始化了一个StreamAllocation对象，我们说在这个StreamAllocation对象里初始化了一个Socket对象用来做连接，但是并没有

### 3.1 创建连接

我们在前面的ConnectInterceptor分析中已经说过，onnectInterceptor用来完成连接。而真正的连接在RealConnect中实现，连接由连接池ConnectPool来管理，连接池最多保
持5个地址的连接keep-alive，每个keep-alive时长为5分钟，并有异步线程清理无效的连接。

主要由以下两个方法完成：

1. HttpCodec httpCodec = streamAllocation.newStream(client, doExtensiveHealthChecks);
2. RealConnection connection = streamAllocation.connection();

我们来具体的看一看。

StreamAllocation.newStream()最终调动findConnect()方法来建立连接。

```java
public final class StreamAllocation {
    
      /**
       * Returns a connection to host a new stream. This prefers the existing connection if it exists,
       * then the pool, finally building a new connection.
       */
      private RealConnection findConnection(int connectTimeout, int readTimeout, int writeTimeout,
          boolean connectionRetryEnabled) throws IOException {
        Route selectedRoute;
        synchronized (connectionPool) {
          if (released) throw new IllegalStateException("released");
          if (codec != null) throw new IllegalStateException("codec != null");
          if (canceled) throw new IOException("Canceled");
    
          //1 查看是否有完好的连接
          RealConnection allocatedConnection = this.connection;
          if (allocatedConnection != null && !allocatedConnection.noNewStreams) {
            return allocatedConnection;
          }
    
          //2 连接池中是否用可用的连接，有则使用
          Internal.instance.get(connectionPool, address, this, null);
          if (connection != null) {
            return connection;
          }
    
          selectedRoute = route;
        }
    
        //线程的选择，多IP操作
        if (selectedRoute == null) {
          selectedRoute = routeSelector.next();
        }
    
        //3 如果没有可用连接，则自己创建一个
        RealConnection result;
        synchronized (connectionPool) {
          if (canceled) throw new IOException("Canceled");
    
          // Now that we have an IP address, make another attempt at getting a connection from the pool.
          // This could match due to connection coalescing.
          Internal.instance.get(connectionPool, address, this, selectedRoute);
          if (connection != null) {
            route = selectedRoute;
            return connection;
          }
    
          // Create a connection and assign it to this allocation immediately. This makes it possible
          // for an asynchronous cancel() to interrupt the handshake we're about to do.
          route = selectedRoute;
          refusedStreamCount = 0;
          result = new RealConnection(connectionPool, selectedRoute);
          acquire(result);
        }
    
        //4 开始TCP以及TLS握手操作
        result.connect(connectTimeout, readTimeout, writeTimeout, connectionRetryEnabled);
        routeDatabase().connected(result.route());
    
        //5 将新创建的连接，放在连接池中
        Socket socket = null;
        synchronized (connectionPool) {
          // Pool the connection.
          Internal.instance.put(connectionPool, result);
    
          // If another multiplexed connection to the same address was created concurrently, then
          // release this connection and acquire that one.
          if (result.isMultiplexed()) {
            socket = Internal.instance.deduplicate(connectionPool, address, this);
            result = connection;
          }
        }
        closeQuietly(socket);
    
        return result;
      }    
}
```

整个流程如下：

1. 查找是否有完整的连接可用：

- Socket没有关闭
- 输入流没有关闭
- 输出流没有关闭
- Http2连接没有关闭

2. 连接池中是否有可用的连接，如果有则可用。
3. 如果没有可用连接，则自己创建一个。
4. 开始TCP连接以及TLS握手操作。
5. 将新创建的连接加入连接池。

上述方法完成后会创建一个RealConnection对象，然后调用该方法的connect()方法建立连接，我们再来看看RealConnection.connect()方法的实现。

```java
public final class RealConnection extends Http2Connection.Listener implements Connection {
    
    public void connect(
         int connectTimeout, int readTimeout, int writeTimeout, boolean connectionRetryEnabled) {
       if (protocol != null) throw new IllegalStateException("already connected");
   
       //线路选择
       RouteException routeException = null;
       List<ConnectionSpec> connectionSpecs = route.address().connectionSpecs();
       ConnectionSpecSelector connectionSpecSelector = new ConnectionSpecSelector(connectionSpecs);
   
       if (route.address().sslSocketFactory() == null) {
         if (!connectionSpecs.contains(ConnectionSpec.CLEARTEXT)) {
           throw new RouteException(new UnknownServiceException(
               "CLEARTEXT communication not enabled for client"));
         }
         String host = route.address().url().host();
         if (!Platform.get().isCleartextTrafficPermitted(host)) {
           throw new RouteException(new UnknownServiceException(
               "CLEARTEXT communication to " + host + " not permitted by network security policy"));
         }
       }
       
       //开始连接
       while (true) {
         try {
            //如果是通道模式，则建立通道连接
           if (route.requiresTunnel()) {
             connectTunnel(connectTimeout, readTimeout, writeTimeout);
           } 
           //否则进行Socket连接，一般都是属于这种情况
           else {
             connectSocket(connectTimeout, readTimeout);
           }
           //建立https连接
           establishProtocol(connectionSpecSelector);
           break;
         } catch (IOException e) {
           closeQuietly(socket);
           closeQuietly(rawSocket);
           socket = null;
           rawSocket = null;
           source = null;
           sink = null;
           handshake = null;
           protocol = null;
           http2Connection = null;
   
           if (routeException == null) {
             routeException = new RouteException(e);
           } else {
             routeException.addConnectException(e);
           }
   
           if (!connectionRetryEnabled || !connectionSpecSelector.connectionFailed(e)) {
             throw routeException;
           }
         }
       }
   
       if (http2Connection != null) {
         synchronized (connectionPool) {
           allocationLimit = http2Connection.maxConcurrentStreams();
         }
       }
     }

    /** Does all the work necessary to build a full HTTP or HTTPS connection on a raw socket. */
      private void connectSocket(int connectTimeout, int readTimeout) throws IOException {
        Proxy proxy = route.proxy();
        Address address = route.address();
    
        //根据代理类型的不同处理Socket
        rawSocket = proxy.type() == Proxy.Type.DIRECT || proxy.type() == Proxy.Type.HTTP
            ? address.socketFactory().createSocket()
            : new Socket(proxy);
    
        rawSocket.setSoTimeout(readTimeout);
        try {
          //建立Socket连接
          Platform.get().connectSocket(rawSocket, route.socketAddress(), connectTimeout);
        } catch (ConnectException e) {
          ConnectException ce = new ConnectException("Failed to connect to " + route.socketAddress());
          ce.initCause(e);
          throw ce;
        }
    
        // The following try/catch block is a pseudo hacky way to get around a crash on Android 7.0
        // More details:
        // https://github.com/square/okhttp/issues/3245
        // https://android-review.googlesource.com/#/c/271775/
        try {
          //获取输入/输出流
          source = Okio.buffer(Okio.source(rawSocket));
          sink = Okio.buffer(Okio.sink(rawSocket));
        } catch (NullPointerException npe) {
          if (NPE_THROW_WITH_NULL.equals(npe.getMessage())) {
            throw new IOException(npe);
          }
        }
      }
}
```

最终调用Java里的套接字Socket里的connect()方法。

### 3.2 连接池

我们知道在负责的网络环境下，频繁的进行建立Sokcet连接（TCP三次握手）和断开Socket（TCP四次分手）是非常消耗网络资源和浪费时间的，HTTP中的keepalive连接对于
降低延迟和提升速度有非常重要的作用。

复用连接就需要对连接进行管理，这里就引入了连接池的概念。

Okhttp支持5个并发KeepAlive，默认链路生命为5分钟(链路空闲后，保持存活的时间)，连接池有ConectionPool实现，对连接进行回收和管理。

ConectionPool在内部维护了一个线程池，来清理连接，如下所示：

````java
public final class ConnectionPool {
    
        private static final Executor executor = new ThreadPoolExecutor(0 /* corePoolSize */,
          Integer.MAX_VALUE /* maximumPoolSize */, 60L /* keepAliveTime */, TimeUnit.SECONDS,
          new SynchronousQueue<Runnable>(), Util.threadFactory("OkHttp ConnectionPool", true));
      
        //清理连接，在线程池executor里调用。
        private final Runnable cleanupRunnable = new Runnable() {
          @Override public void run() {
            while (true) {
              //执行清理，并返回下次需要清理的时间。
              long waitNanos = cleanup(System.nanoTime());
              if (waitNanos == -1) return;
              if (waitNanos > 0) {
                long waitMillis = waitNanos / 1000000L;
                waitNanos -= (waitMillis * 1000000L);
                synchronized (ConnectionPool.this) {
                  try {
                    //在timeout时间内释放锁
                    ConnectionPool.this.wait(waitMillis, (int) waitNanos);
                  } catch (InterruptedException ignored) {
                  }
                }
              }
            }
          }
        };
}
````

ConectionPool在内部维护了一个线程池，来清理连，清理任务由cleanup()方法完成，它是一个阻塞操作，首先执行清理，并返回下次需要清理的间隔时间，调用调用wait()
方法释放锁。等时间到了以后，再次进行清理，并返回下一次需要清理的时间，循环往复。

我们来看一看cleanup()方法的具体实现。

```java
public final class ConnectionPool {
    
      long cleanup(long now) {
        int inUseConnectionCount = 0;
        int idleConnectionCount = 0;
        RealConnection longestIdleConnection = null;
        long longestIdleDurationNs = Long.MIN_VALUE;
    
     
        synchronized (this) {
            //遍历所有的连接，标记处不活跃的连接。
          for (Iterator<RealConnection> i = connections.iterator(); i.hasNext(); ) {
            RealConnection connection = i.next();
    
            //1. 查询此连接内部的StreanAllocation的引用数量。
            if (pruneAndGetAllocationCount(connection, now) > 0) {
              inUseConnectionCount++;
              continue;
            }
    
            idleConnectionCount++;
    
            //2. 标记空闲连接。
            long idleDurationNs = now - connection.idleAtNanos;
            if (idleDurationNs > longestIdleDurationNs) {
              longestIdleDurationNs = idleDurationNs;
              longestIdleConnection = connection;
            }
          }
    
          if (longestIdleDurationNs >= this.keepAliveDurationNs
              || idleConnectionCount > this.maxIdleConnections) {
            //3. 如果空闲连接超过5个或者keepalive时间大于5分钟，则将该连接清理掉。
            connections.remove(longestIdleConnection);
          } else if (idleConnectionCount > 0) {
            //4. 返回此连接的到期时间，供下次进行清理。
            return keepAliveDurationNs - longestIdleDurationNs;
          } else if (inUseConnectionCount > 0) {
            //5. 全部都是活跃连接，5分钟时候再进行清理。
            return keepAliveDurationNs;
          } else {
            //6. 没有任何连接，跳出循环。
            cleanupRunning = false;
            return -1;
          }
        }
    
        //7. 关闭连接，返回时间0，立即再次进行清理。
        closeQuietly(longestIdleConnection.socket());
        return 0;
      }
}
```

整个方法的流程如下所示：

1. 查询此连接内部的StreanAllocation的引用数量。
2. 标记空闲连接。
3. 如果空闲连接超过5个或者keepalive时间大于5分钟，则将该连接清理掉。
4. 返回此连接的到期时间，供下次进行清理。
5. 全部都是活跃连接，5分钟时候再进行清理。
6. 没有任何连接，跳出循环。
7. 关闭连接，返回时间0，立即再次进行清理。

在RealConnection里有个StreamAllocation虚引用列表，每创建一个StreamAllocation，就会把它添加进该列表中，如果留关闭以后就将StreamAllocation
对象从该列表中移除，正是利用利用这种引用计数的方式判定一个连接是否为空闲连接，

```java
public final List<Reference<StreamAllocation>> allocations = new ArrayList<>();
```

查找引用计数由pruneAndGetAllocationCount()方法实现，具体实现如下所示：

```java
public final class ConnectionPool {
    
     private int pruneAndGetAllocationCount(RealConnection connection, long now) {
       //虚引用列表
       List<Reference<StreamAllocation>> references = connection.allocations;
       //遍历虚引用列表
       for (int i = 0; i < references.size(); ) {
         Reference<StreamAllocation> reference = references.get(i);
         //如果虚引用StreamAllocation正在被使用，则跳过进行下一次循环，
         if (reference.get() != null) {
           //引用计数
           i++;
           continue;
         }
   
         // We've discovered a leaked allocation. This is an application bug.
         StreamAllocation.StreamAllocationReference streamAllocRef =
             (StreamAllocation.StreamAllocationReference) reference;
         String message = "A connection to " + connection.route().address().url()
             + " was leaked. Did you forget to close a response body?";
         Platform.get().logCloseableLeak(message, streamAllocRef.callStackTrace);
   
         //否则移除该StreamAllocation引用
         references.remove(i);
         connection.noNewStreams = true;
   
         // 如果所有的StreamAllocation引用都没有了，返回引用计数0
         if (references.isEmpty()) {
           connection.idleAtNanos = now - keepAliveDurationNs;
           return 0;
         }
       }
       
       //返回引用列表的大小，作为引用计数
       return references.size();
     } 
}
```
## 四 缓存机制

### 3.1 缓存策略

在分析Okhttp的缓存机制之前，我们先来回顾一下HTTP与缓存相关的理论知识，这是实现Okhttp机制的基础。

HTTP的缓存机制也是依赖于请求和响应header里的参数类实现的，最终响应式从缓存中去，还是从服务端重新拉取，HTTP的缓存机制的流程如下所示：

👉 点击图片查看大图

<img src="https://github.com/guoxiaoxing/android-open-framwork-analysis/raw/master/art/okhttp/http_cache_structure.png" width="600"/>

HTTP的缓存可以分为两种：

- 强制缓存：需要服务端参与判断是否继续使用缓存，当客户端第一次请求数据是，服务端返回了缓存的过期时间（Expires与Cache-Control），没有过期就可以继续使用缓存，否则则不适用，无需再向服务端询问。
- 对比缓存：需要服务端参与判断是否继续使用缓存，当客户端第一次请求数据时，服务端会将缓存标识（Last-Modified/If-Modified-Since与Etag/If-None-Match）与数据一起返回给客户端，客户端将两者都备份到缓存中 ，再次请求数据时，客户端将上次备份的缓存
标识发送给服务端，服务端根据缓存标识进行判断，如果返回304，则表示通知客户端可以继续使用缓存。

强制缓存优先于对比缓存。

上面提到强制缓存使用的的两个标识：

- Expires：Expires的值为服务端返回的到期时间，即下一次请求时，请求时间小于服务端返回的到期时间，直接使用缓存数据。到期时间是服务端生成的，客户端和服务端的时间可能有误差。
- Cache-Control：Expires有个时间校验的问题，所有HTTP1.1采用Cache-Control替代Expires。

Cache-Control的取值有以下几种：

- private:             客户端可以缓存。
- public:              客户端和代理服务器都可缓存。
- max-age=xxx:   缓存的内容将在 xxx 秒后失效
- no-cache:          需要使用对比缓存来验证缓存数据。
- no-store:           所有内容都不会缓存，强制缓存，对比缓存都不会触发。

我们再来看看对比缓存的两个标识：

**Last-Modified/If-Modified-Since**

Last-Modified 表示资源上次修改的时间。

当客户端发送第一次请求时，服务端返回资源上次修改的时间：

```java
Last-Modified: Tue, 12 Jan 2016 09:31:27 GMT
```
客户端再次发送，会在header里携带If-Modified-Since。将上次服务端返回的资源时间上传给服务端。

```java
If-Modified-Since: Tue, 12 Jan 2016 09:31:27 GMT 
```
服务端接收到客户端发来的资源修改时间，与自己当前的资源修改时间进行对比，如果自己的资源修改时间大于客户端发来的资源修改时间，则说明资源做过修改，
则返回200表示需要重新请求资源，否则返回304表示资源没有被修改，可以继续使用缓存。

上面是一种时间戳标记资源是否修改的方法，还有一种资源标识码ETag的方式来标记是否修改，如果标识码发生改变，则说明资源已经被修改，ETag优先级高于Last-Modified。

**Etag/If-None-Match**

ETag是资源文件的一种标识码，当客户端发送第一次请求时，服务端会返回当前资源的标识码：

```java
ETag: "5694c7ef-24dc"
```
客户端再次发送，会在header里携带上次服务端返回的资源标识码：

```java
If-None-Match:"5694c7ef-24dc"
```
服务端接收到客户端发来的资源标识码，则会与自己当前的资源吗进行比较，如果不同，则说明资源已经被修改，则返回200，如果相同则说明资源没有被修改，返回
304，客户端可以继续使用缓存。

以上便是HTTP缓存策略的相关理论知识，我们来看看具体实现。

Okhttp的缓存策略就是根据上述流程图实现的，具体的实现类是CacheStrategy，CacheStrategy的构造函数里有两个参数：

```java
CacheStrategy(Request networkRequest, Response cacheResponse) {
this.networkRequest = networkRequest;
this.cacheResponse = cacheResponse;
}
```
这两个参数参数的含义如下：

- networkRequest：网络请求。
- cacheResponse：缓存响应，基于DiskLruCache实现的文件缓存，可以是请求中url的md5，value是文件中查询到的缓存，这个我们下面会说。

CacheStrategy就是利用这两个参数生成最终的策略，有点像map操作，将networkRequest与cacheResponse这两个值输入，处理之后再将这两个值输出，们的组合结果如下所示：

- 如果networkRequest为null，cacheResponse为null：only-if-cached(表明不进行网络请求，且缓存不存在或者过期，一定会返回503错误)。
- 如果networkRequest为null，cacheResponse为non-null：不进行网络请求，而且缓存可以使用，直接返回缓存，不用请求网络。
- 如果networkRequest为non-null，cacheResponse为null：需要进行网络请求，而且缓存不存在或者过期，直接访问网络。
- 如果networkRequest为non-null，cacheResponse为non-null：Header中含有ETag/Last-Modified标签，需要在条件请求下使用，还是需要访问网络。

那么这四种情况是如何判定的，我们来看一下。

CacheStrategy是利用Factory模式进行构造的，CacheStrategy.Factory对象构建以后，调用它的get()方法即可获得具体的CacheStrategy，CacheStrategy.Factory.get()方法内部
调用的是CacheStrategy.Factory.getCandidate()方法，它是核心的实现。

如下所示：

```java
public static class Factory {
    
        private CacheStrategy getCandidate() {
          //1. 如果缓存没有命中，就直接进行网络请求。
          if (cacheResponse == null) {
            return new CacheStrategy(request, null);
          }
    
          //2. 如果TLS握手信息丢失，则返回直接进行连接。
          if (request.isHttps() && cacheResponse.handshake() == null) {
            return new CacheStrategy(request, null);
          }

          //3. 根据response状态码，Expired时间和是否有no-cache标签就行判断是否进行直接访问。
          if (!isCacheable(cacheResponse, request)) {
            return new CacheStrategy(request, null);
          }
    
          //4. 如果请求header里有"no-cache"或者右条件GET请求（header里带有ETag/Since标签），则直接连接。
          CacheControl requestCaching = request.cacheControl();
          if (requestCaching.noCache() || hasConditions(request)) {
            return new CacheStrategy(request, null);
          }
    
          CacheControl responseCaching = cacheResponse.cacheControl();
          if (responseCaching.immutable()) {
            return new CacheStrategy(null, cacheResponse);
          }
    
          //计算当前age的时间戳：now - sent + age
          long ageMillis = cacheResponseAge();
          //刷新时间，一般服务器设置为max-age
          long freshMillis = computeFreshnessLifetime();
    
          if (requestCaching.maxAgeSeconds() != -1) {
            //一般取max-age
            freshMillis = Math.min(freshMillis, SECONDS.toMillis(requestCaching.maxAgeSeconds()));
          }
    
          long minFreshMillis = 0;
          if (requestCaching.minFreshSeconds() != -1) {
            //一般取0
            minFreshMillis = SECONDS.toMillis(requestCaching.minFreshSeconds());
          }
    
          long maxStaleMillis = 0;
          if (!responseCaching.mustRevalidate() && requestCaching.maxStaleSeconds() != -1) {
            maxStaleMillis = SECONDS.toMillis(requestCaching.maxStaleSeconds());
          }
    
          //5. 如果缓存在过期时间内则可以直接使用，则直接返回上次缓存。
          if (!responseCaching.noCache() && ageMillis + minFreshMillis < freshMillis + maxStaleMillis) {
            Response.Builder builder = cacheResponse.newBuilder();
            if (ageMillis + minFreshMillis >= freshMillis) {
              builder.addHeader("Warning", "110 HttpURLConnection \"Response is stale\"");
            }
            long oneDayMillis = 24 * 60 * 60 * 1000L;
            if (ageMillis > oneDayMillis && isFreshnessLifetimeHeuristic()) {
              builder.addHeader("Warning", "113 HttpURLConnection \"Heuristic expiration\"");
            }
            return new CacheStrategy(null, builder.build());
          }
    
          //6. 如果缓存过期，且有ETag等信息，则发送If-None-Match、If-Modified-Since、If-Modified-Since等条件请求
          //交给服务端判断处理
          String conditionName;
          String conditionValue;
          if (etag != null) {
            conditionName = "If-None-Match";
            conditionValue = etag;
          } else if (lastModified != null) {
            conditionName = "If-Modified-Since";
            conditionValue = lastModifiedString;
          } else if (servedDate != null) {
            conditionName = "If-Modified-Since";
            conditionValue = servedDateString;
          } else {
            return new CacheStrategy(request, null); // No condition! Make a regular request.
          }
    
          Headers.Builder conditionalRequestHeaders = request.headers().newBuilder();
          Internal.instance.addLenient(conditionalRequestHeaders, conditionName, conditionValue);
    
          Request conditionalRequest = request.newBuilder()
              .headers(conditionalRequestHeaders.build())
              .build();
          return new CacheStrategy(conditionalRequest, cacheResponse);
        }
}
```

整个函数的逻辑就是按照上面那个HTTP缓存判定流程图来实现，具体流程如下所示：

1. 如果缓存没有命中，就直接进行网络请求。
2. 如果TLS握手信息丢失，则返回直接进行连接。
3. 根据response状态码，Expired时间和是否有no-cache标签就行判断是否进行直接访问。
4. 如果请求header里有"no-cache"或者右条件GET请求（header里带有ETag/Since标签），则直接连接。
5. 如果缓存在过期时间内则可以直接使用，则直接返回上次缓存。
6. 如果缓存过期，且有ETag等信息，则发送If-None-Match、If-Modified-Since、If-Modified-Since等条件请求交给服务端判断处理        
          
整个流程就是这样，另外说一点，Okhttp的缓存是根据服务器header自动的完成的，整个流程也是根据RFC文档写死的，客户端不必要进行手动控制。

理解了缓存策略，我们来看看缓存在磁盘上是如何被管理的。

### 3.2 缓存管理

这篇文章我们来分析Okhttp的缓存机制，缓存机制是基于DiskLruCache做的。Cache类封装了缓存的实现，实现了InternalCache接口。

InternalCache接口如下所示：

**InternalCache**

```java
public interface InternalCache {
  //获取缓存
  Response get(Request request) throws IOException;
  //存入缓存
  CacheRequest put(Response response) throws IOException;
  //移除缓存
  void remove(Request request) throws IOException;
  //更新缓存
  void update(Response cached, Response network);
  //跟踪一个满足缓存条件的GET请求
  void trackConditionalCacheHit();
  //跟踪满足缓存策略CacheStrategy的响应
  void trackResponse(CacheStrategy cacheStrategy);
}
```
我们接着来看看它的实现类。

Cache没有直接实现InternalCache这个接口，而是在其内部实现了InternalCache的匿名内部类，内部类的方法调用Cache对应的方法，如下所示：

```java
final InternalCache internalCache = new InternalCache() {
@Override public Response get(Request request) throws IOException {
  return Cache.this.get(request);
}

@Override public CacheRequest put(Response response) throws IOException {
  return Cache.this.put(response);
}

@Override public void remove(Request request) throws IOException {
  Cache.this.remove(request);
}

@Override public void update(Response cached, Response network) {
  Cache.this.update(cached, network);
}

@Override public void trackConditionalCacheHit() {
  Cache.this.trackConditionalCacheHit();
}

@Override public void trackResponse(CacheStrategy cacheStrategy) {
  Cache.this.trackResponse(cacheStrategy);
}
};

InternalCache internalCache() {
return cache != null ? cache.internalCache : internalCache;
}
```
`
在Cache类里还定义一些内部类，这些类封装了请求与响应信息。

- Cache.Entry：封装了请求与响应等信息，包括url、varyHeaders、protocol、code、message、responseHeaders、handshake、sentRequestMillis与receivedResponseMillis。
- Cache.CacheResponseBody：继承于ResponseBody，封装了缓存快照snapshot，响应体bodySource，内容类型contentType，内容长度contentLength。

除了两个类以外，Okhttp还封装了一个文件系统类FileSystem类，这个类利用Okio这个库对Java的FIle操作进行了一层封装，简化了IO操作。理解了这些剩下的就是DiskLruCahe里的插入缓存
、获取缓存和删除缓存的操作。

关于这一部分的内容，可以参考我们之前写的内容[07Android开源框架源码分析：LruCache与DiskLruCache](https://github.com/guoxiaoxing/android-open-framwork-analysis/blob/master/doc/源码分析/07Android开源框架源码分析：LruCache与DiskLruCache.md)
。

好了，到这里关于Okhttp的全部内容就都讲完了，可以说Okhttp是设计非常优良的一个库，有很多值得我们学习的地方，下一篇我们来分析它的好搭档Retrofit。

