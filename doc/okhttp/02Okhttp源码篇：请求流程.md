# Okhttp源码篇：请求的发送与拦截流程

**关于作者**

>郭孝星，非著名程序员，主要从事Android平台基础架构与中间件方面的工作，欢迎交流技术方面的问题，可以去我的[Github](https://github.com/guoxiaoxing)提交Issue或者发邮件至guoxiaoxingse@163.com与我联系。

从这篇文章开始，我们开始正式分析Okhttp源码，第一部分我们先来分析Okhttp的整个网络请求的流程，有了对整体流程的把握，我们才能
有的放矢的去掌握细节。

我们从一个简单的例子入手，一步步分析请求的发送与拦截流程。

```java
/**
 * 发送Get请求-异步
 */
private void syncRequest(String url) throws IOException {
    Request request = new Request.Builder()
            .url(url)
            .build();
    OkHttpClient okHttpClient = new OkHttpClient.Builder()
            .build();
    Response response = okHttpClient.newCall(request).execute();
}

/**
 * 发送Get请求-同步
 */
private void asyncRequest(String url) {
    Request request = new Request.Builder()
            .url(url)
            .build();
    OkHttpClient okHttpClient = new OkHttpClient.Builder()
            .build();
    okHttpClient.newCall(request).enqueue(new Callback() {
        @Override
        public void onFailure(Call call, IOException e) {
        }

        @Override
        public void onResponse(Call call, Response response) throws IOException {

        }
    });
}
```

注：我们通过创建OkHttpClient来发起Http请求，每个OkHttpClient都持有自己的线程池与连接池，为了更好的性能，我们通常把OkHttpClient
写成单例，共享使用。

请求处理流程图

<img src="https://github.com/guoxiaoxing/android-open-framwork-analysis/raw/master/art/okhttp/request_sequence.png"/>

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
    //1 该拦截器在Request阶段负责做的事情

    //2 调用RealInterceptorChain.proceed()，其实是在递归调用下一个拦截器的intercept()方法
    response = ((RealInterceptorChain) chain).proceed(request, streamAllocation, null, null);

    //3 完成了该拦截器在Response阶段负责做的事情，然后返回到上一层的拦截器。
    return response;     
    }
  }
```
从上面的描述可知，Request是按照interpretor的顺序正向处理，而Response是逆向处理的。

interceptor的执行顺序：RetryAndFollowUpInterceptor -> BridgeInterceptor -> CacheInterceptor -> ConnectInterceptor -> CallServerInterceptor。

### 6 RetryAndFollowUpInterceptor.intercept(Chain chain) 

RetryAndFollowUpInterceptor负责失败重试以及重定向。

```java
public final class RetryAndFollowUpInterceptor implements Interceptor {
    
    private static final int MAX_FOLLOW_UPS = 20;
    
     @Override public Response intercept(Chain chain) throws IOException {
        Request request = chain.request();
    
        //1 初始化一个Socket连接对象，获取输入/输出流对象
        streamAllocation = new StreamAllocation(
            client.connectionPool(), createAddress(request.url()), callStackTrace);
    
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
            //2 继续执行下一个Interceptor，即BridgeInterceptor
            response = ((RealInterceptorChain) chain).proceed(request, streamAllocation, null, null);
            releaseConnection = false;
          } catch (RouteException e) {
            //3 抛出异常，则检测连接是否还可以继续
            // The attempt to connect via a route failed. The request will not have been sent.
            if (!recover(e.getLastConnectException(), false, request)) {
              throw e.getLastConnectException();
            }
            releaseConnection = false;
            continue;
          } catch (IOException e) {
            // An attempt to communicate with a server failed. The request may have been sent.
            boolean requestSendStarted = !(e instanceof ConnectionShutdownException);
            if (!recover(e, requestSendStarted, request)) throw e;
            releaseConnection = false;
            continue;
          } finally {
            // We're throwing an unchecked exception. Release any resources.
            if (releaseConnection) {
              streamAllocation.streamFailed(null);
              streamAllocation.release();
            }
          }
    
          // Attach the prior response if it exists. Such responses never have a body.
          if (priorResponse != null) {
            response = response.newBuilder()
                .priorResponse(priorResponse.newBuilder()
                        .body(null)
                        .build())
                .build();
          }
    
