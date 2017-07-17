# Okhttp源码篇：WebSocket协议实现

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

## WebSocket协议

要理解Okhttp中关于WebSocket协议的实现，就需要大致先理解WebSocket协议。

WebSocket协议官方文档：https://tools.ietf.org/html/rfc6455

WebSocket协议结构图

```
0                   1                   2                   3
0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
+-+-+-+-+-------+-+-------------+-------------------------------+
|F|R|R|R| opcode|M| Payload len |    Extended payload length    |
|I|S|S|S|  (4)  |A|     (7)     |             (16/64)           |
|N|V|V|V|       |S|             |   (if payload len==126/127)   |
| |1|2|3|       |K|             |                               |
+-+-+-+-+-------+-+-------------+ - - - - - - - - - - - - - - - +
|     Extended payload length continued, if payload len == 127  |
+ - - - - - - - - - - - - - - - +-------------------------------+
|                               |Masking-key, if MASK set to 1  |
+-------------------------------+-------------------------------+
| Masking-key (continued)       |          Payload Data         |
+-------------------------------- - - - - - - - - - - - - - - - +
:                     Payload Data continued ...                :
+ - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - +
|                     Payload Data continued ...                |
+---------------------------------------------------------------+
```

- FIN：1位，标识消息是否是最后一帧。1个消息由1个或者多个数据帧组成，若消息由1帧构成，则起始帧就是结束帧。
- RSV1，RSV2，RSV3：1位，预留位，用于自定义扩展。如果没有扩展，则各位为0；如果定义扩展，即各位非0，但扩展中没有该值的定义，则关闭连接。
- opcode：4位，标识帧类型，帧类型微分控制帧与非控制帧，如果接收到未知帧，接收端就必须关闭连接。
- MASK：1位，掩码位，标识帧里的数据是否经过加密，如果帧经过掩码加密处理，该位为1，Masking-key帧的数据就是掩码密钥，用于解码Payload。
WebSocket协议规定

已定义的帧类型：

- %x0 denotes a continuation frame
- %x1 denotes a text frame
- %x2 denotes a binary frame
- %x3-7 are reserved for further non-control frames
- %x8 denotes a connection close
- %x9 denotes a ping
- %xA denotes a pong
- %xB-F are reserved for further control frames

WebSocket协议的控制帧有3种：

- 关闭帧：用于关闭连接，客户端可以发送关闭帧给服务端，服务端也可以发送关闭帧给客户端。
- Ping/Pong帧：用于心跳检测，服务端向客户端发送Ping帧，客户端回复Pong帧，表示连接还存在，可以继续通信。