# Android开源框架源码鉴赏：EventBus

作者：[郭孝星](https://github.com/guoxiaoxing)

校对：[郭孝星](https://github.com/guoxiaoxing)

**关于项目**

> [Android Open Framework analysis](https://github.com/guoxiaoxing/android-open-framework-analysis)项目主要用来分析Android平台主流开源框架的源码与原理实现。

**文章目录**

- 一 注册订阅者
- 二 发布事件Event
- 三 接收事件Event
- 四 取消注册订阅者

EventBus是一个Android/Java平台基于订阅与发布的通信框架，可以用于Activities, Fragments, Threads, Services等组件的通信，也可以用于多线程通信。

EventBus在应用里的应用是十分广泛的，那么除了EventBus这种应用通信方式外，还有哪些手段呢？🤔

- BroadcastReceiver/LocalBroadcastReceiver：跨域广播和局域广播，跨域广播可以用来做跨进程通信。局域广播也是基于Handler实现，可以用来在应用内通信。
- Handler：这个方式的弊端在于通信消息难以管理。
- 接口回调：接口回调的好处是比较清晰明显，但是如果涉及到大量页面的跳转或者通信场景比较复杂，这种方式就变得难以维护，耦合较高。

相当于这些方式EventBus的优点在于使用简单，事件的订阅者和发布者解耦，但是它也有有自己的问题，例如大量Event类的管理，这个我们后续会说。

> Event bus for Android and Java that simplifies communication between Activities, Fragments, Threads, Services, etc. Less code, better quality.

- 官方网站：https://github.com/greenrobot/EventBus
- 源码版本：3.1.1

我们先来看一下EventBus的源码结构，如下所示：

<img src="https://github.com/guoxiaoxing/android-open-framwork-analysis/raw/master/art/eventbus/event_bus_source_code.png"/>

主要包含了两个部分：

- eventbus：核心库。
- eventbus-annotation-processor：注解处理部分。

我们先来一个简单的Demo，从Demo入手分析事件的订阅和发布流程。

```java
public class MainActivity extends AppCompatActivity implements View.OnClickListener{

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        findViewById(R.id.btn_post_event).setOnClickListener(this);
    }

    @Override
    protected void onStart() {
        super.onStart();
        // 订阅事件
        EventBus.getDefault().register(this);
    }

    @Override
    protected void onStop() {
        super.onStop();
        // 取消订阅s事件
        EventBus.getDefault().unregister(this);
    }

    // 接收事件Event
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(Event event) {
        Toast.makeText(this, event.getMessage(), Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()){
            case R.id.btn_post_event:
                // 发布事件Event
                EventBus.getDefault().post(new Event("Event Message"));
                break;
        }
    }
}
```

整体的流程还是比较简单的，如下所示：

<img src="https://github.com/guoxiaoxing/android-open-framwork-analysis/raw/master/art/eventbus/event_bus_structure.png"/>

1. 注册订阅者。
2. 发布事件Event。
3. 接收事件Event。
4. 取消注册订阅者。

我们具体来看一下。

## 一 注册订阅者

订阅事件是通过以下方法来完成的：

```java
EventBus.getDefault().register(this);
```
getDefault()用来获取EventBus实例，当然你也可以通过EventBusBuilder自己构建实例。

```java
public class EventBus {
    
    public void register(Object subscriber) {
        // 1. 获取订阅者的类名。
        Class<?> subscriberClass = subscriber.getClass();
        // 2. 查找当前订阅者的所有响应函数。
        List<SubscriberMethod> subscriberMethods = subscriberMethodFinder.findSubscriberMethods(subscriberClass);
        synchronized (this) {
            // 3. 循环每个事件响应函数
            for (SubscriberMethod subscriberMethod : subscriberMethods) {
                subscribe(subscriber, subscriberMethod);
            }
        }
    }
 
}
```
> SubscriberMethod用来描述onEvent()这些方法的信息，包含方法名、线程、Class类型、优先级、是否是粘性事件。

整个函数的调用流程所示：

1. 获取订阅者的类名。
2. 查找当前订阅者的所有响应函数。
3. 循环每个事件响应函数

接着调用subscribe()进行事件注册，如下所示：

```java
public class EventBus {
     
     // 订阅者队列
     private final Map<Class<?>, CopyOnWriteArrayList<Subscription>> subscriptionsByEventType;
     // 后续准备取消的事件队列
     private final Map<Object, List<Class<?>>> typesBySubscriber;
     // 粘性事件队列
     private final Map<Class<?>, Object> stickyEvents;
    
      private void subscribe(Object subscriber, SubscriberMethod subscriberMethod) {
          // 事件类型（xxxEvent）
          Class<?> eventType = subscriberMethod.eventType;
          Subscription newSubscription = new Subscription(subscriber, subscriberMethod);
          // 1. 获取该事件类型的所有订阅者信息。
          CopyOnWriteArrayList<Subscription> subscriptions = subscriptionsByEventType.get(eventType);
          if (subscriptions == null) {
              subscriptions = new CopyOnWriteArrayList<>();
              subscriptionsByEventType.put(eventType, subscriptions);
          } else {
              if (subscriptions.contains(newSubscription)) {
                  throw new EventBusException("Subscriber " + subscriber.getClass() + " already registered to event "
                          + eventType);
              }
          }
  
          int size = subscriptions.size();
          // 2. 按照事件优先级将其插入订阅者列表中。
          for (int i = 0; i <= size; i++) {
              if (i == size || subscriberMethod.priority > subscriptions.get(i).subscriberMethod.priority) {
                  subscriptions.add(i, newSubscription);
                  break;
              }
          }
  
          // 3. 得到当前订阅者订阅的所有事件队列，存放在typesBySubscriber中，用于后续取消事件订阅。
          List<Class<?>> subscribedEvents = typesBySubscriber.get(subscriber);
          if (subscribedEvents == null) {
              subscribedEvents = new ArrayList<>();
              typesBySubscriber.put(subscriber, subscribedEvents);
          }
          subscribedEvents.add(eventType);
  
          // 4. 是否是粘性事件，如果是粘性事件，则从stickyEvents队列中取出最后一个该类型的事件发送给订阅者。
          if (subscriberMethod.sticky) {
              if (eventInheritance) {
                  // Existing sticky events of all subclasses of eventType have to be considered.
                  // Note: Iterating over all events may be inefficient with lots of sticky events,
                  // thus data structure should be changed to allow a more efficient lookup
                  // (e.g. an additional map storing sub classes of super classes: Class -> List<Class>).
                  Set<Map.Entry<Class<?>, Object>> entries = stickyEvents.entrySet();
                  for (Map.Entry<Class<?>, Object> entry : entries) {
                      Class<?> candidateEventType = entry.getKey();
                      if (eventType.isAssignableFrom(candidateEventType)) {
                          Object stickyEvent = entry.getValue();
                          checkPostStickyEventToSubscription(newSubscription, stickyEvent);
                      }
                  }
              } else {
                  Object stickyEvent = stickyEvents.get(eventType);
                  checkPostStickyEventToSubscription(newSubscription, stickyEvent);
              }
          }
      }  
}
```

> Subscription包含了订阅者subscriber和订阅函数subscriberMethod两个信息。

该方法的调用流程如下所示：

1. 获取该事件类型的所有订阅者信息。
2. 按照事件优先级将其插入订阅者列表中。
3. 得到当前订阅者订阅的所有事件队列，存放在typesBySubscriber中，用于后续取消事件订阅。
4. 是否是粘性事件，如果是粘性事件，则从stickyEvents队列中取出最后一个该类型的事件发送给订阅者。

## 二 发布事件Event

发送事件Event是通过以下方法完成的，如下所示：

```java
EventBus.getDefault().post(new Event("Event Message"));
```

```java
public class EventBus {
    
    public void post(Object event) {
        // 1. 获取当前线程的PostingThreadState对象，该对象包含事件队列，保存在ThreadLocal中。
        PostingThreadState postingState = currentPostingThreadState.get();
        List<Object> eventQueue = postingState.eventQueue;
        // 2. 将当前事件加入到该线程的事件队列中。
        eventQueue.add(event);

        // 3. 判断事件是否在分发中。如果没有则遍历事件队列进行实际分发。
        if (!postingState.isPosting) {
            postingState.isMainThread = isMainThread();
            postingState.isPosting = true;
            if (postingState.canceled) {
                throw new EventBusException("Internal error. Abort state was not reset");
            }
            try {
                while (!eventQueue.isEmpty()) {
                    // 4. 进行事件分发。
                    postSingleEvent(eventQueue.remove(0), postingState);
                }
            } finally {
                postingState.isPosting = false;
                postingState.isMainThread = false;
            }
        }
    } 
}
```

> PostingThreadState用来描述发送事件的线程的相关状态信息，包含事件队列，是否是主线程、订阅者、事件Event等信息。

1. 获取当前线程的PostingThreadState对象，该对象包含事件队列，保存在ThreadLocal中。
2. 将当前事件加入到该线程的事件队列中。
3. 判断事件是否在分发中。如果没有则遍历事件队列进行实际分发。
4. 进行事件分发。

然后调用postSingleEvent()进行事件分发。

```java
public class EventBus {
    
    private void postSingleEvent(Object event, PostingThreadState postingState) throws Error {
        Class<?> eventClass = event.getClass();
        boolean subscriptionFound = false;
        // 1. 如果事件允许继承，则查找该事件类型的所有父类和接口，依次进行循环。
        if (eventInheritance) {
            
            List<Class<?>> eventTypes = lookupAllEventTypes(eventClass);
            int countTypes = eventTypes.size();
            for (int h = 0; h < countTypes; h++) {
                Class<?> clazz = eventTypes.get(h);
                // 2. 查找该事件的所有订阅者。
                subscriptionFound |= postSingleEventForEventType(event, postingState, clazz);
            }
        } else {
            subscriptionFound = postSingleEventForEventType(event, postingState, eventClass);
        }
        if (!subscriptionFound) {
            if (logNoSubscriberMessages) {
                logger.log(Level.FINE, "No subscribers registered for event " + eventClass);
            }
            if (sendNoSubscriberEvent && eventClass != NoSubscriberEvent.class &&
                    eventClass != SubscriberExceptionEvent.class) {
                post(new NoSubscriberEvent(this, event));
            }
        }
    }
}
```

该方法主要做了以下事情：

1. 如果事件允许继承，则查找该事件类型的所有父类和接口，依次进行循环。
2. 查找该事件的所有订阅者。

然后调用postSingleEventForEventType()方法查询当前事件的所有订阅者，如下所示：

```java
public class EventBus {
    
    private boolean postSingleEventForEventType(Object event, PostingThreadState postingState, Class<?> eventClass) {
        CopyOnWriteArrayList<Subscription> subscriptions;
        synchronized (this) {
            // 1. 获取当前事件的所有订阅者。
            subscriptions = subscriptionsByEventType.get(eventClass);
        }
        if (subscriptions != null && !subscriptions.isEmpty()) {
            // 2. 遍历所有订阅者。
            for (Subscription subscription : subscriptions) {
                postingState.event = event;
                postingState.subscription = subscription;
                boolean aborted = false;
                try {
                    // 3. 根据订阅者所在线程，调用事件响应函数onEvent()。
                    postToSubscription(subscription, event, postingState.isMainThread);
                    aborted = postingState.canceled;
                } finally {
                    postingState.event = null;
                    postingState.subscription = null;
                    postingState.canceled = false;
                }
                if (aborted) {
                    break;
                }
            }
            return true;
        }
        return false;
    }    
}
```

该方法主要做了以下事情：

1. 获取当前事件的所有订阅者。
2. 遍历所有订阅者。
3. 根据订阅者所在线程，调用事件响应函数onEvent()。

调用postToSubscription()方法根据订阅者所在线程，调用事件响应函数onEvent()，这便涉及到接收事件Event的处理了，我们接着来看。

## 三 接收事件Event

```java
public class EventBus {
    
     private void postToSubscription(Subscription subscription, Object event, boolean isMainThread) {
           switch (subscription.subscriberMethod.threadMode) {
               case POSTING:
                   invokeSubscriber(subscription, event);
                   break;
               case MAIN:
                   if (isMainThread) {
                       invokeSubscriber(subscription, event);
                   } else {
                       mainThreadPoster.enqueue(subscription, event);
                   }
                   break;
               case MAIN_ORDERED:
                   if (mainThreadPoster != null) {
                       mainThreadPoster.enqueue(subscription, event);
                   } else {
                       // temporary: technically not correct as poster not decoupled from subscriber
                       invokeSubscriber(subscription, event);
                   }
                   break;
               case BACKGROUND:
                   if (isMainThread) {
                       backgroundPoster.enqueue(subscription, event);
                   } else {
                       invokeSubscriber(subscription, event);
                   }
                   break;
               case ASYNC:
                   asyncPoster.enqueue(subscription, event);
                   break;
               default:
                   throw new IllegalStateException("Unknown thread mode: " + subscription.subscriberMethod.threadMode);
           }
       }    
}
```

```java
@Subscribe(threadMode = ThreadMode.MAIN)
public void onEvent(Event event) {
    Toast.makeText(this, event.getMessage(), Toast.LENGTH_SHORT).show();
}
```
如上所示，onEvent函数上是可以加Subscribe注解了，该注解标明了onEvent()函数在哪个线程执行。主要有以下几个线程：

- PostThread：默认的 ThreadMode，表示在执行 Post 操作的线程直接调用订阅者的事件响应方法，不论该线程是否为主线程（UI 线程）。当该线程为主线程
时，响应方法中不能有耗时操作，否则有卡主线程的风险。适用场景：对于是否在主线程执行无要求，但若 Post 线程为主线程，不能耗时的操作；
- MainThread：在主线程中执行响应方法。如果发布线程就是主线程，则直接调用订阅者的事件响应方法，否则通过主线程的 Handler 发送消息在主线程中处理—
—调用订阅者的事件响应函数。显然，MainThread类的方法也不能有耗时操作，以避免卡主线程。适用场景：必须在主线程执行的操作；
- BackgroundThread：在后台线程中执行响应方法。如果发布线程不是主线程，则直接调用订阅者的事件响应函数，否则启动唯一的后台线程去处理。由于后台线程
是唯一的，当事件超过一个的时候，它们会被放在队列中依次执行，因此该类响应方法虽然没有PostThread类和MainThread类方法对性能敏感，但最好不要有重度耗
时的操作或太频繁的轻度耗时操作，以造成其他操作等待。适用场景：操作轻微耗时且不会过于频繁，即一般的耗时操作都可以放在这里；
- Async：不论发布线程是否为主线程，都使用一个空闲线程来处理。和BackgroundThread不同的是，Async类的所有线程是相互独立的，因此不会出现卡线程的问
题。适用场景：长耗时操作，例如网络访问。

这里我线程执行和EventBus的成员变量对应，它们都实现了Runnable与Poster接口，Poster接口定义了事件排队功能，这些本质上都是个Runnable，放在线程池里执行，如下所示：

private final Poster mainThreadPoster;
private final BackgroundPoster backgroundPoster;
private final AsyncPoster asyncPoster;
private final SubscriberMethodFinder subscriberMethodFinder;
private final ExecutorService executorService;

## 四 取消注册订阅者

取消注册订阅者调用的是以下方法：

```java
EventBus.getDefault().unregister(this);
```
具体如下所示：

```java
public class EventBus {
    
    public synchronized void unregister(Object subscriber) {
        
        // 1. 获取当前订阅者订阅的所有事件类型。
        List<Class<?>> subscribedTypes = typesBySubscriber.get(subscriber);
        if (subscribedTypes != null) {
            // 2. 遍历事件队列，解除事件注册。
            for (Class<?> eventType : subscribedTypes) {
                unsubscribeByEventType(subscriber, eventType);
            }
            // 3. 移除事件订阅者。
            typesBySubscriber.remove(subscriber);
        } else {
            logger.log(Level.WARNING, "Subscriber to unregister was not registered before: " + subscriber.getClass());
        }
    }

}
```

取消注册订阅者的流程也十分简单，如下所示：

1. 获取当前订阅者订阅的所有事件类型。
2. 遍历事件队列，解除事件注册。
3. 移除事件订阅者。

当猴调用unsubscribeByEventType()移除订阅者，如下所示：

```java
public class EventBus {
    
     private void unsubscribeByEventType(Object subscriber, Class<?> eventType) {
         // 1. 获取所有订阅者信息。
         List<Subscription> subscriptions = subscriptionsByEventType.get(eventType);
         if (subscriptions != null) {
             // 2. 遍历订阅者
             int size = subscriptions.size();
             for (int i = 0; i < size; i++) {
                 Subscription subscription = subscriptions.get(i);
                 // 3. 移除该订阅对象。
                 if (subscription.subscriber == subscriber) {
                     subscription.active = false;
                     subscriptions.remove(i);
                     i--;
                     size--;
                 }
             }
         }
     }
}
```

以上便是EventBus核心的实现，还是比较简单的。


