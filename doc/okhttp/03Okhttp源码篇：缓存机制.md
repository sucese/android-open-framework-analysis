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

这篇文章我们来分析Okhttp的缓存机制，缓存机制是基于DiskLruCache做的。Cache类封装了缓存的实现，实现了
InternalCache接口。

**InternalCache**

```java
public interface InternalCache {
  Response get(Request request) throws IOException;

  CacheRequest put(Response response) throws IOException;

  /**
   * Remove any cache entries for the supplied {@code request}. This is invoked when the client
   * invalidates the cache, such as when making POST requests.
   */
  void remove(Request request) throws IOException;

  /**
   * Handles a conditional request hit by updating the stored cache response with the headers from
   * {@code network}. The cached response body is not updated. If the stored response has changed
   * since {@code cached} was returned, this does nothing.
   */
  void update(Response cached, Response network);

  /** Track an conditional GET that was satisfied by this cache. */
  void trackConditionalCacheHit();

  /** Track an HTTP response being satisfied with {@code cacheStrategy}. */
  void trackResponse(CacheStrategy cacheStrategy);
}

```

**Cache**

```java
final @Nullable Cache cache;
final @Nullable InternalCache internalCache;
  
InternalCache internalCache() {
    return cache != null ? cache.internalCache : internalCache;
}
```
我们来看看get()方法的实现。

```java
public final class Cache implements Closeable, Flushable {
    
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
}
```

Cache类定义一些内部类，这些类封装了请求与响应信息。

- Entry：封装了请求与响应等信息，包括url、varyHeaders、protocol、code、message、responseHeaders、handshake、sentRequestMillis与receivedResponseMillis。
- CacheResponseBody：继承于ResponseBody，封装了缓存快照snapshot，响应体bodySource，内容类型contentType，内容长度contentLength。

我们再来看看Cache里的增、删、查、改四个操作。

## 增




## 删

## 查

```java
public final class Cache implements Closeable, Flushable {
    
       @Nullable Response get(Request request) {
         //通过url生成key
         String key = key(request.url());
         DiskLruCache.Snapshot snapshot;
         Entry entry;
         try {
           //从DiskLruCache取出快照
           snapshot = cache.get(key);
           if (snapshot == null) {
             return null;
           }
         } catch (IOException e) {
           // Give up because the cache cannot be read.
           return null;
         }
     
         try {
           //通过快照生成一个实体类Entry，Entry描述了请求与返回的信息
           entry = new Entry(snapshot.getSource(ENTRY_METADATA));
         } catch (IOException e) {
           Util.closeQuietly(snapshot);
           return null;
         }
     
         //通过Entry获取Response
         Response response = entry.response(snapshot);
     
         if (!entry.matches(request, response)) {
           Util.closeQuietly(response.body());
           return null;
         }
     
         return response;
       }    
}
```

## 改

