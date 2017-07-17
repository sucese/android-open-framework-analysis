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


首先会从查询缓存，InternalCache是个接口，它在Cache类里做了实现，调用Cache类的同名方法。

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