          //4 根据响应码处理请求，返回Request不为空时则进行重定向处理
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
      
    
    private boolean recover(IOException e, boolean requestSendStarted, Request userRequest) {
      streamAllocation.streamFailed(e);
  
      // The application layer has forbidden retries.
      if (!client.retryOnConnectionFailure()) return false;
  
      // We can't send the request body again.
      if (requestSendStarted && userRequest.body() instanceof UnrepeatableRequestBody) return false;
  
      // This exception is fatal.
      if (!isRecoverable(e, requestSendStarted)) return false;
  
      // No more routes to attempt.
      if (!streamAllocation.hasMoreRoutes()) return false;
  
      // For failure recovery, use the same route selector with a new connection.
      return true;
    }
    
      private boolean isRecoverable(IOException e, boolean requestSendStarted) {
        // If there was a protocol problem, don't recover.
        if (e instanceof ProtocolException) {
          return false;
        }
    
        // If there was an interruption don't recover, but if there was a timeout connecting to a route
        // we should try the next route (if there is one).
        if (e instanceof InterruptedIOException) {
          return e instanceof SocketTimeoutException && !requestSendStarted;
        }
    
        // Look for known client-side or negotiation errors that are unlikely to be fixed by trying
        // again with a different route.
        if (e instanceof SSLHandshakeException) {
          // If the problem was a CertificateException from the X509TrustManager,
          // do not retry.
          if (e.getCause() instanceof CertificateException) {
            return false;
          }
        }
        if (e instanceof SSLPeerUnverifiedException) {
          // e.g. a certificate pinning error.
          return false;
        }
    
        // An example of one we might want to retry with a different route is a problem connecting to a
        // proxy and would manifest as a standard IOException. Unless it is one we know we should not
        // retry, we return true and try a new route.
        return true;
      }


}
```
我们先来说说StreamAllocation这个类的作用，这个类协调了三个实体类的关系：

- Connections：连接到远程服务器的物理套接字，这个套接字连接可能比较慢，所以它有一套取消机制。
- Streams：定义了逻辑上的HTTP请求/响应对，每个连接都定义了它们可以携带的最大并发流，HTTP/1.x每次只可以携带一个，HTTP/2每次可以携带多个。
- Calls：定义了流的逻辑序列，这个序列通常是一个初始请求以及它的重定向请求，对于同一个连接，我们通常将所有流都放在一个调用中，以此来统一它们的行为。

我们再来看看整个方法的流程：

1 初始化一个Socket连接对象，获取输入/输出流对象
2 继续执行下一个Interceptor，即BridgeInterceptor
3 抛出异常，则检测连接是否还可以继续，以下情况不会重试：

- 客户端配置出错不再重试
- 出错后，request body不能再次发送
- 发生以下Exception也无法恢复连接：
  - ProtocolException：协议异常
  - InterruptedIOException：中断异常
  - SSLHandshakeException：SSL握手异常
  - SSLPeerUnverifiedException：SSL握手未授权异常
- 没有更多线路可以选择

4 根据响应码处理请求，返回Request不为空时则进行重定向处理，重定向的次数不能超过20次。

```java
public final class RetryAndFollowUpInterceptor implements Interceptor {
      /**
       * Figures out the HTTP request to make in response to receiving {@code userResponse}. This will
       * either add authentication headers, follow redirects or handle a client request timeout. If a
       * follow-up is either unnecessary or not applicable, this returns null.
       */
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
            // Does the client allow redirects?
            if (!client.followRedirects()) return null;
    
            String location = userResponse.header("Location");
            if (location == null) return null;
            HttpUrl url = userResponse.request().url().resolve(location);
    
            // Don't follow redirects to unsupported protocols.
            if (url == null) return null;
    
