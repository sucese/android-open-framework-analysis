# Okhttp源码篇：Okhttp源码概览

**关于作者**

>郭孝星，非著名程序员，主要从事Android平台基础架构与中间件方面的工作，欢迎交流技术方面的问题，可以去我的[Github](https://github.com/guoxiaoxing)提交Issue或者发邮件至guoxiaoxingse@163.com与我联系。

我们来看看HttpCodec与RealConnection对象的创建。以及整个连接流程。

```java
public final class StreamAllocation {
    
    public HttpCodec newStream(OkHttpClient client, boolean doExtensiveHealthChecks) {
        int connectTimeout = client.connectTimeoutMillis();
        int readTimeout = client.readTimeoutMillis();
        int writeTimeout = client.writeTimeoutMillis();
        boolean connectionRetryEnabled = client.retryOnConnectionFailure();
    
        try {
          //查找是否有完好的连接
          RealConnection resultConnection = findHealthyConnection(connectTimeout, readTimeout,
              writeTimeout, connectionRetryEnabled, doExtensiveHealthChecks);
          //实例化HttpCodec
          HttpCodec resultCodec = resultConnection.newCodec(client, this);
    
          synchronized (connectionPool) {
            codec = resultCodec;
            return resultCodec;
          }
        } catch (IOException e) {
          throw new RouteException(e);
        }
      }
    
      /**
       * Finds a connection and returns it if it is healthy. If it is unhealthy the process is repeated
       * until a healthy connection is found.
       */
      private RealConnection findHealthyConnection(int connectTimeout, int readTimeout,
          int writeTimeout, boolean connectionRetryEnabled, boolean doExtensiveHealthChecks)
          throws IOException {
        while (true) {
          RealConnection candidate = findConnection(connectTimeout, readTimeout, writeTimeout,
              connectionRetryEnabled);
    
          // If this is a brand new connection, we can skip the extensive health checks.
          synchronized (connectionPool) {
            if (candidate.successCount == 0) {
              return candidate;
            }
          }
    
          // Do a (potentially slow) check to confirm that the pooled connection is still good. If it
          // isn't, take it out of the pool and start again.
          if (!candidate.isHealthy(doExtensiveHealthChecks)) {
            noNewStreams();
            continue;
          }
    
          return candidate;
        }
      }
    
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
          // Attempt to use an already-allocated connection.
          RealConnection allocatedConnection = this.connection;
          if (allocatedConnection != null && !allocatedConnection.noNewStreams) {
            return allocatedConnection;
          }
    
          //2 连接池中是否用可用的连接，有则使用
          // Attempt to get a connection from the pool.
          Internal.instance.get(connectionPool, address, this, null);
          if (connection != null) {
            return connection;
          }
    
          selectedRoute = route;
        }
    
        //线程的选择，多IP操作
        // If we need a route, make one. This is a blocking operation.
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
        // Do TCP + TLS handshakes. This is a blocking operation.
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

1 查找是否有完整的连接可用：

- Socket没有关闭
- 输入流没有关闭
- 输出流没有关闭
- Http2连接没有关闭

2 连接池中是否有可用的连接，如果有则可用。

3 如果没有可用连接，则自己创建一个。

4 开始TCP连接以及TLS握手操作。

5 将新创建的连接加入连接池。


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


```java
public class Platform {
    
     public void connectSocket(Socket socket, InetSocketAddress address,
         int connectTimeout) throws IOException {
       socket.connect(address, connectTimeout);
     }
}
```

TLS


```java
public final class RealConnection extends Http2Connection.Listener implements Connection {
    
     private void establishProtocol(ConnectionSpecSelector connectionSpecSelector) throws IOException {
        if (route.address().sslSocketFactory() == null) {
          protocol = Protocol.HTTP_1_1;
          socket = rawSocket;
          return;
        }
    
        connectTls(connectionSpecSelector);
    
        if (protocol == Protocol.HTTP_2) {
          socket.setSoTimeout(0); // HTTP/2 connection timeouts are set per-stream.
          http2Connection = new Http2Connection.Builder(true)
              .socket(socket, route.address().url().host(), source, sink)
              .listener(this)
              .build();
          http2Connection.start();
        }
      }
    
      private void connectTls(ConnectionSpecSelector connectionSpecSelector) throws IOException {
        Address address = route.address();
        SSLSocketFactory sslSocketFactory = address.sslSocketFactory();
        boolean success = false;
        SSLSocket sslSocket = null;
        try {
          // Create the wrapper over the connected socket.
          sslSocket = (SSLSocket) sslSocketFactory.createSocket(
              rawSocket, address.url().host(), address.url().port(), true /* autoClose */);
    
          // Configure the socket's ciphers, TLS versions, and extensions.
          ConnectionSpec connectionSpec = connectionSpecSelector.configureSecureSocket(sslSocket);
          if (connectionSpec.supportsTlsExtensions()) {
            Platform.get().configureTlsExtensions(
                sslSocket, address.url().host(), address.protocols());
          }
    
          // Force handshake. This can throw!
          sslSocket.startHandshake();
          Handshake unverifiedHandshake = Handshake.get(sslSocket.getSession());
    
          // Verify that the socket's certificates are acceptable for the target host.
          if (!address.hostnameVerifier().verify(address.url().host(), sslSocket.getSession())) {
            X509Certificate cert = (X509Certificate) unverifiedHandshake.peerCertificates().get(0);
            throw new SSLPeerUnverifiedException("Hostname " + address.url().host() + " not verified:"
                + "\n    certificate: " + CertificatePinner.pin(cert)
                + "\n    DN: " + cert.getSubjectDN().getName()
                + "\n    subjectAltNames: " + OkHostnameVerifier.allSubjectAltNames(cert));
          }
    
          // Check that the certificate pinner is satisfied by the certificates presented.
          address.certificatePinner().check(address.url().host(),
              unverifiedHandshake.peerCertificates());
    
          // Success! Save the handshake and the ALPN protocol.
          String maybeProtocol = connectionSpec.supportsTlsExtensions()
              ? Platform.get().getSelectedProtocol(sslSocket)
              : null;
          socket = sslSocket;
          source = Okio.buffer(Okio.source(socket));
          sink = Okio.buffer(Okio.sink(socket));
          handshake = unverifiedHandshake;
          protocol = maybeProtocol != null
              ? Protocol.get(maybeProtocol)
              : Protocol.HTTP_1_1;
          success = true;
        } catch (AssertionError e) {
          if (Util.isAndroidGetsocknameError(e)) throw new IOException(e);
          throw e;
        } finally {
          if (sslSocket != null) {
            Platform.get().afterHandshake(sslSocket);
          }
          if (!success) {
            closeQuietly(sslSocket);
          }
        }
      }
}
```