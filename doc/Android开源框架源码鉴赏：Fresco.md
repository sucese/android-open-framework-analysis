# Android开源框架源码鉴赏：Fresco

作者：[郭孝星](https://github.com/guoxiaoxing)

校对：[郭孝星](https://github.com/guoxiaoxing)

**关于项目**

> [Android Open Framework analysis](https://github.com/guoxiaoxing/android-open-framework-analysis)项目主要用来分析Android平台主流开源框架的源码与原理实现。

**文章目录**e

- 一 图片加载流程
    - 1.1 初始化Fresco
    - 1.2 获取DataSource
    - 1.3 绑定DraweeController与DraweeHierarchy
    - 1.4 从内存缓存/磁盘缓存/网络获取图片，并设置到对应的Drawable层
- 二 DraweeController与DraweeHierarchy
    - 2.1 图层的层级构造
    - 2.2 图层的构建流程
- 三 Producer与Consumer
- 四 缓存机制
    - 3.1 内存缓存
    - 3.2 磁盘缓存
    
更多Android开源框架源码分析文章请参见[Android open framework analysis](https://github.com/guoxiaoxing/android-open-framwork-analysis)。

这个系列的文章原来叫做《Android开源框架源码分析》，后来这些优秀开源库的代码看的多了，感觉大佬们代码写的真真美如画👍，所以就更名为《Android开源框架源码鉴赏》了。闲话
不多说，我们进入正题，今天分析的开源库是Fresco。

Fresco是一个功能完善的图片加载框架，在Android开发中有着广泛的应用，那么它作为一个图片加载框架，有哪些特色让它备受推崇呢？

- 完善的内存管理功能，减少图片对内存的占用，即便在低端机器上也有着不错的表现。
- 自定义图片加载的过程，可以先显示低清晰度图片或者缩略图，加载完成后再显示高清图，可以在加载的时候缩放和旋转图片。
- 自定义图片绘制的过程，可以自定义谷中焦点、圆角图、占位图、overlay、进图条。
- 渐进式显示图片。
- 支持Gif。
- 支持Webp。

好，又吹了一波Fresco（人家好像也不给广告费T_T），但是光知道人家好并没有用，我们还需要为什么这么好，怎么实现的，日后在做我们的框架的时候偷师一手，岂不美哉。
Fresco的源码还是比较多的，看起来会比较费劲，但是不怕，Android的系统源码都被我们啃下来了，还怕一个小小的Fresco吗😎。要更好的去理解Fresco的实现，还是要从
整体入手，了解它的模块和层次划分，层层推进，逐个理解，才能达到融会贯通的效果。

由于Fresco比较大，我们先来看一下它的整体结构，有个整体的把握，Fresco的整体架构如下图所示：

👉 点击图片查看大图

<img src="https://github.com/guoxiaoxing/android-open-framwork-analysis/raw/master/art/fresco/fresco_structure.png" width="600"/>

- DraweeView：继承于ImageView，只是简单的读取xml文件的一些属性值和做一些初始化的工作，图层管理交由Hierarchy负责，图层数据获取交由负责。
- DraweeHierarchy：由多层Drawable组成，每层Drawable提供某种功能（例如：缩放、圆角）。
- DraweeController：控制数据的获取与图片加载，向pipeline发出请求，并接收相应事件，并根据不同事件控制Hierarchy，从DraweeView接收用户的事件，然后执行取消网络请求、回收资源等操作。
- DraweeHolder：统筹管理Hierarchy与DraweeHolder。
- ImagePipeline：Fresco的核心模块，用来以各种方式（内存、磁盘、网络等）获取图像。
- Producer/Consumer：Producer也有很多种，它用来完成网络数据获取，缓存数据获取、图片解码等多种工作，它产生的结果由Consumer进行消费。
- IO/Data：这一层便是数据层了，负责实现内存缓存、磁盘缓存、网络缓存和其他IO相关的功能。

纵观整个Fresco的架构，DraweeView是门面，和用户进行交互，DraweeHierarchy是视图层级，管理图层，DraweeController是控制器，管理数据。它们构成了整个Fresco框架的三驾马车。当然还有我们
幕后英雄Producer，所有的脏活累活都是它干的，最佳劳模👍

理解了Fresco整体的架构，我们还有了解在这套矿建里发挥重要作用的几个关键角色，如下所示：

- Supplier：提供一种特定类型的对象，Fresco里有很多以Supplier结尾的类都实现了这个接口。
- SimpleDraweeView：这个我们就很熟悉了，它接收一个URL，然后调用Controller去加载图片。该类继承于GenericDraweeView，GenericDraweeView又继承于DraweeView，DraweeView是Fresco的顶层View类。
- PipelineDraweeController：负责图片数据的获取与加载，它继承于AbstractDraweeController，由PipelineDraweeControllerBuilder构建而来。AbstractDraweeController实现了DraweeController接口，DraweeController
是Fresco的数据大管家，所以的图片数据的处理都是由它来完成的。
- GenericDraweeHierarchy：负责SimpleDraweeView上的图层管理，由多层Drawable组成，每层Drawable提供某种功能（例如：缩放、圆角），该类由GenericDraweeHierarchyBuilder进行构建，该构建器
将placeholderImage、retryImage、failureImage、progressBarImage、background、overlays与pressedStateOverlay等
xml文件或者Java代码里设置的属性信息都传入GenericDraweeHierarchy中，由GenericDraweeHierarchy进行处理。
- DraweeHolder：该类是一个Holder类，和SimpleDraweeView关联在一起，DraweeView是通过DraweeHolder来统一管理的。而DraweeHolder又是用来统一管理相关的Hierarchy与Controller
- DataSource：类似于Java里的Futures，代表数据的来源，和Futures不同，它可以有多个result。
- DataSubscriber：接收DataSource返回的结果。
- ImagePipeline：用来调取获取图片的接口。
- Producer：加载与处理图片，它有多种实现，例如：NetworkFetcherProducer，LocalAssetFetcherProducer，LocalFileFetchProducer。从这些类的名字我们就可以知道它们是干什么的。
Producer由ProducerFactory这个工厂类构建的，而且所有的Producer都是像Java的IO流那样，可以一层嵌套一层，最终只得到一个结果，这是一个很精巧的设计👍
- Consumer：用来接收Producer产生的结果，它与Producer组成了生产者与消费者模式。

注：Fresco源码里的类的名字都比较长，但是都是按照一定的命令规律来的，例如：以Supplier结尾的类都实现了Supplier接口，它可以提供某一个类型的对象（factory, generator, builder, closure等）。
以Builder结尾的当然就是以构造者模式创建对象的类。

通过上面的描述，想必大家都Fresco有了一个整体的认识，那面对这样庞大的一个库，我们在去分析它的时候需要重点关注哪些点呢？🤔

1. 图片加载流程
2. DraweeController与DraweeHierarchy
3. Producer与Consumer
4. 缓存机制

👉 注：Fresco里还大量运用各种设计模式，例如：Builder、Factory、Wrapper、Producer/Consumer、Adapter等，在阅读源码的时候，大家也要留心这些设计模式的应用与实践。

接下来我们就带着这4个问题去源码中一探究竟。

## 一 图片加载流程

至于分析的手段，还是老套路，先从一个简单的例子入手，展示Fresco是如何加载图片的，然后去分析它的图片加载流程，让大家有个整体的理解，然后再逐个去分析Fresco每个
子模块的功能实现。

好，我们先来写一个小例子。

👉 举例

初始化

```java
Fresco.initialize(this);
```
加载图片

```java
String url = "https://github.com/guoxiaoxing/android-open-framwork-analysis/raw/master/art/fresco/scenery.jpg";
SimpleDraweeView simpleDraweeView = findViewById(R.id.drawee_view);
simpleDraweeView.setImageURI(Uri.parse(url));
```

我们来看一下它的调用流程，序列图如下所示：

👉 点击图片查看大图

<img src="https://github.com/guoxiaoxing/android-open-framwork-analysis/raw/master/art/fresco/image_load_sequence.png"/>

嗯，图看起来有点大，但是不要紧，我们按照颜色将整个流程分为了四大步：

1. 初始化Fresco。
2. 获取DataSource。
3. 绑定Controller与Hierarchy。
4. 从内存缓存/磁盘缓存/网络获取图片，并设置到对应的Drawable层。

👉 注：Fresco里的类虽多，类名虽长，但都是基于接口和Abstract类的设计，每个模块自成一套继承体系，所以只要掌握了它们的继承关系以及不同模块之间的联系，整个
流程还是比较简单的。

由于序列图设计具体细节，为了辅助理解，我们再提供一张总结新的流程图，如下所示：

👉 点击图片查看大图

<img src="https://github.com/guoxiaoxing/android-open-framwork-analysis/raw/master/art/fresco/image_load_structure.png" width="600"/>

接下来，我们就针对这两张图结合具体细节来一一分析。

### 1.1 初始化Fresco

👉 序列图 1.1 -> 1.11

```java
public class Fresco {
    public static void initialize(
        Context context,
        @Nullable ImagePipelineConfig imagePipelineConfig,
        @Nullable DraweeConfig draweeConfig) {
      //... 重复初始化检验
      try {
        //1. 加载so库，这个主要是一些第三方的native库，例如：giflib，libjpeg，libpng，
        //主要用来做图片解码。
        SoLoader.init(context, 0);
      } catch (IOException e) {
        throw new RuntimeException("Could not initialize SoLoader", e);
      }
      //2. 设置传入的配置参数magePipelineConfig。
      context = context.getApplicationContext();
      if (imagePipelineConfig == null) {
        ImagePipelineFactory.initialize(context);
      } else {
        ImagePipelineFactory.initialize(imagePipelineConfig);
      }
      //3. 初始化SimpleDraweeView。
      initializeDrawee(context, draweeConfig);
    }
  
    private static void initializeDrawee(
        Context context,
        @Nullable DraweeConfig draweeConfig) {
      //构建PipelineDraweeControllerBuilderSupplier对象，并传给SimpleDraweeView。
      sDraweeControllerBuilderSupplier =
          new PipelineDraweeControllerBuilderSupplier(context, draweeConfig);
      SimpleDraweeView.initialize(sDraweeControllerBuilderSupplier);
    }  
}
```

可以发现，Fresco在初始化的过程中，主要做了三件事情：

1. 加载so库，这个主要是一些第三方的native库，例如：giflib，libjpeg，libpng，主要用来做图片解码。
2. 设置传入的配置参数magePipelineConfig。
3. 初始化SimpleDraweeView。

这里面我们需要重点关注三个对象：

- ImagePipelineConfig：ImagePipeline参数配置。
- DraweeControllerBuilderSupplier：提供DraweeControllerBuilder用来构建DraweeController。

我们先来看ImagePipelineConfig，ImagePipelineConfig通过建造者模式来构建传递给ImagePipeline的参数，如下所示：

- Bitmap.Config mBitmapConfig; 图片质量。
- Supplier<MemoryCacheParams> mBitmapMemoryCacheParamsSupplier; 内存缓存的配置参数提供者。
- CountingMemoryCache.CacheTrimStrategy mBitmapMemoryCacheTrimStrategy; 内存缓存的削减策略。
- CacheKeyFactory mCacheKeyFactory; CacheKey的创建工厂。
- Context mContext; 上下文环境。
- boolean mDownsampleEnabled; 是否开启图片向下采样。
- FileCacheFactory mFileCacheFactory; 磁盘缓存创建工厂。
- Supplier<MemoryCacheParams> mEncodedMemoryCacheParamsSupplier; 未解码图片缓存配置参数提供者。
- ExecutorSupplier mExecutorSupplier; 线程池提供者。
- ImageCacheStatsTracker mImageCacheStatsTracker; 图片缓存状态追踪器。
- ImageDecoder mImageDecoder; 图片解码器。
- Supplier<Boolean> mIsPrefetchEnabledSupplier; 是否开启预加载。
- DiskCacheConfig mMainDiskCacheConfig; 磁盘缓存配置。
- MemoryTrimmableRegistry mMemoryTrimmableRegistry; 内存变化监听注册表，那些需要监听系统内存变化的对象需要添加到这个表中类。
- NetworkFetcher mNetworkFetcher; 下载网络图片，默认使用内置的HttpUrlConnectionNetworkFetcher，也可以自定义。
- PlatformBitmapFactory mPlatformBitmapFactory; 根据不同的Android版本生成不同的Bitmap的工厂，主要的区别在Bitmap在内存中的位置，Android 5.0以下存储在Ashmem中，Android 5.0以上存在Java Heap中。
- PoolFactory mPoolFactory; Bitmap池等各种池的构建工厂。
- ProgressiveJpegConfig mProgressiveJpegConfig; 渐进式JPEG配置。
- Set<RequestListener> mRequestListeners; 请求监听器集合，监听请求过程中的各种事件。
- boolean mResizeAndRotateEnabledForNetwork; 是否开启网络图片的压缩和旋转。
- DiskCacheConfig mSmallImageDiskCacheConfig; 磁盘缓存配置
- ImageDecoderConfig mImageDecoderConfig; 图片解码配置
- ImagePipelineExperiments mImagePipelineExperiments; Fresco提供的关于Image Pipe的实验性功能。

上述参数基本不需要我们手动配置，除非项目上有定制性的需求。

我们可以发现，在初始化方法的最后调用initializeDrawee()给SimpleDraweeView传入了一个PipelineDraweeControllerBuilderSupplier，这是一个很重要的对象，我们
来看看它都初始化了哪些东西。

```java
public class PipelineDraweeControllerBuilderSupplier implements
    Supplier<PipelineDraweeControllerBuilder> {
    
      public PipelineDraweeControllerBuilderSupplier(
          Context context,
          ImagePipelineFactory imagePipelineFactory,
          Set<ControllerListener> boundControllerListeners,
          @Nullable DraweeConfig draweeConfig) {
        mContext = context;
        //1. 获取ImagePipeline
        mImagePipeline = imagePipelineFactory.getImagePipeline();
    
        if (draweeConfig != null && draweeConfig.getPipelineDraweeControllerFactory() != null) {
          mPipelineDraweeControllerFactory = draweeConfig.getPipelineDraweeControllerFactory();
        } else {
          mPipelineDraweeControllerFactory = new PipelineDraweeControllerFactory();
        }
        //2. 获取PipelineDraweeControllerFactory，并初始化。
        mPipelineDraweeControllerFactory.init(
            context.getResources(),
            DeferredReleaser.getInstance(),
            imagePipelineFactory.getAnimatedDrawableFactory(context),
            UiThreadImmediateExecutorService.getInstance(),
            mImagePipeline.getBitmapMemoryCache(),
            draweeConfig != null
                ? draweeConfig.getCustomDrawableFactories()
                : null,
            draweeConfig != null
                ? draweeConfig.getDebugOverlayEnabledSupplier()
                : null);
        mBoundControllerListeners = boundControllerListeners;
      }
}
```

可以发现在这个方法里初始化了两个重要的对象：

1. 获取ImagePipeline。
2. 获取PipelineDraweeControllerFactory，并初始化。

这个PipelineDraweeControllerFactory就是用来构建PipelineDraweeController，我们前面说过PipelineDraweeController继承于AbstractDraweeController，用来控制图片
数据的获取和加载，这个PipelineDraweeControllerFactory()的init()方法也是将参数里的遍历传入PipelineDraweeControllerFactory中，用来准备构建PipelineDraweeController。
我们来看一下它都传入哪些东西进去。

- context.getResources()：Android的Resources对象。
- DeferredReleaser.getInstance()：延迟释放资源，等主线程处理完消息后再进行回收。
- mImagePipeline.getBitmapMemoryCache()：已解码的图片缓存。

👉 注：所谓拔出萝卜带出泥，在分析图片加载流程的时候难免会带进来各种各样的类，如果一时理不清它们的关系也没关系，第一步只是要掌握整体的加载流程即可，后面
我们会对这些类逐一分析。

该方法执行完成后调用SimpleDraweeView的initizlize()方法将PipelineDraweeControllerBuilderSupplier对象设置进SimpleDraweeView的静态对象sDraweeControllerBuilderSupplier中
整个初始化流程便完成了。

### 1.2 获取DataSource

👉 序列图 2.1 -> 2.12

在分析如何生成DataSource之前，我们得先了解什么DataSource。

>DataSource是一个接口其实现类是AbstractDataSource，它可以提交数据请求，并能获取progress、fail result与success result等信息，类似于Java里的Future。

DataSource接口如下所示：

```java
public interface DataSource<T> {
  //数据源是否关闭
  boolean isClosed();
  //异步请求的结果
  @Nullable T getResult();
  //是否有结果返回
  boolean hasResult();
  //请求是否结束
  boolean isFinished();
  //请求是否发生错误
  boolean hasFailed();
  //发生错误的原因
  @Nullable Throwable getFailureCause();
  //请求的进度[0, 1]
  float getProgress();
  //结束请求，释放资源。 
  boolean close();
  //发送并订阅请求，等待请求结果。
  void subscribe(DataSubscriber<T> dataSubscriber, Executor executor);
}
```
AbstractDataSource实现了DataSource接口，它是一个基础类，其他DataSource类都扩展自该类。AbstractDataSource实现了上述接口里的方法，维护这DataSource的success、progress和fail的状态。
除此之外还有以下DataSource类：

- AbstractProducerToDataSourceAdapter：继承自AbstractDataSource，包装了Producer取数据的过程，也就是创建了一个Consumer，详细的过程我们后面还会说。
- CloseableProducerToDataSourceAdapter：继承自AbstractProducerToDataSourceAdapter，实现了closeResult()方法，绘制自己销毁时同时销毁Result，这个是最主要使用的DataSource。
- ProducerToDataSourceAdapter：没有实现额外的方法，仅仅用于预加载图片。
- IncreasingQualityDataSource：内部维护一个CloseableProducerToDataSourceAdapter列表，按数据的清晰度从后往前递增，它为列表里的每个DataSour测绑定一个DataSubscriber，该类负责保证
每次获取清晰度更高的数据，获取数据的同时销毁清晰度更低的数据。
- FirstAvailableDataSource：内部维护一个CloseableProducerToDataSourceAdapter列表，它会返回列表里最先获取数据的DataSource，它为列表里的每个DataSour测绑定一个DataSubscriber，如果
数据加载成功，则将当前成功的DataSource指定为目标DataSource，否则跳转到下一个DataSource继续尝试。
- SettableDataSource：继承自AbstractDataSource，并将重写settResult()、setFailure()、setProgress()在内部调用父类的相应函数，但是修饰符变成了public（原来是protected）。即使
用SettableDataSource时可以在外部调用这三个函数设置DataSource状态。一般用于在获取DataSource失败时直接产生一个设置为Failure的DataSource。

了解了DataSource，我们再来看看它是如何生成的。

我们知道，在使用Fresco展示图片的时候，只需要调用setImageURI()设置图片URL即可，我们就以这个方法为入口开始分析，如下所示：

```java
public class SimpleDraweeView extends GenericDraweeView {
    
      public void setImageURI(Uri uri, @Nullable Object callerContext) {
        DraweeController controller = mSimpleDraweeControllerBuilder
            .setCallerContext(callerContext)
            .setUri(uri)
            .setOldController(getController())
            .build();
        setController(controller);
      }
}
```
可以发现，SimpleDraweeView将外面传递的URL数据封装进了DraweeController，并调用mSimpleDraweeControllerBuilder构造了一个DraweeController对象，这个
DraweeController对象实际上就是PipelineDraweeController。

我们来看看它是如何构建的，mSimpleDraweeControllerBuilder由sDraweeControllerBuilderSupplier调用get()方法获得，我们前面已经说过sDraweeControllerBuilderSupplier是在
SimpleDraweeView的initialize()被传递进来的，我们接着来看PipelineDraweeController的构建过程。

SimpleDraweeControllerBuilder是调用器父类AbstractDraweeControllerBuilder的build()方法来进行构建，而该build()方法又反过来调用其子类SimpleDraweeControllerBuilder
的obtainController()方法来完成具体子类SimpleDraweeControllerBuilder的构建，我们来看看它的实现。

👉 注：Fresco的设计很好的体现了面向接口编程这一点，大部分功能都基于接口设计，然后设计出抽象类AbstractXXX，用来封装通用的功能，个别具体的功能交由其子类实现。

```java
public class PipelineDraweeControllerBuilder extends AbstractDraweeControllerBuilder<
    PipelineDraweeControllerBuilder,
    ImageRequest,
    CloseableReference<CloseableImage>,
    ImageInfo> {
    
      @Override
      protected PipelineDraweeController obtainController() {
        DraweeController oldController = getOldController();
        PipelineDraweeController controller;
        //如果已经有PipelineDraweeController，则进行复用，否则构建新的PipelineDraweeController。
        if (oldController instanceof PipelineDraweeController) {
          controller = (PipelineDraweeController) oldController;
          controller.initialize(
              obtainDataSourceSupplier(),
              generateUniqueControllerId(),
              getCacheKey(),
              getCallerContext(),
              mCustomDrawableFactories);
        } else {
          controller = mPipelineDraweeControllerFactory.newController(
              obtainDataSourceSupplier(),
              generateUniqueControllerId(),
              getCacheKey(),
              getCallerContext(),
              mCustomDrawableFactories);
        }
        return controller;
      }
}
```

可以发现上述函数的逻辑也很简单，如果已经有PipelineDraweeController，则进行复用，否则调用PipelineDraweeControllerFactory.newController()方法构建
新的PipelineDraweeController。PipelineDraweeControllerFactory.newController()方法最终调用PipelineDraweeController的构造方法完成PipelineDraweeController
对象的构建，后续的流程很简单，我们重点关注在构建的过程中传入了哪些对象，这些对象是如何生成的。

- obtainDataSourceSupplier()：获取数据源。
- generateUniqueControllerId()：生成唯一的Controller ID。
- getCacheKey()：获取缓存key。
- getCallerContext()：获取调用者的上下为环境。
- ImmutableList<DrawableFactory>列表，用来生成各种图片效果的Drawable。

其他的实现都比较简单，我们重点关注obtainDataSourceSupplier()的实现，如下所示：

```java
public class PipelineDraweeControllerBuilder extends AbstractDraweeControllerBuilder<
    PipelineDraweeControllerBuilder,
    ImageRequest,
    CloseableReference<CloseableImage>,
    ImageInfo> {
    
      protected Supplier<DataSource<IMAGE>> obtainDataSourceSupplier() {
        if (mDataSourceSupplier != null) {
          return mDataSourceSupplier;
        }
    
        Supplier<DataSource<IMAGE>> supplier = null;
    
        //1. 生成最终的image supplier。
        if (mImageRequest != null) {
          supplier = getDataSourceSupplierForRequest(mImageRequest);
        } else if (mMultiImageRequests != null) {
          supplier = getFirstAvailableDataSourceSupplier(mMultiImageRequests, mTryCacheOnlyFirst);
        }
    
        //2. 生成一个ncreasing-quality supplier，这里会有两级的清晰度，高清晰度的supplier优先。
        if (supplier != null && mLowResImageRequest != null) {
          List<Supplier<DataSource<IMAGE>>> suppliers = new ArrayList<>(2);
          suppliers.add(supplier);
          suppliers.add(getDataSourceSupplierForRequest(mLowResImageRequest));
          supplier = IncreasingQualityDataSourceSupplier.create(suppliers);
        }
    
        //如果没有图片请求，则提供一个空的supplier。
        if (supplier == null) {
          supplier = DataSources.getFailedDataSourceSupplier(NO_REQUEST_EXCEPTION);
        }
    
        return supplier;
      }
}
```

getDataSourceSupplierForRequest()方法最终调用（具体调用链可以参照序列图，这里就不再赘述）的是PipelineDraweeControllerBuilder的getDataSourceForRequest()

```java
public class PipelineDraweeControllerBuilder extends AbstractDraweeControllerBuilder<
    PipelineDraweeControllerBuilder,
    ImageRequest,
    CloseableReference<CloseableImage>,
    ImageInfo> {
    
      @Override
      protected DataSource<CloseableReference<CloseableImage>> getDataSourceForRequest(
          ImageRequest imageRequest,
          Object callerContext,
          AbstractDraweeControllerBuilder.CacheLevel cacheLevel) {
        
        //调用ImagePipeline的fetchDecodedImage()方法获取DataSource
        return mImagePipeline.fetchDecodedImage(
            imageRequest,
            callerContext,
            convertCacheLevelToRequestLevel(cacheLevel));
      }
}
```

>ImagePipeline是Fresco Image Pipeline的入口类，前面也说过ImagePipeline是Fresco的核心模块，用来以各种方式（内存、磁盘、网络等）获取图像。

这个mImagePipeline就是在PipelineDraweeControllerBuilderSupplier中调用ImagePipelineFactory的getImagePipeline()方法创建的。
我们接着来看ImagePipeline的fetchDecodedImage()方法，如下所示：

```java
public class ImagePipeline {
    
    public DataSource<CloseableReference<CloseableImage>> fetchDecodedImage(
        ImageRequest imageRequest,
        Object callerContext,
        ImageRequest.RequestLevel lowestPermittedRequestLevelOnSubmit) {
      try {
        //1. 获取Producer序列，为DataSource提供不同的数据输入管道。
        Producer<CloseableReference<CloseableImage>> producerSequence =
            mProducerSequenceFactory.getDecodedImageProducerSequence(imageRequest);
        //2. 调用submitFetchRequest()方法生成DataSource。
        return submitFetchRequest(
            producerSequence,
            imageRequest,
            lowestPermittedRequestLevelOnSubmit,
            callerContext);
      } catch (Exception exception) {
        return DataSources.immediateFailedDataSource(exception);
      }
    }  
}
```

关于什么是Producer，我们前面也已经说过。

>Producer用来加载与处理图片，它有多种实现，例如：NetworkFetcherProducer，LocalAssetFetcherProducer，LocalFileFetchProducer。从这些类的名字我们就可以知道它们是干什么的。
 Producer由ProducerFactory这个工厂类构建的，而且所有的Producer都是像Java的IO流那样，可以一层嵌套一层，最终只得到一个结果，
 
关于Producer的更多内容，我们后面会专门讲，这个方法主要做了两件事情：

1. 获取Producer序列，为DataSource提供不同的数据输入管道，Producer是由很多种的，代表从不同途径获取图片数据，我们下面会详细讲。
2. 调用submitFetchRequest()方法生成DataSource。

可以发现该方法最终调用submitFetchRequest()方法生成了DataSource，如下所示：

```java
public class ImagePipeline {
    
    private <T> DataSource<CloseableReference<T>> submitFetchRequest(
          Producer<CloseableReference<T>> producerSequence,
          ImageRequest imageRequest,
          ImageRequest.RequestLevel lowestPermittedRequestLevelOnSubmit,
          Object callerContext) {
        final RequestListener requestListener = getRequestListenerForRequest(imageRequest);
    
        try {
          //1. 获取缓存级别，RequestLevel将缓存分为四级 FULL_FETCH(1) 从网络或者本地存储获取，DISK_CACHE(2) 从磁盘缓存获取，ENCODED_MEMORY_CACHE(3)从
          //未接吗的内存缓存获取，BITMAP_MEMORY_CACHE(4)已解码的内存缓存获取。
          ImageRequest.RequestLevel lowestPermittedRequestLevel =
              ImageRequest.RequestLevel.getMax(
                  imageRequest.getLowestPermittedRequestLevel(),
                  lowestPermittedRequestLevelOnSubmit);
          //2. 将ImageRequest、RequestListener等信息封装进SettableProducerContext，ProducerContext是Producer
          //的上下文环境，利用ProducerContext可以改变Producer内部的状态。
          SettableProducerContext settableProducerContext = new SettableProducerContext(
              imageRequest,
              generateUniqueFutureId(),
              requestListener,
              callerContext,
              lowestPermittedRequestLevel,
            /* isPrefetch */ false,
              imageRequest.getProgressiveRenderingEnabled() ||
                  imageRequest.getMediaVariations() != null ||
                  !UriUtil.isNetworkUri(imageRequest.getSourceUri()),
              imageRequest.getPriority());
          //3. 创建CloseableProducerToDataSourceAdapter，CloseableProducerToDataSourceAdapter是DataSource的一种。
          return CloseableProducerToDataSourceAdapter.create(
              producerSequence,
              settableProducerContext,
              requestListener);
        } catch (Exception exception) {
          return DataSources.immediateFailedDataSource(exception);
        }
      }
}
```

该方法主要做了三件事情：

1. 获取缓存级别，RequestLevel将缓存分为四级 FULL_FETCH(1) 从网络或者本地存储获取，DISK_CACHE(2) 从磁盘缓存获取，ENCODED_MEMORY_CACHE(3)从
未接吗的内存缓存获取，BITMAP_MEMORY_CACHE(4)已解码的内存缓存获取。
2. 将ImageRequest、RequestListener等信息封装进SettableProducerContext，ProducerContext是Producer
的上下文环境，利用ProducerContext可以改变Producer内部的状态。
3. 创建CloseableProducerToDataSourceAdapter，CloseableProducerToDataSourceAdapter是DataSource的一种。

接着CloseableProducerToDataSourceAdapter调用了自己create()方法构建一个CloseableProducerToDataSourceAdapter对象。至此DataSource已经完成完成了，然后把它设置到
PipelineDraweeController里。

我们接着来看绑定Controller与Hierarchy的流程。👇

### 1.3 绑定DraweeController与DraweeHierarchy

👉 序列图 3.1 -> 3.7

前面提到在SimpleDraweeView的setImageURI()方法里会为SimpleDraweeView设置前面构建好的PipelineDraweeController，如下所示：

```java
  public void setImageURI(Uri uri, @Nullable Object callerContext) {
    DraweeController controller = mSimpleDraweeControllerBuilder
        .setCallerContext(callerContext)
        .setUri(uri)
        .setOldController(getController())
        .build();
    setController(controller);
  }
```

从上面的序列图得知，setController()方法经过层层调用，最终调用的是DraweeHolder的setController()方法，DraweeHolder用来统筹管理Controller与Hierarchy，它是DraweeView的一个
成员变量，在DraweeHolder对象初始化的时候被构建，我们来看看它的setController()方法，如下所示：

```java
public class DraweeHolder<DH extends DraweeHierarchy>
    implements VisibilityCallback {
      public void setController(@Nullable DraweeController draweeController) {
        boolean wasAttached = mIsControllerAttached;
        //1. 如果已经和Controller建立联系，则先detach。
        if (wasAttached) {
          detachController();
        }
    
        //2. 清楚旧的Controller。
        if (isControllerValid()) {
          mEventTracker.recordEvent(Event.ON_CLEAR_OLD_CONTROLLER);
          mController.setHierarchy(null);
        }
        
        //3. 为Controller重新设置Hierarchy)建立新的Controller。
        mController = draweeController;
        if (mController != null) {
          mEventTracker.recordEvent(Event.ON_SET_CONTROLLER);
          mController.setHierarchy(mHierarchy);
        } else {
          mEventTracker.recordEvent(Event.ON_CLEAR_CONTROLLER);
        }
    
        //4. 对DraweeHolder和Controller进行attach操作。
        if (wasAttached) {
          attachController();
        }
      }
 }
```

上述方法的流程也十分简单，如下所示:

1. 如果已经和Controller建立联系，则先detach。
2. 清楚旧的Controller。
3. 为Controller重新设置Hierarchy)建立新的Controller。
4. 对DraweeHolder和Controller进行attach操作。

上述流程里有两个关键的地方：设置Hierarchy和attch操作，我们分别来看看，

从上面的序列图可以看出，这个mHierarchy是在GenricDraweeView的构造方法里调用inflateHierarchy()方法创建的，它实际上是一个GenericDraweeHierarchy对象，而setHierarchy()方法
最终调用的是AbstractDraweeController的setHierarchy()方法，如下所示：

```java
public abstract class AbstractDraweeController<T, INFO> implements
    DraweeController,
    DeferredReleaser.Releasable,
    GestureDetector.ClickListener {
    
      public void setHierarchy(@Nullable DraweeHierarchy hierarchy) {
        //... log
        mEventTracker.recordEvent(
            (hierarchy != null) ? Event.ON_SET_HIERARCHY : Event.ON_CLEAR_HIERARCHY);
        //1. 释放掉当前正在进行的请求。
        if (mIsRequestSubmitted) {
          mDeferredReleaser.cancelDeferredRelease(this);
          release();
        }
        //2. 清除已经存在的Hierarchy。
        if (mSettableDraweeHierarchy != null) {
          mSettableDraweeHierarchy.setControllerOverlay(null);
          mSettableDraweeHierarchy = null;
        }
        //3. 设置新的Hierarchy。
        if (hierarchy != null) {
          Preconditions.checkArgument(hierarchy instanceof SettableDraweeHierarchy);
          mSettableDraweeHierarchy = (SettableDraweeHierarchy) hierarchy;
          mSettableDraweeHierarchy.setControllerOverlay(mControllerOverlay);
        }
      }
    }
```

这个mSettableDraweeHierarchy实际的实现类是GenericDraweeHierarchy，

走到这里，DraweeController与DraweeHierarchy的绑定流程就完成了。

### 1.4 从内存缓存/磁盘缓存/网络获取图片，并设置到对应的Drawable层

👉 序列图 4.1 -> 4.14

这一块的内容主要执行上面创建的各种Producer，从从内存缓存/磁盘缓存/网络获取图片，并调用对应的Consumer消费结果，最终
不同的Drawable设置到对应的图层中去，关于DraweeHierarchy与Producer我们下面都会详细的讲，我们先来看看上面层层请求到
图片最终是如何设置到SimpleDraweeView中去的，如下所示：

```java
public class GenericDraweeHierarchy implements SettableDraweeHierarchy {
    @Override
    public void setImage(Drawable drawable, float progress, boolean immediate) {
      drawable = WrappingUtils.maybeApplyLeafRounding(drawable, mRoundingParams, mResources);
      drawable.mutate();
      //mActualImageWrapper就是实际加载图片的那个图层，此处要设置的SimpleDraweeView最终要显示的图片。
      mActualImageWrapper.setDrawable(drawable);
      mFadeDrawable.beginBatchMode();
      fadeOutBranches();
      fadeInLayer(ACTUAL_IMAGE_INDEX);
      setProgress(progress);
      if (immediate) {
        mFadeDrawable.finishTransitionImmediately();
      }
      mFadeDrawable.endBatchMode();
    }  
}
```
mActualImageWrapper就是实际加载图片的那个图层，此处要设置的SimpleDraweeView最终要显示的图片。

如此，一个SimpleDraweeView的图片加载流程就完成了，面对如此长的流程，读者不免疑惑，我们只要掌握了整体流程，就可以
分而治之，逐个击破。

## 二 DraweeHierarchy

Fresco的图片效果是依赖于Drawee实现的，也就是Drawable层级。

>DraweeHierarchy是Fresco里的Drawable层级，它是一层一层叠加在DraweeView上的来实现各种效果，例如：占位图、失败图、加载进度图等，DraweeHierarchy是一个接口，它还有个
子接口SettableDraweeHierarchy，它们的实现类是GenericDraweeHierarchy。

DraweeHierarchy接口与SettableDraweeHierarchy接口如下所示：

```java
public interface DraweeHierarchy {
  //获取顶层的Drawable，也就是其父节点的图层
  Drawable getTopLevelDrawable();
}

public interface SettableDraweeHierarchy extends DraweeHierarchy {
  //由DraweeController调用，重置DraweeHierarchy状态
  void reset();
   //由DraweeController调用，设置图片数据，progress在渐进式JPEG里使用，immediate表示是否立即显示这张图片
  void setImage(Drawable drawable, float progress, boolean immediate);
   //由DraweeController调用，更新图片加载进度【0, 1】，progress为1或者immediate为true时的时候会隐藏进度条。
  void setProgress(float progress, boolean immediate);
   //由DraweeController调用，设置失败原因，DraweeHierarchy可以根据不同的原因展示不同的失败图片。
  void setFailure(Throwable throwable);
   //由DraweeController调用，设置重试原因，DraweeHierarchy可以根据不同的原因展示不同的重试图片。
  void setRetry(Throwable throwable);
   //由DraweeController调用，设置其他的Controller覆盖层
  void setControllerOverlay(Drawable drawable);
}
```

理解了DraweeHierarchy的大致接口，我们继续从以下几个角度来解析DraweeHierarchy：

- 图层的层级构造
- 图层的创建流程

### 2.1 图层的层级构造

Fresco里定义了许多Drawable，它们都直接或者间接的继承了Drawable，来实现不同的功能。它们的图层层级如下所示：

````
o RootDrawable (top level drawable)
|
+--o FadeDrawable
  |
  +--o ScaleTypeDrawable (placeholder branch, optional)
  |  |
  |  +--o Drawable (placeholder image)
  |
  +--o ScaleTypeDrawable (actual image branch)
  |  |
  |  +--o ForwardingDrawable (actual image wrapper)
  |     |
  |     +--o Drawable (actual image)
  |
  +--o null (progress bar branch, optional)
  |
  +--o Drawable (retry image branch, optional)
  |
  +--o ScaleTypeDrawable (failure image branch, optional)
     |
     +--o Drawable (failure image)
````

Fresco里的Drawable子类有很多，按照功能划分可以分为三大类：

容器类Drawable

- ArrayDrawable：内部存储着一个Drawable数组，与Android里的LayerDrawable类似，将数组里的Drawable当作图层，按照数组的顺序绘制Drawable，数组最后
的成员会在最上方，不过它和LayerDrawable也有不同的地方：① 绘制顺序是数组的顺序，但是ArrayDrawable会跳过暂时不需要绘制的图层。② 不支持动态添加图层。
- FadeDrawable：继承与ArrayDrawable，除了具有ArrayDrawable的功能外，它还可以隐藏和显示图层。

容器类Drawable

- ForwardingDrawable：内部为患者一个Drawable成员变量，将Drawable的一些基本操作和回调传递给目标Drawable，它是所以容器类Drawable的基类。
- ScaleTypeDrawable：继承于ForwardingDrawable，封装了对代理图片的缩放处理。
- SettableDrawable：继承于ForwardingDrawable，可以多次设置内容Drawable的容器，多用在目标图片的图层中。
- AutoRotateDrawable：继承于ForwardingDrawable，提供内容动态旋转的容器。
- OrientedDrawable：继承于ForwardingDrawable，可以将内容Drawable以一个特定的角度绘制的容器。
- MatrixDrawable：继承于ForwardingDrawable，可以为内容应用变形矩阵的容器，它只能赋予给显示目标图片的那个图层。不能在一个图层上同时使用MatrixDrawable与ScaleTypeDrawable！
- RoundedCornersDrawable：继承于ForwardingDrawable，可以将内容的边界修剪成圆角矩形（目前版本暂不支持）或用实心的圆角矩形覆盖内容的容器。
- GenericDraweeHierarchy.RootDrawable：继承于ForwardingDrawable，专门用于顶层图层的容器。

视图类Drawable

- ProgressBarDrawable：负责绘制进度条。
- RoundedBitmapDrawable：将自身内容修剪成圆角矩形绘制出来，可以使用Bitmap作为对象，返回一个BitmapDrawable。

除了这些Drawable类以为，还有一个Drawable接口，凡是做matrix变换和圆角处理的Drawable都实现了这个接口，这是为了子Drawable可以父Drawable的
变换矩阵和圆角节点，以便可以正确的绘制自己。

如下所示：

```java
public interface TransformAwareDrawable {
  //设置TransformCallback回调
  void setTransformCallback(TransformCallback transformCallback);
}

public interface TransformCallback {
  //获取应用在Drawable上的所有matrices矩阵，存储在transform中
  void getTransform(Matrix transform);
  //获取Drawable的根节点边界，存储在bounds中。
  void getRootBounds(RectF bounds);
}
```

从用户的角度，SimpleDraweeView上的图层主要被分成了以下几层：

- 背景图（backgroundImage）
- 占位图（placeholderImage=）
- 加载的图片（actualImage）
- 进度条（progressBarImage）
- 重试加载的图片（retryImage）
- 失败图片（failureImage）
- 叠加图（overlayImage）

理解了图层的层级构造，我们接着来看看图层的创建流程。👇

### 2.2 图层的创建流程
 
我们前面说过在GenericDraweeView的构造方法里，调用了它的inflateHierarchy()方法构建了一个GenericDraweeHierarchy对象，GenericDraweeHierarchy的实际
是由GenericDraweeHierarchyBuild调用build()方法来完成的。

GenericDraweeHierarchy是负责加载每个图层信息的载体，我们来看看它的构造方法的实现，如下所示：

```java
public class GenericDraweeHierarchy implements SettableDraweeHierarchy {
    
      //就跟我们上面说的一样，7个图层。
      
      //背景图层
      private static final int BACKGROUND_IMAGE_INDEX = 0;
      //占位图层
      private static final int PLACEHOLDER_IMAGE_INDEX = 1;
      //加载的图片图层
      private static final int ACTUAL_IMAGE_INDEX = 2;
      //进度条
      private static final int PROGRESS_BAR_IMAGE_INDEX = 3;
      //重试加载的图片
      private static final int RETRY_IMAGE_INDEX = 4;
      //失败图片
      private static final int FAILURE_IMAGE_INDEX = 5;
      //叠加图
      private static final int OVERLAY_IMAGES_INDEX = 6;
    
      GenericDraweeHierarchy(GenericDraweeHierarchyBuilder builder) {
        mResources = builder.getResources();
        mRoundingParams = builder.getRoundingParams();
    
        //实际加载图片的Drawable
        mActualImageWrapper = new ForwardingDrawable(mEmptyActualImageDrawable);
    
        int numOverlays = (builder.getOverlays() != null) ? builder.getOverlays().size() : 1;
        numOverlays += (builder.getPressedStateOverlay() != null) ? 1 : 0;
    
        //图层数量
        int numLayers = OVERLAY_IMAGES_INDEX + numOverlays;
    
        // array of layers
        Drawable[] layers = new Drawable[numLayers];
        //1. 构建背景图层Drawable。
        layers[BACKGROUND_IMAGE_INDEX] = buildBranch(builder.getBackground(), null);
        //2. 构建占位图层Drawable。
        layers[PLACEHOLDER_IMAGE_INDEX] = buildBranch(
            builder.getPlaceholderImage(),
            builder.getPlaceholderImageScaleType());
        //3. 构建加载的图片图层Drawable。
        layers[ACTUAL_IMAGE_INDEX] = buildActualImageBranch(
            mActualImageWrapper,
            builder.getActualImageScaleType(),
            builder.getActualImageFocusPoint(),
            builder.getActualImageColorFilter());
        //4. 构建进度条图层Drawable。
        layers[PROGRESS_BAR_IMAGE_INDEX] = buildBranch(
            builder.getProgressBarImage(),
            builder.getProgressBarImageScaleType());
        //5. 构建重新加载的图片图层Drawable。
        layers[RETRY_IMAGE_INDEX] = buildBranch(
            builder.getRetryImage(),
            builder.getRetryImageScaleType());
        //6. 构建失败图片图层Drawable。
        layers[FAILURE_IMAGE_INDEX] = buildBranch(
            builder.getFailureImage(),
            builder.getFailureImageScaleType());
        if (numOverlays > 0) {
          int index = 0;
          if (builder.getOverlays() != null) {
            for (Drawable overlay : builder.getOverlays()) {
              //7. 构建叠加图图层Drawable。
              layers[OVERLAY_IMAGES_INDEX + index++] = buildBranch(overlay, null);
            }
          } else {
            index = 1; // reserve space for one overlay
          }
          if (builder.getPressedStateOverlay() != null) {
            layers[OVERLAY_IMAGES_INDEX + index] = buildBranch(builder.getPressedStateOverlay(), null);
          }
        }
    
        // fade drawable composed of layers
        mFadeDrawable = new FadeDrawable(layers);
        mFadeDrawable.setTransitionDuration(builder.getFadeDuration());
    
        // rounded corners drawable (optional)
        Drawable maybeRoundedDrawable =
            WrappingUtils.maybeWrapWithRoundedOverlayColor(mFadeDrawable, mRoundingParams);
    
        // top-level drawable
        mTopLevelDrawable = new RootDrawable(maybeRoundedDrawable);
        mTopLevelDrawable.mutate();
    
        resetFade();
      }
}
```
这个方法主要是构建各个图层的Drawable对象，如下所示：

1. 构建背景图层Drawable。
2. 构建占位图层Drawable。
3. 构建加载的图片图层Drawable。
4. 构建进度条图层Drawable。
5. 构建重新加载的图片图层Drawable。
6. 构建失败图片图层Drawable。
7. 构建叠加图图层Drawable。

而构建的方法设计到两个方法

```java
public class GenericDraweeHierarchy implements SettableDraweeHierarchy {
     @Nullable
     private Drawable buildActualImageBranch(
         Drawable drawable,
         @Nullable ScaleType scaleType,
         @Nullable PointF focusPoint,
         @Nullable ColorFilter colorFilter) {
       drawable.setColorFilter(colorFilter);
       drawable = WrappingUtils.maybeWrapWithScaleType(drawable, scaleType, focusPoint);
       return drawable;
     }
   
     /** Applies scale type and rounding (both if specified). */
     @Nullable
     private Drawable buildBranch(@Nullable Drawable drawable, @Nullable ScaleType scaleType) {
       //如果需要为Drawable设置Round，RoundedBitmapDrawable或者RoundedColorDrawable。
       drawable = WrappingUtils.maybeApplyLeafRounding(drawable, mRoundingParams, mResources);
       //如果需要为Drawable设置ScaleType，则将它包装成一个ScaleTypeDrawable。
       drawable = WrappingUtils.maybeWrapWithScaleType(drawable, scaleType);
       return drawable;
     } 
}
```

构建Drawable的过程中都要应用相应的缩放类型和圆角角度，如下所示：

- 如果需要为Drawable设置Round，RoundedBitmapDrawable或者RoundedColorDrawable。
- 如果需要为Drawable设置ScaleType，则将它包装成一个ScaleTypeDrawable。

这样一个图层的载体GenericDraweeHierarchy就构建完成了，后续GenericDraweeHierarchy里的各种操作都是调用器内部的各种Drawable的方法来完成的。

## 三 Producer与Consumer

我们前面说过Producer是Fresco的最佳劳模，所有的脏话累活都是它干的，我们来看看它的实现。

```java
public interface Producer<T> {
  //开始处理任务，执行的结果右Consumer进行消费。
  void produceResults(Consumer<T> consumer, ProducerContext context);
}
```
Fresco里实现了多个Producer，按照功能划分可以分为以下几类：

>本地数据获取P类roducer，这类Producer负责从本地获取数据。

- LocalFetchProducer：实现了Producer接口，所有本地数据Producer获取的基类。
- LocalAssetFetchProducer 继承于LocalFetchProducer，通过AssetManager获取ImageRequest对象的输入流及对象字节码长度，将它转换为EncodedImage；
- LocalContentUriFetchProducer 继承于LocalFetchProducer，若Uri指向联系人，则获取联系人头像；若指向相册图片，则会根据是否传入ResizeOption进行一定缩放（这里不是完全按ResizeOption缩放）；若止这两个条件都不满足，则直接调用ContentResolver的函数openInputStream(Uri uri)获取输入流并转化为EncodedImage；
- LocalFileFetchProducer 继承于LocalFetchProducer，直接通过指定文件获取输入流，从而转化为EncodedImage；
- LocalResourceFetchProducer 继承于LocalFetchProducer，通过Resources的函数openRawResources获取输入流，从而转化为EncodedImage。

- LocalExifThumbnailProducer 没有继承于LocalFetchProducer，可以获取Exif图像的Producer；
- LocalVideoThumbnailProducer 没有继承于LocalFetchProducer，可以获取视频缩略图的Producer。

>网络数据获取类Producer，这类Producer负责从网络获取数据。

- NetworkFetchProducer：实现了Producer接口，从网络上获取图片数据。

>缓存数据获取类Producer，这类Producer负责从缓存中获取数据。

- BitmapMemoryCacheGetProducer 它是一个Immutable的Producer，仅用于包装后续Producer；
- BitmapMemoryCacheProducer 在已解码的内存缓存中获取数据；若未找到，则在nextProducer中获取数据，并在获取到数据的同时将其缓存；
- BitmapMemoryCacheKeyMultiplexProducer 是MultiplexProducer的子类，nextProducer为BitmapMemoryCacheProducer，将多个拥有相同已解码内存缓存键的ImageRequest进行“合并”，若缓存命中，它们都会获取到该数据；
- PostprocessedBitmapMemoryCacheProducer 在已解码的内存缓存中寻找PostProcessor处理过的图片。它的nextProducer都是PostProcessorProducer，因为如果没有获取到被PostProcess的缓存，就需要对获取的图片进行PostProcess。；若未找到，则在nextProducer中获取数据；
- EncodedMemoryCacheProducer 在未解码的内存缓存中寻找数据，如果找到则返回，使用结束后释放资源；若未找到，则在nextProducer中获取数据，并在获取到数据的同时将其缓存；
- EncodedCacheKeyMultiplexProducer 是MultiplexProducer的子类，nextProducer为EncodedMemoryCacheProducer，将多个拥有相同未解码内存缓存键的ImageRequest进行“合并”，若缓存命中，它们都会获取到该数据；
- DiskCacheProducer 在文件内存缓存中获取数据；若未找到，则在nextProducer中获取数据，并在获取到数据的同时将其缓存

>功能类Producer，这类Producer在初始化的时候会传入一个nextProducer，它们会对nextProducer产生的结果进行处理。

- MultiplexProducer 将多个拥有相同CacheKey的ImageRequest进行“合并”，让他们从都从nextProducer中获取数据；
- ThreadHandoffProducer 将nextProducer的produceResult方法放在后台线程中执行（线程池容量为1）；
- SwallowResultProducer 将nextProducer的获取的数据“吞”掉，回在Consumer的onNewResult中传入null值；
- ResizeAndRotateProducer 将nextProducer产生的EncodedImage根据EXIF的旋转、缩放属性进行变换（如果对象不是JPEG格式图像，则不会发生变换）；
- PostProcessorProducer 将nextProducer产生的EncodedImage根据PostProcessor进行修改，关于PostProcessor详见修改图片；
- DecodeProducer 将nextProducer产生的EncodedImage解码。解码在后台线程中执行，可以在ImagePipelineConfig中通过setExecutorSupplier来设置线程池数量，默认为最大可用的处理器数；
- WebpTranscodeProducer 若nextProducer产生的EncodedImage为WebP格式，则将其解码成DecodeProducer能够处理的EncodedImage。解码在后代进程中进行。

那么这些Producer是在哪里构建的呢？🤔

我们前面说过，在构建DataSource的时候，会调用ProducerSequenceFactory.getDecodedImageProducerSequence(imageRequest);方法为指定的ImageRequest构建
想要的Producer序列，事实上，ProducerSequenceFactory里除了getDecodedImageProducerSequence()方法以为，还有几个针对其他情况获取序列的方法，这里我们
列一下从网络获取图片的时候Producer序列是什么样的。

如下所示：

1. PostprocessedBitmapMemoryCacheProducer，非必须	，在Bitmap缓存中查找被PostProcess过的数据。
2. PostprocessorProducer，非必须，对下层Producer传上来的数据进行PostProcess。
3. BitmapMemoryCacheGetProducer，必须，使Producer序列只读。
4. ThreadHandoffProducer，必须，使下层Producer工作在后台进程中执行。
5. BitmapMemoryCacheKeyMultiplexProducer，必须，使多个相同已解码内存缓存键的ImageRequest都从相同Producer中获取数据。
6. BitmapMemoryCacheProducer，必须，从已解码的内存缓存中获取数据。
7. DecodeProducer，必须，将下层Producer产生的数据解码。
8. ResizeAndRotateProducer，非必须，将下层Producer产生的数据变换。
9. EncodedCacheKeyMultiplexProducer，必须，使多个相同未解码内存缓存键的ImageRequest都从相同Producer中获取数据。
10. EncodedMemoryCacheProducer，必须，从未解码的内存缓存中获取数据。
11. DiskCacheProducer，必须，从文件缓存中获取数据。
12. WebpTranscodeProducer，非必须，将下层Producer产生的Webp（如果是的话）进行解码。
13. NetworkFetchProducer，必须，从网络上获取数据。

我们上面说道Producer产生的结果由Consumer来消费，那它又是如何创建的呢？🤔

Producer在处理数据时是向下传递的，而Consumer来接收结果时则是向上传递的，基本上Producer在接收上层传递的Consumer进行包装，我们举个小例子。

在上面的流程分析中，我们说过最终创建的DataSource是CloseableProducerToDataSourceAdapter，CloseableProducerToDataSourceAdapter的父类是AbstractProducerToDataSourceAdapter，在它的
构造方法中会调用createConsumer()来创建第一层Consumer，如下所示：

```java
public abstract class AbstractProducerToDataSourceAdapter<T> extends AbstractDataSource<T> {
    
     private Consumer<T> createConsumer() {
       return new BaseConsumer<T>() {
         @Override
         protected void onNewResultImpl(@Nullable T newResult, @Status int status) {
           AbstractProducerToDataSourceAdapter.this.onNewResultImpl(newResult, status);
         }
   
         @Override
         protected void onFailureImpl(Throwable throwable) {
           AbstractProducerToDataSourceAdapter.this.onFailureImpl(throwable);
         }
   
         @Override
         protected void onCancellationImpl() {
           AbstractProducerToDataSourceAdapter.this.onCancellationImpl();
         }
   
         @Override
         protected void onProgressUpdateImpl(float progress) {
           AbstractProducerToDataSourceAdapter.this.setProgress(progress);
         }
       };
     } 
}
```

从上面列出的Producer序列可以看出，第一层Producer就是PostprocessedBitmapMemoryCacheProducer，在它的produceResults()方法中，会对上面传递下来的Consumer进行包装，如下所示：

```java
public class PostprocessedBitmapMemoryCacheProducer
    implements Producer<CloseableReference<CloseableImage>> {
    
     @Override
     public void produceResults(
         final Consumer<CloseableReference<CloseableImage>> consumer,
         final ProducerContext producerContext) {
         //...
         final boolean isRepeatedProcessor = postprocessor instanceof RepeatedPostprocessor;
         Consumer<CloseableReference<CloseableImage>> cachedConsumer = new CachedPostprocessorConsumer(
             consumer,
             cacheKey,
             isRepeatedProcessor,
             mMemoryCache);
         listener.onProducerFinishWithSuccess(
             requestId,
             getProducerName(),
             listener.requiresExtraMap(requestId) ? ImmutableMap.of(VALUE_FOUND, "false") : null);
         mInputProducer.produceResults(cachedConsumer, producerContext);
         //...
     }
}
```

当PostprocessedBitmapMemoryCacheProducer调用自己的produceResults()处理自己的任务时，会继续调用下一层的Producer，当所有的Producer都完成自己的工作
以后，结果就由下至上层层返回到最上层的Consumer回调中，最终将结果返回给调用者。

Fresco里的Producer是按照一定的顺序进行排列，一个执行完了，继续执行下一个。

以上便是Fresco里整个Producer/Consumer结构。

## 三 缓存机制

Fresco里有三级缓存，两级内存缓存，一级磁盘缓存，如下图所示：

👉 点击图片查看大图

<img src="https://github.com/guoxiaoxing/android-open-framwork-analysis/raw/master/art/fresco/three_level_cache_structure.png" width="600"/>

- 未编码图片内存缓存
- 已编码图片内存缓存
- 磁盘缓存

磁盘缓存因为涉及到文件读写要比内存缓存复杂一些，从下至上可以将磁盘缓存分为三层：

- 缓冲缓存层：由BufferedDiskCache实现，提供缓冲功能。
- 文件缓存层：由DiskStroageCache实现，提供实际的缓存功能。
- 文件存储层：由DefaultDiskStorage实现，提供磁盘文件读写的功能。

我们先来看看Fresco的缓存键值的设计，Fresco为缓存键设计了一个接口，如下所示：

```java
public interface CacheKey {
  String toString();
  boolean equals(Object o);
  int hashCode();
  //是否是由Uri构建而来的
  boolean containsUri(Uri uri);
  //获取url string
  String getUriString();
}
```

CacheKey有两个实现类：

- BitmapMemoryCacheKey 用于已解码的内存缓存键，会对Uri字符串、缩放尺寸、解码参数、PostProcessor等关键参数进行hashCode作为唯一标识；
- SimpleCacheKey 普通的缓存键实现，使用传入字符串的hashCode作为唯一标识，所以需要保证相同键传入字符串相同。

好，我们继续来分析内存缓存和磁盘缓存的实现。

### 3.1 内存缓存

我们前面说到，内存缓存分为两级：

- 未解码图片内存缓存：由EncodedImage描述真正的缓存对象。
- 已解码图片内存缓存：由BitmapMemoryCache描述真正的缓存对象。

它们的区别在于缓存的数据格式不同，未编码图片内存缓存使用的是CloseableReference<CloseableBitmap>，已编码图片内存缓存使用的是CloseableReference<CloseableBitmap>，它们的区别在于资源
的测量和释放方式是不同，它们使用VauleDescriptor来描述不同资源的数据大小，使用不同的ResourceReleaser来释放资源。

内部的数据结构使用的是CountingLruMap，我们之前在文章[07Android开源框架源码赏析：LruCache与DiskLruCache](https://github.com/guoxiaoxing/android-open-framwork-analysis/blob/master/doc/源码分析/07Android开源框架源码赏析：LruCache与DiskLruCache.md)中
提到，LruCache与DiskLruCache都使用的是LinkedHashMap，Fresco没有直接使用LinkedHashMap，而是对它做了一层封装，这个就是CountingLruMap，它内部有一个双向链表，在查找的时候，可以从最
早插入的单位开始查询，这样就可以快速删除掉最早插入的数据，提高效率。

我们接着来看内存缓存是如何实现的，内存缓存的实现源于一个共同的接口，如下所示：

```java
public interface MemoryCache<K, V> {
  //缓存指定的key-value对，该方法会返回一份新的缓存拷贝用来代码原有的引用，但不需要的时候需要关闭这个缓存引用
  CloseableReference<V> cache(K key, CloseableReference<V> value);
  //获取缓存
  CloseableReference<V> get(K key);
  //根据指定的key移除缓存
  public int removeAll(Predicate<K> predicate);
  //查询是否包含该key对应的缓存
  public boolean contains(Predicate<K> predicate);
}
```
和内存缓存相关的还有一个接口MemoryTrimmable，实现该接口，并将自己注册的MemoryTrimmableRegistry中，当内存变化时，可以
通知到自己，如下所示:

```java
public interface MemoryTrimmable {
  //内存发生变化
  void trim(MemoryTrimType trimType);
}

```

我们来看看有哪些类直接或者间接实现了该缓存接口。

- CountingMemoryCache。它实现了MemoryCache与MemoryTrimmable接口，内部维护这一个Entry用来封装缓存对象，Entry对象除了记录缓存键、缓存值之外，还记录着
该对象的引用数量（clientCount），以及是否被缓存追踪（isOrphan）。
- InstrumentedMemoryCache：也实现了MemoryCache接口，但它没有直接实现相应的功能，它相当于是个Wrapper类，对CountingMemoryCache进行了包装。增加了MemoryCacheTracker
，在缓存未命中时提供回调函数，供调用者实现自定义功能。

在CountingMemoryCache内部使用Entry对象来描述缓存对，它包含以下信息：

```java
  static class Entry<K, V> {
    //缓存key
    public final K key;
    //缓存对象
    public final CloseableReference<V> valueRef;
    // The number of clients that reference the value.
    //缓存的引用计数
    public int clientCount;
    //该Entry对象是否被其所描述的缓存所追踪
    public boolean isOrphan;
    //缓存状态监听器
    @Nullable public final EntryStateObserver<K> observer;
}
```

👉 注：只有引用数量（clientCount）为0，且没有被缓存追踪（isOrphan = true）时缓存对象才可以被释放。

我们接着开看看CountingMemoryCache是如何插入、获取和删除缓存的。

#### 插入缓存

首先我们要了解缓存的操作涉及到两个集合：

```java
  //待移除缓存集合，这里面的缓存没有被外面使用
  @VisibleForTesting
  final CountingLruMap<K, Entry<K, V>> mExclusiveEntries;

  //所有缓存的集合，包括待移除的缓存
  @GuardedBy("this")
  @VisibleForTesting
  final CountingLruMap<K, Entry<K, V>> mCachedEntries;
```

我们接着来看插入缓存的实现。

```java
public class CountingMemoryCache<K, V> implements MemoryCache<K, V>, MemoryTrimmable {
    
      public CloseableReference<V> cache(
          final K key,
          final CloseableReference<V> valueRef,
          final EntryStateObserver<K> observer) {
        Preconditions.checkNotNull(key);
        Preconditions.checkNotNull(valueRef);
    
        //1. 检查是否需要更新缓存参数。
        maybeUpdateCacheParams();
    
        Entry<K, V> oldExclusive;
        CloseableReference<V> oldRefToClose = null;
        CloseableReference<V> clientRef = null;
        synchronized (this) {
          //2. 在缓存中查找要插入的对象，若存在则将其从待移除缓存集合移除，并调用它的close()方法
          //当该缓存对象的引用数目为0的时候会释放掉该对象。
          oldExclusive = mExclusiveEntries.remove(key);
          Entry<K, V> oldEntry = mCachedEntries.remove(key);
          if (oldEntry != null) {
            makeOrphan(oldEntry);
            oldRefToClose = referenceToClose(oldEntry);
          }
          //3. 检查是否缓存对象达到最大显示或者缓存池已满，如果都为否，则插入新缓存对象。
          if (canCacheNewValue(valueRef.get())) {
            Entry<K, V> newEntry = Entry.of(key, valueRef, observer);
            mCachedEntries.put(key, newEntry);
            //4. 将插入的对象包装成一个CloseableReference，重新包装对象主要是为了重设
            //一下ResourceReleaser，它会在释放资源的时候减少Entry的clientCount，并将该缓存对象
            // 加入到mExclusiveEntries中，mExclusiveEntries里存放的是已经被使用过的缓存（等待被释放），
            // 如果缓存对象可以释放，则直接释放缓存对象。
            clientRef = newClientReference(newEntry);
          }
        }
        CloseableReference.closeSafely(oldRefToClose);
        maybeNotifyExclusiveEntryRemoval(oldExclusive);
    
        //5. 判断是否需要释放资源，当超过了EvictEntries最大容量或者缓存池已满，则移除EvictEntries最早插入的对象。
        maybeEvictEntries();
        return clientRef;
      }
}
```
插入缓存主要做了以下几件事情：

1. 检查是否需要更新缓存参数。
2. 在缓存中查找要插入的对象，若存在则将其从待移除缓存集合移除，并调用它的close()方法当该缓存对象的引用数目为0的时候会释放掉该对象。
3. 检查是否缓存对象达到最大显示或者缓存池已满，如果都为否，则插入新缓存对象。
4. 将插入的对象包装成一个CloseableReference，重新包装对象主要是为了重设一下ResourceRelr，它会在释放资源的时候减少Entry的clientCount，并将该缓存对象
加入到mExclusiveEntries中，mExclusiveEntries里存放的是已经被使用过的缓存（等待被释放），如果缓存对象可以释放，则直接释放缓存对象。
5. 判断是否需要释放资源，当超过了EvictEntries最大容量或者缓存池已满，则移除EvictEntries最早插入的对象。

#### 获取缓存

```java
public class CountingMemoryCache<K, V> implements MemoryCache<K, V>, MemoryTrimmable {
    
      @Nullable
      public CloseableReference<V> get(final K key) {
        Preconditions.checkNotNull(key);
        Entry<K, V> oldExclusive;
        CloseableReference<V> clientRef = null;
        synchronized (this) {
          //1. 查询该缓存，说明该缓存可能要被使用，则尝试将其从待移除缓存集合移除。
          oldExclusive = mExclusiveEntries.remove(key);
          //2. 从缓存集合中查询该缓存。
          Entry<K, V> entry = mCachedEntries.get(key);
          if (entry != null) {
            //3. 如果查询到该缓存，将该缓存对象包装成一个CloseableReference，重新包装对象主要是为了重设
           //一下ResourceReleaser，它会在释放资源的时候减少Entry的clientCount，并将该缓存对象
            // 加入到mExclusiveEntries中，mExclusiveEntries里存放的是已经被使用过的缓存（等待被释放），
            // 如果缓存对象可以释放，则直接释放缓存对象。
            clientRef = newClientReference(entry);
          }
        }
       //4. 判断是否需要通知待删除集合里的元素被移除了。
        maybeNotifyExclusiveEntryRemoval(oldExclusive);
        //5. 判断是否需要更新缓存参数。
        maybeUpdateCacheParams();
        //6. 判断是否需要释放资源，当超过了EvictEntries最大容量或者缓存池已满，则移除EvictEntries最早插入的对象。
        maybeEvictEntries();
        return clientRef;
      }

}
```

获取缓存主要执行了以下操作：

1. 查询该缓存，说明该缓存可能要被使用，则尝试将其从待移除缓存集合移除。
2. 从缓存集合中查询该缓存。
3. 如果查询到该缓存，将该缓存对象包装成一个CloseableReference，重新包装对象主要是为了重设一下ResourceReleaser，它会在释放资源的时候减少Entry的clientCount，并将该缓存对象
加入到mExclusiveEntries中，mExclusiveEntries里存放的是已经被使用过的缓存（等待被释放），如果缓存对象可以释放，则直接释放缓存对象。
. 判断是否需要通知待删除集合里的元素被移除了。
5. 判断是否需要更新缓存参数。
6. 判断是否需要释放资源，当超过了EvictEntries最大容量或者缓存池已满，则移除EvictEntries最早插入的对象。

#### 移除缓存

移除缓存就是调用集合的removeAll()方法移除所有的元素，如下所示：

```java
public class CountingMemoryCache<K, V> implements MemoryCache<K, V>, MemoryTrimmable {
    
      public int removeAll(Predicate<K> predicate) {
        ArrayList<Entry<K, V>> oldExclusives;
        ArrayList<Entry<K, V>> oldEntries;
        synchronized (this) {
          oldExclusives = mExclusiveEntries.removeAll(predicate);
          oldEntries = mCachedEntries.removeAll(predicate);
          makeOrphans(oldEntries);
        }
        maybeClose(oldEntries);
        maybeNotifyExclusiveEntryRemoval(oldExclusives);
        maybeUpdateCacheParams();
        maybeEvictEntries();
        return oldEntries.size();
      }
}
```
这个方法比较简单，我们重点关注的是一个多次出现的方法：maybeEvictEntries()，它是用来调节总缓存的大小的，保证缓存不超过最大缓存个数和最大容量，如下所示：

```java
public class CountingMemoryCache<K, V> implements MemoryCache<K, V>, MemoryTrimmable {
    
      private void maybeEvictEntries() {
        ArrayList<Entry<K, V>> oldEntries;
        synchronized (this) {
          int maxCount = Math.min(
              //待移除集合最大持有的缓存个数
              mMemoryCacheParams.maxEvictionQueueEntries,
              //缓存集合最大持有的缓存个数 - 当前正在使用的缓存个数
              mMemoryCacheParams.maxCacheEntries - getInUseCount());
          int maxSize = Math.min(
              //待移除集合最大持有的缓存容量
              mMemoryCacheParams.maxEvictionQueueSize,
              //缓存集合最大持有的缓存容量 - 当前正在使用的缓存容量
              mMemoryCacheParams.maxCacheSize - getInUseSizeInBytes());
          //1. 根据maxCount和maxSize，不断的从mExclusiveEntries移除队头的元素，知道满足缓存限制规则。
          oldEntries = trimExclusivelyOwnedEntries(maxCount, maxSize);
          //2. 将缓存Entry的isOrphan置为true，表示该Entry对象不再被追踪，等待被删除。
          makeOrphans(oldEntries);
        }
        //3. 关闭缓存。
        maybeClose(oldEntries);
        //4. 通知缓存被关闭。
        maybeNotifyExclusiveEntryRemoval(oldEntries);
      }

}
```
整个调整容量的流程就是根据当前缓存的个数和容量进行调整直到满足最大缓存个数和最大缓存容量的限制，如下所示：

1. 根据maxCount和maxSize，不断的从mExclusiveEntries移除队头的元素，知道满足缓存限制规则。
2. 将缓存Entry的isOrphan置为true，表示该Entry对象不再被追踪，等待被删除。
3. 关闭缓存。
4. 通知缓存被关闭。

以上就是内存缓存的全部内容，我们接着来看磁盘缓存的实现。👇

### 3.2 磁盘缓存

我们前面已经说过，磁盘缓存也分为三层，我们再来回顾一下，如下图所示：

👉 点击图片查看大图

<img src="https://github.com/guoxiaoxing/android-open-framwork-analysis/raw/master/art/fresco/three_level_cache_structure.png" width="600"/>

磁盘缓存因为涉及到文件读写要比内存缓存复杂一些，从下至上可以将磁盘缓存分为三层：

- 缓冲缓存层：由BufferedDiskCache实现，提供缓冲功能。
- 文件缓存层：由DiskStroageCache实现，提供实际的缓存功能。
- 文件存储层：由DefaultDiskStorage实现，提供磁盘文件读写的功能。

我们来看看相关的接口。

磁盘缓存的接口是FileCache，如下所示：

```java
public interface FileCache extends DiskTrimmable {
  //是否可以进行磁盘缓存，主要是本地存储是否存在以及是否可以读写。
  boolean isEnabled();
  //返回缓存的二进制资源
  BinaryResource getResource(CacheKey key);
  //是否包含该缓存key，异步调用。
  boolean hasKeySync(CacheKey key);
  //是否包含该缓存key，同步调用。
  boolean hasKey(CacheKey key);
  boolean probe(CacheKey key);
  //插入缓存
  BinaryResource insert(CacheKey key, WriterCallback writer) throws IOException;
  //移除缓存
  void remove(CacheKey key);
  //获取缓存总大小
  long getSize();
   //获取缓存个数
  long getCount();
  //清除过期的缓存
  long clearOldEntries(long cacheExpirationMs);
  //清除所有缓存
  void clearAll();
  //获取磁盘dump信息
  DiskStorage.DiskDumpInfo getDumpInfo() throws IOException;
}
```
可以发现FileCahce接口继承于DisTrimmable，它是一个用来监听磁盘容量变化的接口，如下所示：

```java
public interface DiskTrimmable {
  //当磁盘只有很少的空间可以使用的时候回调。
  void trimToMinimum();
  //当磁盘没有空间可以使用的时候回调
  void trimToNothing();
}

```

除了缓存接口DiskStorageCache，Fresco还定义了DiskStorage接口来封装文件IO的读写逻辑，如下所示：

```java
public interface DiskStorage {

  class DiskDumpInfoEntry {
    public final String path;
    public final String type;
    public final float size;
    public final String firstBits;
    protected DiskDumpInfoEntry(String path, String type, float size, String firstBits) {
      this.path = path;
      this.type = type;
      this.size = size;
      this.firstBits = firstBits;
    }
  }

  class DiskDumpInfo {
    public List<DiskDumpInfoEntry> entries;
    public Map<String, Integer> typeCounts;
    public DiskDumpInfo() {
      entries = new ArrayList<>();
      typeCounts = new HashMap<>();
    }
  }

  //文件存储是否可用
  boolean isEnabled();
  //是否包含外部存储
  boolean isExternal();
  //获取文件描述符指向的文件
  BinaryResource getResource(String resourceId, Object debugInfo) throws IOException;
  //检查是否包含文件描述符所指的文件
  boolean contains(String resourceId, Object debugInfo) throws IOException;
  //检查resourceId对应的文件是否存在，如果存在则更新上次读取的时间戳。
  boolean touch(String resourceId, Object debugInfo) throws IOException;
  void purgeUnexpectedResources();
  //插入
  Inserter insert(String resourceId, Object debugInfo) throws IOException;
  //获取磁盘缓存里所有的Entry。
  Collection<Entry> getEntries() throws IOException;
  //移除指定的缓存Entry。
  long remove(Entry entry) throws IOException;
  //根据resourceId移除对应的磁盘缓存文件
  long remove(String resourceId) throws IOException;
  //清除所有缓存文件
  void clearAll() throws IOException;

  DiskDumpInfo getDumpInfo() throws IOException;

  //获取存储名
  String getStorageName();

  interface Entry {
    //ID
    String getId();
    //时间戳
    long getTimestamp();
    //大小
    long getSize();
    //Fresco使用BinaryResource对象来描述磁盘缓存对象，通过该对象可以获取文件的输入流、字节码等信息。
    BinaryResource getResource();
  }

  interface Inserter {
    //写入数据
    void writeData(WriterCallback callback, Object debugInfo) throws IOException;
    //提交写入的数据
    BinaryResource commit(Object debugInfo) throws IOException;
    //取消此次插入操作
    boolean cleanUp();
  }
}
```

理解了主要接口的功能我们就看看看主要的实现类：

- DiskStroageCache：实现了FileCache接口与DiskTrimmable接口是缓存的主要实现类。
- DefaultDiskStorage：实现了DiskStorage接口，封装了磁盘IO的读写逻辑。
- BufferedDiskCache：在DiskStroageCache的基础上提供了Buffer功能。

BufferedDiskCache主要提供了三个方面的功能：

- 提供写入缓冲StagingArea，所有要写入的数据在发出写入命令到最终写入之前会存储在这里，在查找缓存的时候会首先在这块区域内查找，若命中则直接返回；
- 提供了写入数据的办法，在writeToDiskCache中可以看出它提供的WriterCallback将要写入的EncodedImage转码成输入流；
- 将get、put两个方法放在后台线程中运行（get时在缓冲区域查找时除外），分别都是容量为2的线程池。

我们来看看它们的实现细节。

上面DiskStorage里定义了个接口Entry来描述磁盘缓存对象的信息，真正持有缓存对象的是BinaryResource接口，它的实现类是FileBinaryResource，该类主要定义了
File的一些操作，可以通过它获取文件的输入流和字节码等。

此外，Fresco定义了每个文件的唯一描述符，此描述符由CacheKey的toString()方法导出字符串的SHA-1哈希码，然后该哈希码再经过Base64加密得出。

我们来看看磁盘缓存的插入、查找和删除的实现。

#### 插入缓存

```java
public class DiskStorageCache implements FileCache, DiskTrimmable {
    
   @Override
     public BinaryResource insert(CacheKey key, WriterCallback callback) throws IOException {
       //1. 先将磁盘缓存写入到缓存文件，这可以提供写缓存的并发速度。
       SettableCacheEvent cacheEvent = SettableCacheEvent.obtain()
           .setCacheKey(key);
       mCacheEventListener.onWriteAttempt(cacheEvent);
       String resourceId;
       synchronized (mLock) {
         //2. 获取缓存的resoucesId。
         resourceId = CacheKeyUtil.getFirstResourceId(key);
       }
       cacheEvent.setResourceId(resourceId);
       try {
         //3. 创建要插入的文件（同步操作），这里构建了Inserter对象，该对象封装了具体的写入流程。
         DiskStorage.Inserter inserter = startInsert(resourceId, key);
         try {
           inserter.writeData(callback, key);
           //4. 提交新创建的缓存文件到缓存中。
           BinaryResource resource = endInsert(inserter, key, resourceId);
           cacheEvent.setItemSize(resource.size())
               .setCacheSize(mCacheStats.getSize());
           mCacheEventListener.onWriteSuccess(cacheEvent);
           return resource;
         } finally {
           if (!inserter.cleanUp()) {
             FLog.e(TAG, "Failed to delete temp file");
           }
         }
       } catch (IOException ioe) {
         //... 异常处理
       } finally {
         cacheEvent.recycle();
       }
     } 
}
```

整个插入缓存的流程如下所示：

1. 先将磁盘缓存写入到缓存文件，这可以提供写缓存的并发速度。
2. 获取缓存的resoucesId。
3. 创建要插入的文件（同步操作），这里构建了Inserter对象，该对象封装了具体的写入流程。
4. 提交新创建的缓存文件到缓存中。

我们重点来看看这两个方法startInsert()与endInsert()。

```java
public class DiskStorageCache implements FileCache, DiskTrimmable {
    
      //创建一个临时文件，后缀为.tmp
      private DiskStorage.Inserter startInsert(
          final String resourceId,
          final CacheKey key)
          throws IOException {
        maybeEvictFilesInCacheDir();
        //调用DefaultDiskStorage的insert()方法创建一个临时文件
        return mStorage.insert(resourceId, key);
      }

      //将缓存文件提交到缓存中，如何缓存文件已经存在则尝试删除原来的文件
      private BinaryResource endInsert(
          final DiskStorage.Inserter inserter,
          final CacheKey key,
          String resourceId) throws IOException {
        synchronized (mLock) {
          BinaryResource resource = inserter.commit(key);
          //将resourceId添加点resourceId集合中，DiskStorageCache里只维护了这一个集合
          //来记录缓存
          mResourceIndex.add(resourceId);
          mCacheStats.increment(resource.size(), 1);
          return resource;
        }
      }
}
```


DiskStorageCache里只维护了这一个集合Set<String> mResourceIndex来记录缓存的Resource ID，而DefaultDiskStorage负责对磁盘上
的缓存就行管理，体为DiskStorageCache提供索引功能。

我们接着来看看查找缓存的实现。

#### 查找缓存

根据CacheKey查找缓存BinaryResource，如果缓存以及存在，则更新它的LRU访问时间戳，如果缓存不存在，则返回空。

```java
public class DiskStorageCache implements FileCache, DiskTrimmable {
    
     @Override
     public BinaryResource getResource(final CacheKey key) {
       String resourceId = null;
       SettableCacheEvent cacheEvent = SettableCacheEvent.obtain()
           .setCacheKey(key);
       try {
         synchronized (mLock) {
           BinaryResource resource = null;
           //1. 获取缓存的ResourceId，这里是一个列表，因为可能存在MultiCacheKey，它wrap多个CacheKey。
           List<String> resourceIds = CacheKeyUtil.getResourceIds(key);
           for (int i = 0; i < resourceIds.size(); i++) {
             resourceId = resourceIds.get(i);
             cacheEvent.setResourceId(resourceId);
             //2. 获取ResourceId对应的BinaryResource。
             resource = mStorage.getResource(resourceId, key);
             if (resource != null) {
               break;
             }
           }
           if (resource == null) {
             //3. 缓存没有命中，则执行onMiss()回调，并将resourceId从mResourceIndex移除。
             mCacheEventListener.onMiss(cacheEvent);
             mResourceIndex.remove(resourceId);
           } else {
             //4. 缓存命中，则执行onHit()回调，并将resourceId添加到mResourceIndex。
             mCacheEventListener.onHit(cacheEvent);
             mResourceIndex.add(resourceId);
           }
           return resource;
         }
       } catch (IOException ioe) {
         //... 异常处理
         return null;
       } finally {
         cacheEvent.recycle();
       }
     } 
}
```

整个查找的流程如下所示：

1. 获取缓存的ResourceId，这里是一个列表，因为可能存在MultiCacheKey，它wrap多个CacheKey。
2. 获取ResourceId对应的BinaryResource。
3. 缓存没有命中，则执行onMiss()回调，并将resourceId从mResourceIndex移除。
4. 缓存命中，则执行onHit()回调，并将resourceId添加到mResourceIndex。mCacheEventListener.onHit(cacheEvent);

这里会调用DefaultDiskStorage的getReSource()方法去查询缓存文件的路径并构建一个BinaryResource对象。

Fresco在本地保存缓存文件的路径如下所示：

```
parentPath + File.separator + resourceId + type;
```

parentPath是根目录，type分为两种：

- private static final String CONTENT_FILE_EXTENSION = ".cnt";
- private static final String TEMP_FILE_EXTENSION = ".tmp";

以上就是查询缓存的逻辑，我们接着来看看删除缓存的逻辑。
             
#### 删除缓存

```java
public class DiskStorageCache implements FileCache, DiskTrimmable {
    
      @Override
      public void remove(CacheKey key) {
        synchronized (mLock) {
          try {
            String resourceId = null;
            //获取Resoucesid，根据resouceId移除缓存，并将自己从mResourceIndex移除。
            List<String> resourceIds = CacheKeyUtil.getResourceIds(key);
            for (int i = 0; i < resourceIds.size(); i++) {
              resourceId = resourceIds.get(i);
              mStorage.remove(resourceId);
              mResourceIndex.remove(resourceId);
            }
          } catch (IOException e) {
             //...移除处理
          }
        }
      }
}
```
删除缓存的逻辑也很简单，获取Resoucesid，根据resouceId移除缓存，并将自己从mResourceIndex移除。

磁盘缓存也会自己调节自己的缓存大小来满足缓存最大容量限制条件，我们也来简单看一看。

Fresco里的磁盘缓存过载时，会以不超过缓存容量的90%为目标进行清理，具体清理流程如下所示：

```java
public class DiskStorageCache implements FileCache, DiskTrimmable {
    
      @GuardedBy("mLock")
      private void evictAboveSize(
          long desiredSize,
          CacheEventListener.EvictionReason reason) throws IOException {
        Collection<DiskStorage.Entry> entries;
        try {
          //1. 获取缓存目录下所有文件的Entry的集合，以最近被访问的时间为序，最近被访问的Entry放在后面。
          entries = getSortedEntries(mStorage.getEntries());
        } catch (IOException ioe) {
          //... 捕获异常
        }
    
        //要删除的数据量
        long cacheSizeBeforeClearance = mCacheStats.getSize();
        long deleteSize = cacheSizeBeforeClearance - desiredSize;
        //记录删除数据数量
        int itemCount = 0;
        //记录删除数据大小
        long sumItemSizes = 0L;
        //2. 循环遍历，从头部开始删除元素，直到剩余容量达到desiredSize位置。
        for (DiskStorage.Entry entry: entries) {
          if (sumItemSizes > (deleteSize)) {
            break;
          }
          long deletedSize = mStorage.remove(entry);
          mResourceIndex.remove(entry.getId());
          if (deletedSize > 0) {
            itemCount++;
            sumItemSizes += deletedSize;
            SettableCacheEvent cacheEvent = SettableCacheEvent.obtain()
                .setResourceId(entry.getId())
                .setEvictionReason(reason)
                .setItemSize(deletedSize)
                .setCacheSize(cacheSizeBeforeClearance - sumItemSizes)
                .setCacheLimit(desiredSize);
            mCacheEventListener.onEviction(cacheEvent);
            cacheEvent.recycle();
          }
        }
        //3. 更新容量，删除不需要的临时文件。
        mCacheStats.increment(-sumItemSizes, -itemCount);
        mStorage.purgeUnexpectedResources();
      }
}
```
整个清理流程可以分为以下几步：

1. 获取缓存目录下所有文件的Entry的集合，以最近被访问的时间为序，最近被访问的Entry放在后面。
2. 循环遍历，从头部开始删除元素，直到剩余容量达到desiredSize位置。
3. 更新容量，删除不需要的临时文件。

关于Fresco的源码分析就到这里了，本来还想再讲一讲Fresco内存管理方面的知识，但是这牵扯到Java Heap以及Android匿名共享内存方面的知识，相对比较深入，所以
等着后续分析《Android内存管理框架》的时候结合着一块讲。

