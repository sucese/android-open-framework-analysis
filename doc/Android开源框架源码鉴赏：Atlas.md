# Android开源框架源码鉴赏：Atlas

作者：[郭孝星](https://github.com/guoxiaoxing)

校对：[郭孝星](https://github.com/guoxiaoxing)

**关于项目**

> [Android Open Framework analysis](https://github.com/guoxiaoxing/android-open-framework-analysis)项目主要用来分析Android平台主流开源框架的源码与原理实现。

**文章目录**

Atlas发展历程，如下所示：

- 第一代：2012-2013，插件式，进程隔离，插件独立。
- 第二代：2014-1015，静态组件式，单一进程，组件可独立，中间件复用。
- 第三代：2015-2016，动态组件式，按需加载，支持远程组件，升级。

Atlas特性

Atlas框架主要提供了组件化、动态性、解耦化的支持，支持工程师在工程编译器，APK运行期以及后续运维期快速修复问题，具体
说来，有以下特点：

- 实现完整的组件生命周期的映射，类隔离机制。
- 实现工程独立开发，吊事的功能，工程模块彼此独立。
- 快速增量的更新修复能力，快速升级。


Atlas动态特性如下所示：

- 全类型的动态能力，支持Class文件、SO库和资源的增删改操作。
- 良好的兼容性，适配4.x及其以上版本，在手淘稳定运行两年多。
- 高性能，通过Verify等手段，达到最低的性能损耗。
- 小补丁，通过精细化diff方法，使得补丁包非常小。
- 非常高的部署成功率和修复效率。
- 对开发者透明，与政策的开发功能无差别，自动生成diff包。

0x7f  0x02 0x0002

pakcage type entry


资源分段

--customized-package-id

--use-skt-package-name

Atlas编译期

Atlas运行期