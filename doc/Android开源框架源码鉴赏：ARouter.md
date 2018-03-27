# Android开源框架源码鉴赏：ARouter

作者：[郭孝星](https://github.com/guoxiaoxing)

校对：[郭孝星](https://github.com/guoxiaoxing)

**关于项目**

> [Android Open Framework analysis](https://github.com/guoxiaoxing/android-open-framework-analysis)项目主要用来分析Android平台主流开源框架的源码与原理实现。

**文章目录**e

在介绍ARouter路由方案之前，我们首先来思考一下为什么需要一个路由框架？🤔

- 显示Intent跳转，直接的类依赖，耦合严重。
- 隐式Intent跳转，规则集中式管理，协作困难。
- Manifest扩展性较差。
- 跳转过程无法控制。
- 失败无法降级。

我们接着来看看ARouter是如何解决这些问题。

>An android router middleware that help app navigating to activities and custom services.

官方网站：https://github.com/alibaba/ARouter

源码版本：1.3.1

ARouter具有以下特点：

- 支持直接解析标准URL进行跳转，并自动注入参数到目标页面中
- 支持多模块工程使用
- 支持添加多个拦截器，自定义拦截顺序
- 支持依赖注入，可单独作为依赖注入框架使用
- 支持InstantRun
- 支持MultiDex(Google方案)
- 映射关系按组分类、多级管理，按需初始化
- 支持用户指定全局降级与局部降级策略
- 页面、拦截器、服务等组件均自动注册到框架
- 支持多种方式配置转场动画
- 支持获取Fragment
- 完全支持Kotlin以及混编(配置见文末 其他#5)

ARouter应用场景如下所示：

- 从外部URL映射到内部页面，以及参数传递与解析
- 跨模块页面跳转，模块间解耦
- 拦截跳转过程，处理登陆、埋点等逻辑
- 跨模块API调用，通过控制反转来做组件解耦

理解了ARouter的特点与应用场景，我们再来看看ARouter的架构设计，一般来说，一个完整的路由框架会具备以下功能：

- 分发：吧一个URL或者请求按照一定的规则跳转到某个服务或者某个页面。
- 管理：将组件和页面按照一定的规则管理起来，在分发的时候提供搜索、加载和修改等操作。
- 控制：在路由的过程中，可以做一些定制性的扩展，例如降级等。

ARouter的源码架构如下所示：

<img src="https://github.com/guoxiaoxing/android-open-framwork-analysis/raw/master/art/arouter/arouter_source_code_structure.png"/>

ARouter的框架架构如下所示：

👉 点击图片查看大图

<img src="https://github.com/guoxiaoxing/android-open-framwork-analysis/raw/master/art/arouter/arouter_structure.png"/>

- Launcher：用户API层。
- Service：不同于，把功能和组件封装成接口。
- Callback：回调。
- Template：编译期生成的组件模板。
- Ware House：存储映射文件和配置表。
- Thread：处理跳转和拦截的异步执行。
- Class Tool：处理Instant Run和Mutil Dex里的兼容性问题。
- Route Processor：路由处理器。
- Interceptor Processor：拦截器处理器。
- Autowire Processor：自动装配处理器。

ARouter作为一个App底层框架还具备以下特点：

- 自举性：所有的组件可以自己进行初始化。
- 扩展性：ARouter作为一个App底层框架不会经常去升级，它会提供良好的扩展性来解决不同场景的业务需求。
- 简单够用：ARouter的实现并不复杂，功能上简单够用。

理解了ARouter的大体框架，我们来看一看ARouter是如何实现路由方案的。

## 一 页面注册

ARouter是采用注解的方式标记页面的路径，为了解决性能问题，采用的也是编译期处理注解，页面路径使用Route来标记，如下所示：

```java
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.CLASS)
public @interface Route {

    /**
     * Path of route
     */
    String path();

    /**
     * Used to merger routes, the group name MUST BE USE THE COMMON WORDS !!!
     */
    String group() default "";

    /**
     * Name of route, used to generate javadoc.
     */
    String name() default "undefined";

    /**
     * Extra data, can be set by user.
     * Ps. U should use the integer num sign the switch, by bits. 10001010101010
     */
    int extras() default Integer.MIN_VALUE;

    /**
     * The priority of route.
     */
    int priority() default -1;
}

```

1. 注解处理器扫描出被标注的类文件。
2. 按照不同种类的源文件进行分类。
3. 按照固定的命名格式生成映射文件。
4. 初始化的时候通过固定包名加载映射文件。

## 二 页面加载

ARouter页面结构图如下所示：

<img src="https://github.com/guoxiaoxing/android-open-framwork-analysis/raw/master/art/arouter/page_load_structure.png"/>

按照业务进行模块划分，每个模块又包含以下节点：

- root节点：页面的root节点。
- interceptor节点：拦截器节点，用来做拦截。
- provider节点：provider节点，用来做控制反转。
- group节点：页面按照一定规则进行分组。

第一次加载页面映射关系时，会加载所有模块的root节点、interceptor节点、provider节点，group节点在第一次加载的时候不进行加载，当group中有一个页面被访问的时候才会
去加载group节点。

## 三 拦截器

ARouter拦截器的流程图如下所示：

<img src="https://github.com/guoxiaoxing/android-open-framwork-analysis/raw/master/art/arouter/interceptor_structure.png"/>

## 四 依赖注入

编译期

1. 编译期扫出需要自动装配的字段。
2. 把自动装配的字段注册到映射文件中。
3. 跳转的时候按照预先的配置从URL提取参数，并按照类型放入Intent。

运行期

1. 目标页面在初始化的时候调用ARouter.inject(this)。
2. ARouter会查找到编译器为调用方生成的注入辅助类。
3. 实例化辅助类以后，调用其中的inject()方法完成字段的赋值。


## 五 控制反装

依赖查找

依赖注入