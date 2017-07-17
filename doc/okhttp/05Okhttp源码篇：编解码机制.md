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

本篇文章分析Okhttp的请求编码与响应解码机制。

请求编码与响应解码机制定义在HttpCodec接口里。HttpCodec有两个实现类，针对HTTP/1.1的Http1Codec与针对HTTP/2的Http2Codec。

```java
/** Encodes HTTP requests and decodes HTTP responses. */
public interface HttpCodec {
  /**
   * The timeout to use while discarding a stream of input data. Since this is used for connection
   * reuse, this timeout should be significantly less than the time it takes to establish a new
   * connection.
   */
  int DISCARD_STREAM_TIMEOUT_MILLIS = 100;

   
  //返回一个输出流
  /** Returns an output stream where the request body can be streamed. */
  Sink createRequestBody(Request request, long contentLength);

  //写入请求头
  /** This should update the HTTP engine's sentRequestMillis field. */
  void writeRequestHeaders(Request request) throws IOException;

  /** Flush the request to the underlying socket. */
  void flushRequest() throws IOException;

  /** Flush the request to the underlying socket and signal no more bytes will be transmitted. */
  void finishRequest() throws IOException;


  //读取响应头
  /**
   * Parses bytes of a response header from an HTTP transport.
   *
   * @param expectContinue true to return null if this is an intermediate response with a "100"
   *     response code. Otherwise this method never returns null.
   */
  Response.Builder readResponseHeaders(boolean expectContinue) throws IOException;

  //读取响应体
  /** Returns a stream that reads the response body. */
  ResponseBody openResponseBody(Response response) throws IOException;

  /**
   * Cancel this stream. Resources held by this stream will be cleaned up, though not synchronously.
   * That may happen later by the connection pool thread.
   */
  void cancel();
}
```