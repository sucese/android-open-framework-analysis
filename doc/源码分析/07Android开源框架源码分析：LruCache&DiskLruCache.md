# Android开源框架源码分析：LruCache & DiskLruCache

**关于作者**

>郭孝星，程序员，吉他手，主要从事Android平台基础架构方面的工作，欢迎交流技术方面的问题，可以去我的[Github](https://github.com/guoxiaoxing)提issue或者发邮件至guoxiaoxingse@163.com与我交流。

**文章目录**

- 一 Lru算法
- 二 LruCache原理分析
    - 2.1 写入缓存
    - 2.2 读取缓存
    - 2.3 删除缓存
- 三 DiskLruCache原理分析
    - 3.1 写入缓存
    - 3.2 读取缓存
    - 3.3 删除缓存

## 一 Lru算法

在分析LruCache与DiskLruCache之前，我们先来简单的了解下LRU算法的核心原理。

LRU算法可以用一句话来描述，如下所示：

>LRU是Least Recently Used的缩写，最近最久未使用算法，从它的名字就可以看出，它的核心原则是如果一个数据在最近一段时间没有使用到，那么它在将来被
访问到的可能性也很小，则这类数据项会被优先淘汰掉。

LRU算法流程图如下所示：

<img src="https://github.com/guoxiaoxing/android-open-framwork-analysis/raw/master/art/lru/lru_structure.png"/>

了解了算法原理，我们来思考一下如果是我们来做，应该如何实现这个算法。从上图可以看出，双向链表是一个好主意。

>假设我们从表尾访问数据，在表头删除数据，当访问的数据项在链表中存在时，则将该数据项移动到表尾，否则在表尾新建一个数据项。当链表容量超过一定阈值，则移除表头的数据。

好，以上便是整个Lru算法的原理，我们接着来分析LruCache与DiskLruCache之的实现。

## 二 LruCache原理分析

理解了Lru算法的原理，我们接着从LruCache的使用入手，逐步分析LruCache的源码实现。

👉 [LruCache.java](https://android.googlesource.com/platform/frameworks/support.git/+/795b97d901e1793dac5c3e67d43c96a758fec388/v4/java/android/support/v4/util/LruCache.java)

在分析LruCache的源码实现之前，我们先来看看LruCache的简单使用，如下所示：

```java
int maxMemorySize = (int) (Runtime.getRuntime().totalMemory() / 1024);
int cacheMemorySize = maxMemorySize / 8;
LruCache<String, Bitmap> lrucache = new LruCache<String, Bitmap>(cacheMemorySize) {

    @Override
    protected int sizeOf(String key, Bitmap value) {
        return getBitmapSize(value);
    }

    @Override
    protected void entryRemoved(boolean evicted, String key, Bitmap oldValue, Bitmap newValue) {
        super.entryRemoved(evicted, key, oldValue, newValue);
    }

    @Override
    protected Bitmap create(String key) {
        return super.create(key);
    }
};
```

注：getBitmapSize()用来计算图片占内存的大小，具体方法参见附录。

可以发现，在使用LruCache的过程中，需要我们关注的主要有三个方法：

- sizeOf()：覆写此方法实现自己的一套定义计算entry大小的规则。
- V create(K key)：如果key对象缓存被移除了，则调用次方法重建缓存。
- entryRemoved(boolean evicted, K key, V oldValue, V newValue) ：当key对应的缓存被删除时回调该方法。

我们来看看这三个方法的默认实现，如下所示：

```java
public class LruCache<K, V> {
    
    //该方法默认返回1，也就是以entry的数量来计算entry的大小，这通常不符合我们的需求，所以我们一般会覆写此方法。
    protected int sizeOf(K key, V value) {
        return 1;
    }
    protected void entryRemoved(boolean evicted, K key, V oldValue, V newValue) {}
    protected V create(K key) {
        return null;
    }
}
```
可以发现entryRemoved()方法为空实现，create()方法也默认返回null。sizeOf()方法默认返回1，也就是以entry的数量来计算entry的大小，这通常不符合我们的需求，所以我们一般会覆写此方法。

我们前面提到，要实现Lru算法，可以利用双向链表。

>假设我们从表尾访问数据，在表头删除数据，当访问的数据项在链表中存在时，则将该数据项移动到表尾，否则在表尾新建一个数据项。当链表容量超过一定阈值，则移除表头的数据。

LruCache使用的是LinkedHashMap，为什么会选择LinkedHashMap呢？🤔

这跟LinkedHashMap的特性有关，LinkedHashMap的构造函数里有个布尔参数accessOrder，当它为true时，LinkedHashMap会以访问顺序为序排列元素，否则以插入顺序为序排序元素。

```java
public class LruCache<K, V> {
   public LinkedHashMap(int initialCapacity,
                        float loadFactor,
                        boolean accessOrder) {
       super(initialCapacity, loadFactor);
       this.accessOrder = accessOrder;
   } 
}
```

我们来写个小例子验证一下。

程序输入Log：

<img src="https://github.com/guoxiaoxing/android-open-framwork-analysis/raw/master/art/lru/lru_structure.png"/>

注：在LinkedHashMap中最近被方位的元素会被移动到表尾，LruCache也是从从表尾访问数据，在表头删除数据，

可以发现，最后访问的数据就会被移动最尾端，这是符合我们的预期的。所有在LruCache的构造方法中构造了一个这样的LinkedHashMap。

```java
public LruCache(int maxSize) {
    if (maxSize <= 0) {
        throw new IllegalArgumentException("maxSize <= 0");
    }
    this.maxSize = maxSize;
    this.map = new LinkedHashMap<K, V>(0, 0.75f, true);
}
```

我们再来看看LruCache是如何进行数据的插入、访问和删除的。

### 2.1 写入缓存


```java
public class LruCache<K, V> {
    
    public final V put(K key, V value) {
        if (key == null || value == null) {
            throw new NullPointerException("key == null || value == null");
        }

        V previous;
        //加锁，线程安全
        synchronized (this) {
            //插入的数量自增
            putCount++;
            //利用我们提供的sizeOf()方法计算当前项的大小，并增加已有缓存size的大小
            size += safeSizeOf(key, value);
            //插入当前项、
            previous = map.put(key, value);
            //previous如果不为空，则说明该项在原来的链表中以及存在，已有缓存大小size恢复到
            //以前的大小
            if (previous != null) {
                size -= safeSizeOf(key, previous);
            }
        }

        //回调entryRemoved()方法
        if (previous != null) {
            entryRemoved(false, key, previous, value);
        }

        //调整缓存大小，如果缓存满了，则按照Lru算法删除对应的项。
        trimToSize(maxSize);
        return previous;
    }
    
    public void trimToSize(int maxSize) {
        //开启死循环，知道缓存不满为止
        while (true) {
            K key;
            V value;
            synchronized (this) {
                //参数检查
                if (size < 0 || (map.isEmpty() && size != 0)) {
                    throw new IllegalStateException(getClass().getName()
                            + ".sizeOf() is reporting inconsistent results!");
                }

                //如果缓存为满，直接返回                
                if (size <= maxSize) {
                    break;
                }

                //返回最近最久未使用的元素，也就是链表的表头元素
                Map.Entry<K, V> toEvict = map.eldest();
                if (toEvict == null) {
                    break;
                }

                key = toEvict.getKey();
                value = toEvict.getValue();
                //删除该表头元素
                map.remove(key);
                //减少总缓存大小
                size -= safeSizeOf(key, value);
                //被删除的项的数量自增
                evictionCount++;
            }
            //回到entryRemoved()方法
            entryRemoved(true, key, value, null);
        }
    }
}
```

整个插入元素的方法put()实现逻辑是很简单的，如下所示：

1. 插入元素，并相应增加当前缓存的容量。
2. 调用trimToSize()开启一个死循环，不断的从表头删除元素，直到当前缓存的容量小于最大容量为止。


### 2.2 读取缓存


```java
public class LruCache<K, V> {
    
    public final V get(K key) {
          if (key == null) {
              throw new NullPointerException("key == null");
          }
  
          V mapValue;
          synchronized (this) {
              //调用LinkedHashMap的get()方法，注意如果该元素存在，且accessOrder为true，这个方法会
              //将该元素移动到表尾
              mapValue = map.get(key);
              if (mapValue != null) {
                  hitCount++;
                  return mapValue;
              }
              //
              missCount++;
          }
            
          //前面我们就提到过，可以覆写create()方法，当获取不到和key对应的元素时，尝试调用create()方法
          //创建建元素，以下就是创建的过程，和put()方法流程相同。
          V createdValue = create(key);
          if (createdValue == null) {
              return null;
          }
  
          synchronized (this) {
              createCount++;
              mapValue = map.put(key, createdValue);
  
              if (mapValue != null) {
                  // There was a conflict so undo that last put
                  map.put(key, mapValue);
              } else {
                  size += safeSizeOf(key, createdValue);
              }
          }
  
          if (mapValue != null) {
              entryRemoved(false, key, createdValue, mapValue);
              return mapValue;
          } else {
              trimToSize(maxSize);
              return createdValue;
          }
      }
}
```

获取元素的逻辑如下所示：

1. 调用LinkedHashMap的get()方法，注意如果该元素存在，且accessOrder为true，这个方法会将该元素移动到表尾.
2. 当获取不到和key对应的元素时，尝试调用create()方法创建建元素，以下就是创建的过程，和put()方法流程相同。

### 2.3 删除缓存

```java
public class LruCache<K, V> {
    
    public final V remove(K key) {
        if (key == null) {
            throw new NullPointerException("key == null");
        }

        V previous;
        synchronized (this) {
            //调用对应LinkedHashMap的remove()方法删除对应元素
            previous = map.remove(key);
            if (previous != null) {
                size -= safeSizeOf(key, previous);
            }
        }

        if (previous != null) {
            entryRemoved(false, key, previous, null);
        }

        return previous;
    }

}    
```    

删除元素的逻辑就比较简单了，调用对应LinkedHashMap的remove()方法删除对应元素。

## 三 DiskLruCache原理分析

👉 [DiskLruCache.java](https://android.googlesource.com/platform/libcore/+/android-4.3_r3/luni/src/main/java/libcore/io/DiskLruCache.java)

在分析DiskLruCache的实现原理之前，我们先来写个简单的小例子，从例子出发去分析DiskLruCache的实现原理。

```java
File directory = getCacheDir();
int appVersion = 1;
int valueCount = 1;
long maxSize = 10 * 1024;
DiskLruCache diskLruCache = DiskLruCache.open(directory, appVersion, valueCount, maxSize);

DiskLruCache.Editor editor = diskLruCache.edit(String.valueOf(System.currentTimeMillis()));
BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(editor.newOutputStream(0));
Bitmap bitmap = BitmapFactory.decodeResource(getResources(), R.drawable.scenery);
bitmap.compress(Bitmap.CompressFormat.JPEG, 100, bufferedOutputStream);

editor.commit();
diskLruCache.flush();
diskLruCache.close();
```
这个就是DiskLruCache的大致使用流程，我们来看看这个入口方法的实现，如下所示：

```java
public final class DiskLruCache implements Closeable {
    
     public static DiskLruCache open(File directory, int appVersion, int valueCount, long maxSize)
          throws IOException {
        if (maxSize <= 0) {
          throw new IllegalArgumentException("maxSize <= 0");
        }
        if (valueCount <= 0) {
          throw new IllegalArgumentException("valueCount <= 0");
        }
    
        File backupFile = new File(directory, JOURNAL_FILE_BACKUP);
        //如果备份文件存在
        if (backupFile.exists()) {
          File journalFile = new File(directory, JOURNAL_FILE);
          // 如果journal文件存在，则把备份文件journal.bkp是删了
          if (journalFile.exists()) {
            backupFile.delete();
          } else {
            //如果journal文件不存在，则将备份文件命名为journal
            renameTo(backupFile, journalFile, false);
          }
        }
    
        DiskLruCache cache = new DiskLruCache(directory, appVersion, valueCount, maxSize);
        
        //判断journal文件是否存在
        if (cache.journalFile.exists()) {
          //如果日志文件以及存在
          try {
            //读取journal文件，根据记录中不同的操作类型进行相应的处理。
            cache.readJournal();
            //计算当前缓存容量的大小
            cache.processJournal();
            cache.journalWriter = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(cache.journalFile, true), Util.US_ASCII));
            return cache;
          } catch (IOException journalIsCorrupt) {
            System.out
                .println("DiskLruCache "
                    + directory
                    + " is corrupt: "
                    + journalIsCorrupt.getMessage()
                    + ", removing");
            cache.delete();
          }
        }
    
        // Create a new empty cache.
        //创建新的缓存目录
        directory.mkdirs();
        cache = new DiskLruCache(directory, appVersion, valueCount, maxSize);
        //调用新的方法建立新的journal文件
        cache.rebuildJournal();
        return cache;
      }
}
```
先来说一下这个入口方法的四个参数的含义：

- File directory：缓存目录。
- int appVersion：应用版本号。
- int valueCount：一个key对应的缓存文件的数目，如果我们传入的参数大于1，那么缓存文件后缀就是.0，.1等。
- long maxSize：缓存容量上限。

DiskLruCache的构造方法并没有做别的事情，只是简单的将对应成员变量进行初始化，open()方法主要围绕着journal文件的创建与读写而展开的，如下所示：

- readJournal()：读取journal文件，主要是读取文件头里的信息进行检验，然后调用readJournalLine()逐行去读取，根据读取的内容，执行相应的缓存
添加、移除等操作。
- rebuildJournal()：重建journal文件，重建journal文件主要是写入文件头（上面提到的journal文件都有的前面五行的内容）。

我们接着来分析什么是journal文件，以及它的创建与读写流程。

### 3.1 journal文件的创建

在前面分析的open()方法中，主要围绕着journal文件的创建和读写来展开的，那么journal文件是什么呢？🤔

我们如果去打开缓存目录，就会发现除了缓存文件，还会发现一个journal文件，journal文件用来记录缓存的操作记录的，如下所示：

```
libcore.io.DiskLruCache
1
1
1

DIRTY 1517126350519
CLEAN 1517126350519 5325928
REMOVE 1517126350519
```

注：这里的缓存目录是应用的缓存目录/data/data/pckagename/cache，未root的手机可以通过以下命令进入到该目录中或者将该目录整体拷贝出来：

```java

//进入/data/data/pckagename/cache目录
adb shell
run-as com.your.packagename 
cp /data/data/com.your.packagename/

//将/data/data/pckagename目录拷贝出来
adb backup -noapk com.your.packagename
```

我们来分析下这个文件的内容：

- 第一行：libcore.io.DiskLruCache，固定字符串。
- 第二行：1，DiskLruCache源码版本号。
- 第三行：1，App的版本号，通过open()方法传入进去的。
- 第四行：1，每个key对应几个文件，一般为1.
- 第五行：空行
- 第六行及后续行：缓存操作记录。

第六行及后续行表示缓存操作记录，关于操作记录，我们需要了解以下三点：

1. DIRTY 表示一个entry正在被写入。写入分两种情况，如果成功会紧接着写入一行CLEAN的记录；如果失败，会增加一行REMOVE记录。注意单独只有DIRTY状态的记录是非法的。
2. 当手动调用remove(key)方法的时候也会写入一条REMOVE记录。
3. READ就是说明有一次读取的记录。
4. CLEAN的后面还记录了文件的长度，注意可能会一个key对应多个文件，那么就会有多个数字。

这几种操作对应到DiskLruCache源码中，如下所示：

```java
private static final String CLEAN = "CLEAN";
private static final String DIRTY = "DIRTY";
private static final String REMOVE = "REMOVE";
private static final String READ = "READ";

```

那么构建一个新的journal文件呢？上面我们也说过这是调用rebuildJournal()方法来完成的。

#### rebuildJournal()

```java
public final class DiskLruCache implements Closeable {
    
     static final String MAGIC = "libcore.io.DiskLruCache";
    
     private synchronized void rebuildJournal() throws IOException {
        if (journalWriter != null) {
          journalWriter.close();
        }
    
        Writer writer = new BufferedWriter(
            new OutputStreamWriter(new FileOutputStream(journalFileTmp), Util.US_ASCII));
        try {
          writer.write(MAGIC);
          writer.write("\n");
          writer.write(VERSION_1);
          writer.write("\n");
          writer.write(Integer.toString(appVersion));
          writer.write("\n");
          writer.write(Integer.toString(valueCount));
          writer.write("\n");
          writer.write("\n");
    
          for (Entry entry : lruEntries.values()) {
            if (entry.currentEditor != null) {
              writer.write(DIRTY + ' ' + entry.key + '\n');
            } else {
              writer.write(CLEAN + ' ' + entry.key + entry.getLengths() + '\n');
            }
          }
        } finally {
          writer.close();
        }
    
        if (journalFile.exists()) {
          renameTo(journalFile, journalFileBackup, true);
        }
        renameTo(journalFileTmp, journalFile, false);
        journalFileBackup.delete();
    
        journalWriter = new BufferedWriter(
            new OutputStreamWriter(new FileOutputStream(journalFile, true), Util.US_ASCII));
      }
}
```

你可以发现，构建一个新的journal文件过程就是写入文件头的过程，文件头内容包含前面我们说的appVersion、valueCount、空行等五行内容。

我们再来看看如何读取journal文件里的内容。

#### readJournal()

```java
public final class DiskLruCache implements Closeable {
   private void readJournal() throws IOException {
        StrictLineReader reader = new StrictLineReader(new FileInputStream(journalFile), Util.US_ASCII);
        try {
          //读取文件头，并进行校验。
          String magic = reader.readLine();
          String version = reader.readLine();
          String appVersionString = reader.readLine();
          String valueCountString = reader.readLine();
          String blank = reader.readLine();
          //检查前五行的内容是否合法
          if (!MAGIC.equals(magic)
              || !VERSION_1.equals(version)
              || !Integer.toString(appVersion).equals(appVersionString)
              || !Integer.toString(valueCount).equals(valueCountString)
              || !"".equals(blank)) {
            throw new IOException("unexpected journal header: [" + magic + ", " + version + ", "
                + valueCountString + ", " + blank + "]");
          }
    
         int lineCount = 0;
         while (true) {
           try {
             //开启死循环，逐行读取journal内容
             readJournalLine(reader.readLine());
             //文件以及读取的行数
             lineCount++;
           } catch (EOFException endOfJournal) {
             break;
           }
         }
         //lineCount表示文件总行数，lruEntries.size()表示最终缓存的个数，redundantOpCount
         //就表示非法缓存记录的个数，这些非法缓存记录会被移除掉。
         redundantOpCount = lineCount - lruEntries.size();
       } finally {
         Util.closeQuietly(reader);
       }
     }
   
     private void readJournalLine(String line) throws IOException {
       //每行记录都是用空格开分隔的，这里取第一个空格出现的位置
       int firstSpace = line.indexOf(' ');
       //如果没有空格，则说明是非法的记录
       if (firstSpace == -1) {
         throw new IOException("unexpected journal line: " + line);
       }
   
       //第一个空格前面就是CLEAN、READ这些操作类型，接下来针对不同的操作类型进行
       //相应的处理
       int keyBegin = firstSpace + 1;
       int secondSpace = line.indexOf(' ', keyBegin);
       final String key;
       if (secondSpace == -1) {
         key = line.substring(keyBegin);
         //1. 如果该条记录以REMOVE为开头，则执行删除操作。
         if (firstSpace == REMOVE.length() && line.startsWith(REMOVE)) {
           lruEntries.remove(key);
           return;
         }
       } else {
         key = line.substring(keyBegin, secondSpace);
       }
   
       //2. 如果该key不存在，则新建Entry并加入lruEntries。
       Entry entry = lruEntries.get(key);
       if (entry == null) {
         entry = new Entry(key);
         lruEntries.put(key, entry);
       }
   
       //3. 如果该条记录以CLEAN为开头，则初始化entry，并设置entry.readable为true、设置entry.currentEditor为
       //null，初始化entry长度。
       //CLEAN 3400330d1dfc7f3f7f4b8d4d803dfcf6 832 21054
       if (secondSpace != -1 && firstSpace == CLEAN.length() && line.startsWith(CLEAN)) {
         //数组中其实是数字，其实就是文件的大小。因为可以通过valueCount来设置一个key对应的value的个数，
         //所以文件大小也是有valueCount个
         String[] parts = line.substring(secondSpace + 1).split(" ");
         entry.readable = true;
         entry.currentEditor = null;
         entry.setLengths(parts);
       }
       //4. 如果该条记录以DIRTY为开头。则设置currentEditor对象。
       //DIRTY 335c4c6028171cfddfbaae1a9c313c52
       else if (secondSpace == -1 && firstSpace == DIRTY.length() && line.startsWith(DIRTY)) {
         entry.currentEditor = new Editor(entry);
       } 
       //5. 如果该条记录以READ为开头，则什么也不做。
       else if (secondSpace == -1 && firstSpace == READ.length() && line.startsWith(READ)) {
         // This work was already done by calling lruEntries.get().
       } else {
         throw new IOException("unexpected journal line: " + line);
       }
     } 
}
```

新来说一下这个lruEntries是什么，如下所示：

```java
private final LinkedHashMap<String, Entry> lruEntries =
  new LinkedHashMap<String, Entry>(0, 0.75f, true);
```

就跟上面的LruCache一样，它也是一个以访问顺序为序的LinkedHashMap，可以用它来实现Lru算法。

该方法的逻辑就是根据记录中不同的操作类型进行相应的处理，如下所示：

1. 如果该条记录以REMOVE为开头，则执行删除操作。
2. 如果该key不存在，则新建Entry并加入lruEntries。
3. 如果该条记录以CLEAN为开头，则初始化entry，并设置entry.readable为true、设置entry.currentEditor为null，初始化entry长度。
4. 如果该条记录以DIRTY为开头。则设置currentEditor对象。
5. 如果该条记录以READ为开头，则什么也不做。

说了这么多，readJournalLine()方法主要是通过读取journal文件的每一行，然后封装成entry对象，放到了LinkedHashMap集合中。并且根据每一行不同的开头，设置entry的值。也就是说通过读取这
个文件，我们把所有的在本地缓存的文件的key都保存到了集合中，这样我们用的时候就可以通过集合来操作了。
      
### processJournal()

```java
public final class DiskLruCache implements Closeable {
    
      private void processJournal() throws IOException {
        //删除journal.tmp临时文件
        deleteIfExists(journalFileTmp);
        //变量缓存集合里的所有元素
        for (Iterator<Entry> i = lruEntries.values().iterator(); i.hasNext(); ) {
          Entry entry = i.next();
          //如果当前元素entry的currentEditor不为空，则计算该元素的总大小，并添加到总缓存容量size中去
          if (entry.currentEditor == null) {
            for (int t = 0; t < valueCount; t++) {
              size += entry.lengths[t];
            }
          } 
          //如果当前元素entry的currentEditor不为空，代表该元素时非法缓存记录，该记录以及对应的缓存文件
          //都会被删除掉。
          else {
            entry.currentEditor = null;
            for (int t = 0; t < valueCount; t++) {
              deleteIfExists(entry.getCleanFile(t));
              deleteIfExists(entry.getDirtyFile(t));
            }
            i.remove();
          }
        }
      }
}
```

这里提到了一个非常缓存记录，那么什么是非法缓存记录呢？🤔

>DIRTY 表示一个entry正在被写入。写入分两种情况，如果成功会紧接着写入一行CLEAN的记录；如果失败，会增加一行REMOVE记录。注意单独只有DIRTY状态的记录是非法的。

该方法主要用来计算当前的缓存总容量，并删除非法缓存记录以及该记录对应的文件。

理解了journal文件的创建以及读写流程，我们来看看硬盘缓存的写入、读取和删除的过程。

### 3.2 写入缓存

DiskLruCache缓存的写入是通过edit()方法来完成的。


```java
public final class DiskLruCache implements Closeable {
    
    private synchronized Editor edit(String key, long expectedSequenceNumber) throws IOException {
        checkNotClosed();
        validateKey(key);
        //从之前的缓存中读取对应的entry
        Entry entry = lruEntries.get(key);
        //当前无法写入磁盘缓存
        if (expectedSequenceNumber != ANY_SEQUENCE_NUMBER && (entry == null
            || entry.sequenceNumber != expectedSequenceNumber)) {
          return null; // Snapshot is stale.
        }
        
        //如果entry为空，则新建一个entry对象加入到缓存集合中
        if (entry == null) {
          entry = new Entry(key);
          lruEntries.put(key, entry);
        } 
        //currentEditor不为空，表示当前有别的插入操作在执行
        else if (entry.currentEditor != null) {
          return null; // Another edit is in progress.
        }
    
        //为当前创建的entry知道新创建的editor
        Editor editor = new Editor(entry);
        entry.currentEditor = editor;
    
        //向journal写入一行DIRTY + 空格 + key的记录，表示这个key对应的缓存正在处于被编辑的状态。
        journalWriter.write(DIRTY + ' ' + key + '\n');
        //刷新文件里的记录
        journalWriter.flush();
        return editor;
      }
}
```

这个方法构建了一个Editor对象，它主要做了两件事情：

1. 从集合中找到对应的实例（如果没有创建一个放到集合中），然后创建一个editor，将editor和entry关联起来。
2. 向journal中写入一行操作数据（DITTY 空格 和key拼接的文字），表示这个key当前正处于编辑状态。

我们在前面的DiskLruCache的使用例子中，调用了Editor的newOutputStream()方法创建了一个OutputStream来写入缓存文件。如下所示：

```java
public final class DiskLruCache implements Closeable {
    
    public InputStream newInputStream(int index) throws IOException {
      synchronized (DiskLruCache.this) {
        if (entry.currentEditor != this) {
          throw new IllegalStateException();
        }
        if (!entry.readable) {
          return null;
        }
        try {
          return new FileInputStream(entry.getCleanFile(index));
        } catch (FileNotFoundException e) {
          return null;
        }
      }
    }
}
```

这个方法的形参index就是我们开始在open()方法里传入的valueCount，这个valueCount表示了一个key对应几个value,也就是说一个key对应几个缓存文件。那么现在传入的这个index就表示
要缓存的文件时对应的第几个value。

有了输出流，我们在接着调用Editor的commit()方法就可以完成缓存文件的写入了，如下所示：

```java
public final class DiskLruCache implements Closeable {
     public void commit() throws IOException {
         //如果通过输出流写入缓存文件出错了就把集合中的缓存移除掉
          if (hasErrors) {
            completeEdit(this, false);
            remove(entry.key); // The previous entry is stale.
          } else {
            //调用completeEdit()方法完成缓存写入。
            completeEdit(this, true);
          }
          committed = true;
        }
}
```

可以看到该方法调用DiskLruCache的completeEdit()方法来完成缓存写入，如下所示：

```java
public final class DiskLruCache implements Closeable {
    
    private synchronized void completeEdit(Editor editor, boolean success) throws IOException {
        Entry entry = editor.entry;
        if (entry.currentEditor != editor) {
          throw new IllegalStateException();
        }
    
        // If this edit is creating the entry for the first time, every index must have a value.
        if (success && !entry.readable) {
          for (int i = 0; i < valueCount; i++) {
            if (!editor.written[i]) {
              editor.abort();
              throw new IllegalStateException("Newly created entry didn't create value for index " + i);
            }
            if (!entry.getDirtyFile(i).exists()) {
              editor.abort();
              return;
            }
          }
        }
    
        for (int i = 0; i < valueCount; i++) {
          //获取对象缓存的临时文件
          File dirty = entry.getDirtyFile(i);
          if (success) {
            //如果临时文件存在，则将其重名为正式的缓存文件
            if (dirty.exists()) {
              File clean = entry.getCleanFile(i);
              dirty.renameTo(clean);
              long oldLength = entry.lengths[i];
              long newLength = clean.length();
              entry.lengths[i] = newLength;
              //重新计算缓存的大小
              size = size - oldLength + newLength;
            }
          } else {
            //如果写入不成功，则删除掉临时文件。
            deleteIfExists(dirty);
          }
        }
    
        //操作次数自增
        redundantOpCount++;
        //将当前缓存的编辑器置为空
        entry.currentEditor = null;
        if (entry.readable | success) {
          //缓存已经写入，设置为可读。
          entry.readable = true;
          //向journal写入一行CLEAN开头的记录，表示缓存成功写入到磁盘。
          journalWriter.write(CLEAN + ' ' + entry.key + entry.getLengths() + '\n');
          if (success) {
            entry.sequenceNumber = nextSequenceNumber++;
          }
        } else {
          //如果不成功，则从集合中删除掉这个缓存
          lruEntries.remove(entry.key);
          //向journal文件写入一行REMOVE开头的记录，表示删除了缓存
          journalWriter.write(REMOVE + ' ' + entry.key + '\n');
        }
        journalWriter.flush();
    
        //如果缓存总大小已经超过了设定的最大缓存大小或者操作次数超过了2000次，
        // 就开一个线程将集合中的数据删除到小于最大缓存大小为止并重新写journal文件
        if (size > maxSize || journalRebuildRequired()) {
          executorService.submit(cleanupCallable);
        }
      }
}
```

这个方法一共做了以下几件事情：

1. 如果输出流写入数据成功，就把写入的临时文件重命名为正式的缓存文件
2. 重新设置当前总缓存的大小
3. 向journal文件写入一行CLEAN开头的字符（包括key和文件的大小，文件大小可能存在多个 使用空格分开的）
4. 如果输出流写入失败，就删除掉写入的临时文件，并且把集合中的缓存也删除
5. 向journal文件写入一行REMOVE开头的字符
6. 重新比较当前缓存和最大缓存的大小，如果超过最大缓存或者journal文件的操作大于2000条，就把集合中的缓存删除一部分，直到小于最大缓存，重新建立新的journal文件

到这里，缓存的插入流程就完成了。

### 3.3 读取缓存

读取缓存是由DiskLruCache的get()方法来完成的。

```java
public final class DiskLruCache implements Closeable {
    
      public synchronized Snapshot get(String key) throws IOException {
        checkNotClosed();
        validateKey(key);
        //获取对应的entry
        Entry entry = lruEntries.get(key);
        if (entry == null) {
          return null;
        }
    
        //如果entry不可读，说明可能在编辑，则返回空。
        if (!entry.readable) {
          return null;
        }
    
        //打开所有缓存文件的输入流，等待被读取。
        InputStream[] ins = new InputStream[valueCount];
        try {
          for (int i = 0; i < valueCount; i++) {
            ins[i] = new FileInputStream(entry.getCleanFile(i));
          }
        } catch (FileNotFoundException e) {
          // A file must have been deleted manually!
          for (int i = 0; i < valueCount; i++) {
            if (ins[i] != null) {
              Util.closeQuietly(ins[i]);
            } else {
              break;
            }
          }
          return null;
        }
    
        redundantOpCount++;
        //向journal写入一行READ开头的记录，表示执行了一次读取操作
        journalWriter.append(READ + ' ' + key + '\n');
        
         
        //如果缓存总大小已经超过了设定的最大缓存大小或者操作次数超过了2000次，
        // 就开一个线程将集合中的数据删除到小于最大缓存大小为止并重新写journal文件
        if (journalRebuildRequired()) {
          executorService.submit(cleanupCallable);
        }
        
        //返回一个缓存文件快照，包含缓存文件大小，输入流等信息。
        return new Snapshot(key, entry.sequenceNumber, ins, entry.lengths);
      }
}
```

读取操作主要完成了以下几件事情：

1. 获取对应的entry。
2. 打开所有缓存文件的输入流，等待被读取。
3. 向journal写入一行READ开头的记录，表示执行了一次读取操作。
4. 如果缓存总大小已经超过了设定的最大缓存大小或者操作次数超过了2000次，就开一个线程将集合中的数据删除到小于最大缓存大小为止并重新写journal文件。
5. 返回一个缓存文件快照，包含缓存文件大小，输入流等信息。

该方法最终返回一个缓存文件快照，包含缓存文件大小，输入流等信息。利用这个快照我们就可以读取缓存文件了。

### 3.4 删除缓存

删除缓存是由DiskLruCache的remove()方法来完成的。

```java
public final class DiskLruCache implements Closeable {
    
      public synchronized boolean remove(String key) throws IOException {
        checkNotClosed();
        validateKey(key);
        //获取对应的entry
        Entry entry = lruEntries.get(key);
        if (entry == null || entry.currentEditor != null) {
          return false;
        }
    
        //删除对应的缓存文件，并将缓存大小置为0.
        for (int i = 0; i < valueCount; i++) {
          File file = entry.getCleanFile(i);
          if (file.exists() && !file.delete()) {
            throw new IOException("failed to delete " + file);
          }
          size -= entry.lengths[i];
          entry.lengths[i] = 0;
        }
    
        redundantOpCount++;
        //向journal文件添加一行REMOVE开头的记录，表示执行了一次删除操作。
        journalWriter.append(REMOVE + ' ' + key + '\n');
        lruEntries.remove(key);
    
    
        //如果缓存总大小已经超过了设定的最大缓存大小或者操作次数超过了2000次，
        // 就开一个线程将集合中的数据删除到小于最大缓存大小为止并重新写journal文件
        if (journalRebuildRequired()) {
          executorService.submit(cleanupCallable);
        }
    
        return true;
      }   
}
```

删除操作主要做了以下几件事情：

1. 获取对应的entry。
2. 删除对应的缓存文件，并将缓存大小置为0.
3. 向journal文件添加一行REMOVE开头的记录，表示执行了一次删除操作。
4. 如果缓存总大小已经超过了设定的最大缓存大小或者操作次数超过了2000次，就开一个线程将集合中的数据删除到小于最大缓存大小为止并重新写journal文件。

好，到这里LrcCache和DiskLruCache的实现原理都讲完了，这两个类在主流的图片框架Fresco、Glide和网络框架Okhttp等都有着广泛的应用，后续的文章后继续分析LrcCache和DiskLruCache
在这些框架里的应用。

## 附录

### 图片占用内存大小的计算

Android里面缓存应用最多的场景就是图片缓存了，谁让图片在内存里是个大胖子呢，在做缓存的时候我们经常会去计算图片展内存的大小。

那么如何去获取一张图片占用内存的大小呢？🤔

```java
private int getBitmapSize(Bitmap bitmap) {
    //API 19
    if (Build.VERSION.SDK_INT == Build.VERSION_CODES.KITKAT) {
        return bitmap.getAllocationByteCount();
    }
    //API 12
    if (Build.VERSION.SDK_INT == Build.VERSION_CODES.HONEYCOMB_MR1) {
        return bitmap.getByteCount();
    }
    // Earlier Version
    return bitmap.getRowBytes() * bitmap.getHeight();
}
```

那么这三个方法处了版本上的差异，具体有什么区别呢？

>getRowBytes()返回的是每行的像素值，乘以高度就是总的像素数，也就是占用内存的大小。 getAllocationByteCount()与getByteCount()的返回值一般情况下都是相等的。只是在图片
复用的时候，getAllocationByteCount()返回的是复用图像所占内存的大小，getByteCount()返回的是新解码图片占用内存的大小。

我们来写一个小例子验证一下，如下所示：

```java
BitmapFactory.Options options = new BitmapFactory.Options();
options.inDensity = 320;
options.inTargetDensity = 320;
//要实现复用，图像必须是可变的，也就是inMutable为true。
options.inMutable = true;
Bitmap bitmap = BitmapFactory.decodeResource(getResources(), R.drawable.scenery, options);
Log.d(TAG, "bitmap.getAllocationByteCount(): " + String.valueOf(bitmap.getAllocationByteCount()));
Log.d(TAG, "bitmap.getByteCount(): " + String.valueOf(bitmap.getByteCount()));
Log.d(TAG, "bitmap.getRowBytes() * bitmap.getHeight(): " + String.valueOf(bitmap.getRowBytes() * bitmap.getHeight()));

BitmapFactory.Options reuseOptions = new BitmapFactory.Options();
reuseOptions.inDensity = 320;
reuseOptions.inTargetDensity = 320;
//要复用的Bitmap
reuseOptions.inBitmap = bitmap;
//要实现复用，图像必须是可变的，也就是inMutable为true。
reuseOptions.inMutable = true;
Bitmap reuseBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.scenery_reuse, reuseOptions);
Log.d(TAG, "reuseBitmap.getAllocationByteCount(): " + String.valueOf(reuseBitmap.getAllocationByteCount()));
Log.d(TAG, "reuseBitmap.getByteCount(): " + String.valueOf(reuseBitmap.getByteCount()));
Log.d(TAG, "reuseBitmap.getRowBytes() * reuseBitmap.getHeight(): " + String.valueOf(reuseBitmap.getRowBytes() * reuseBitmap.getHeight()));
```

运行的log如下所示：

<img src="https://github.com/guoxiaoxing/android-open-framwork-analysis/raw/master/art/lru/bitmap_reuse.png"/>

可以发现reuseBitmap的getAllocationByteCount()和getByteCount()返回不一样，getAllocationByteCount()返回的是复用bitmap占用内存的大小，
getByteCount()返回的是新的reuseOptions实际解码占用的内存大小。

注意在复用图片的时候，options.inMutable必须设置为true，否则无法进行复用，如下所示：

<img src="https://github.com/guoxiaoxing/android-open-framwork-analysis/raw/master/art/lru/bitmap_non_reuse.png"/>