            //查询是否存在http与https之间的重定向
            // If configured, don't follow redirects between SSL and non-SSL.
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
总的来说，这个RetryAndFollowUpInterceptor就是初始化一个Socket连接，并处理了一些异常。我们接着来看看下一个BridgeInterceptor。

### 7 BridgeInterceptor.intercept(Chain chain) 

就跟它的名字描述的那样，它是一个桥梁，负责把用户构造的请求转换为发送给服务器的请求，把服务器返回的响应转换为对用户友好的响应。

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

这个方法主要是针对Header做了一些处理，这里主要提一下"Accept-Encoding", "gzip"，关于它有以下几点需要注意：

- 开发者没有添加Accept-Encoding时，自动添加Accept-Encoding: gzip
- 自动添加Accept-Encoding，会对request，response进行自动解压
- 手动添加Accept-Encoding，不负责解压缩
- 自动解压时移除Content-Length，所以上层Java代码想要contentLength时为-1
- 自动解压时移除 Content-Encoding
- 自动解压时，如果是分块传输编码，Transfer-Encoding: chunked不受影响。

BridgeInterceptor主要就是针对Header做了一些处理，我们接着来看CacheInterceptor。

### 8 CacheInterceptor.intercept(Chain chain) 

```java
public final class CacheInterceptor implements Interceptor {
    
     @Override public Response intercept(Chain chain) throws IOException {
         
        //1 如果配置了缓存，则从缓存中取一次，不保证存在。
        Response cacheCandidate = cache != null
            ? cache.get(chain.request())
            : null;
    
        long now = System.currentTimeMillis();
    
        //2 获取缓存策略
        CacheStrategy strategy = new CacheStrategy.Factory(now, chain.request(), cacheCandidate).get();
        Request networkRequest = strategy.networkRequest;
        Response cacheResponse = strategy.cacheResponse;
    
        //3 开始缓存监测
        if (cache != null) {
          cache.trackResponse(strategy);
        }
    
        if (cacheCandidate != null && cacheResponse == null) {
          closeQuietly(cacheCandidate.body()); // The cache candidate wasn't applicable. Close it.
        }
    
        //4 如果根据缓存策略禁止使用网络，而缓存又为空，则直接返回
        // If we're forbidden from using the network and the cache is insufficient, fail.
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
    
        //如果缓存不空，则直接返回缓存
        // If we don't need the network, we're done.
        if (networkRequest == null) {
          return cacheResponse.newBuilder()
              .cacheResponse(stripBody(cacheResponse))
              .build();
        }
    
        Response networkResponse = null;
        try {
          //5 继续执行下一个Interceptor，即ConnectInterceptor
          networkResponse = chain.proceed(networkRequest);
        } finally {
          // If we're crashing on I/O or otherwise, don't leak the cache body.
          if (networkResponse == null && cacheCandidate != null) {
            closeQuietly(cacheCandidate.body());
          }
        }
    
        // If we have a cache response too, then we're doing a conditional get.
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
    
            // Update the cache after combining headers but before stripping the
            // Content-Encoding header (as performed by initContentStream()).
            cache.trackConditionalCacheHit();
            cache.update(cacheResponse, response);
            return response;
          } else {
            closeQuietly(cacheResponse.body());
          }
        }
    
        Response response = networkResponse.newBuilder()
            .cacheResponse(stripBody(cacheResponse))
            .networkResponse(stripBody(networkResponse))
            .build();
    
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
    
        return response;
      }
}
```

Okhttp的缓存是基于DiskLruCache也就是磁盘缓存来做的，CacheStrategy实现了Okhttp的缓存策略，它根据equest 与 cached response来决定
使用缓存、网络还是两者都用。

我们再接着来看ConnectInterceptor。

### 9 ConnectInterceptor.intercept(Chain chain) 

```java
public final class ConnectInterceptor implements Interceptor {
    
      @Override public Response intercept(Chain chain) throws IOException {
        RealInterceptorChain realChain = (RealInterceptorChain) chain;
        Request request = realChain.request();
        StreamAllocation streamAllocation = realChain.streamAllocation();
    
        // We need the network to satisfy this request. Possibly for validating a conditional GET.
        boolean doExtensiveHealthChecks = !request.method().equals("GET");
        HttpCodec httpCodec = streamAllocation.newStream(client, doExtensiveHealthChecks);
        RealConnection connection = streamAllocation.connection();
    
        return realChain.proceed(request, streamAllocation, httpCodec, connection);
      }
}
```

RealInterceptorChain构造函数有五个主要参数：

- List<Interceptor>
- Request
- StreamAllocation
- HttpCodec
- RealConnection

```java
  public RealInterceptorChain(List<Interceptor> interceptors, StreamAllocation streamAllocation,
      HttpCodec httpCodec, RealConnection connection, int index, Request request) {
    this.interceptors = interceptors;
    this.connection = connection;
    this.streamAllocation = streamAllocation;
    this.httpCodec = httpCodec;
    this.index = index;
    this.request = request;
  }
```
List<Interceptor>在RealCall中创建，StreamAllocation在RetryAndFollowUpInterceptor中创建，而HttpCodec与RealConnection则在ConnectInterceptor中创建。

- HttpCodec：用来编码HTTP requests和解码HTTP responses
- RealConnection：连接对象，负责发起与服务器的连接。

### 10 CallServerInterceptor.intercept(Chain chain) 

```java
public final class CallServerInterceptor implements Interceptor {
    
    @Override public Response intercept(Chain chain) throws IOException {
        RealInterceptorChain realChain = (RealInterceptorChain) chain;
        HttpCodec httpCodec = realChain.httpStream();
        StreamAllocation streamAllocation = realChain.streamAllocation();
        RealConnection connection = (RealConnection) realChain.connection();
        Request request = realChain.request();
    
        long sentRequestMillis = System.currentTimeMillis();
        //1 写入请求头 
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