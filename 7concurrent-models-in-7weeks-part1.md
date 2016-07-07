---
title: 7周7并发模型 P1 - Thread and Lock
date: 2016-06-29 02:27:25
tags: [并发,多线程,7日7并发模型,笔记]
categories: 并发
---

Notes on 7 Concurrent Models in 7 Weeks - Part 1. Thread and Lock

<!-- more -->

**How to create a thread**
``` java
public class HelloWorld {
  public static void main(String[] args) throws InterruptedException {
    Thread myThread = new Thread() {
        public void run() {
          System.out.println("Hello from new thread");
        }
      };
    myThread.start();
    Thread.yield();
    System.out.println("Hello from main thread");
    myThread.join();
  }
}
```

Why Thread.yield();  
yield() is a hint to the scheduler that the current thread is willing to yield the current use of a processor.
Without this call, the startup overhead of the new thread would mean that the main thread would almost certainly print first.


**Intrinsic Lock**
``` java
class Counter {
  private int count = 0;
  public synchronized void increment() { ++count; }
  public int getCount() { return count; }
}
```

Intrinsic lock comes built into every Java object.

*synchronized* claims that the Counter object' lock when it is called and released when it returns.

The code has some flaws which will be revealed later.


**Mysterious Memory**
``` java
 public class Puzzle {
   static boolean answerReady = false;
   static int answer = 0;
   static Thread t1 = new Thread() {
       public void run() {
         answer = 42;
         answerReady = true;
       }
     };
   static Thread t2 = new Thread() {
       public void run() {
         if (answerReady)
           System.out.println("The meaning of life is: " + answer);
         else
           System.out.println("I don't know the answer");
       }
     };

   public static void main(String[] args) throws InterruptedException {
     t1.start(); t2.start();
     t1.join(); t2.join();
   }
 }
```

The output could be: "The meaning of life is: 0", how is it possible? Because line 6 & 7 could be swipped due to

1. Compiler statically optimization by reordering
2. JVM dynamically optimization
3. Hardware is allowded to optimize performance

And it goes further than just reordering, sometimes it could even change the logic ... 

Take above example, the line 7 could even be skipped and answerReady might not be true forever ...


**Memory Visibility**

The java memory model defines when changes to memory made by one thread become visible to another thread.

The bottom line is that there are no guarantees unless both the *reading and writing* threads use synchronization.

The flaw in the code of *Intrinsic Lock* section
``` java
class Counter {
  private int count = 0;
  public synchronized void increment() { ++count; }
  public synchronized int getCount() { return count; }
}
```

Yep, getCount() needs to be synchronized as well, or a thread calling getCount() may see a stale (not fresh) value.


**Dead Lock when there are multiple locks**

Based on previous sections, it is the only safe way in a multithread world to make every method synchronized, but:

1. it is dreadfully inefficient.
2. as soon as you have more than one lock, the opportunity is created for the threads to become deadlocked.

*Dining Philosophers - dead locked version*
``` java
class Philosopher extends Thread {
  private Chopstick left, right;
  private Random random;
  public Philosopher(Chopstick left, Chopstick right) {
    this.left = left; this.right = right;
    random = new Random();
  }
  public void run() {
    try {
      while(true) {
        Thread.sleep(random.nextInt(1000));     // Think for a while
        synchronized(left) {                    // Grab left chopstick
          synchronized(right) {                 // Grab right chopstick
            Thread.sleep(random.nextInt(1000)); // Eat for a while
          }
        }
      }
    } catch(InterruptedException e) {}
  }
}
```

The alternative way of claiming an object's intrinsic lock: synchronized(object)  

--- claiming an object's intrinsic lock from outer (by other objects)

The main role of the thread is a philosopher which holds two Chopsticks, and the philosopher will lock left one and the right one in a row.
If 5 philosophers going simultaneousely, then they will lock their left Chopsticks at the same time, and try to lock their right ones, 
which have been already locked by the philosophers on their right side ---- BOOM, dead lock !!

Dead lock is a danger whenever a thread tries to hold more than one lock.

How to fix it ??

1. Order the chopstick by some rules.
2. Instead of lock chopstick from left to right, lock them in ascending or descending order.
Done !


**The peril of Alien Method**
``` java
class Downloader extends Thread {
  ......
  private ArrayList<ProgressListener> listeners;
  public Downloader(URL url, String outputFilename) throws IOException {
    ......
    listeners = new ArrayList<ProgressListener>();
  }
  public synchronized void addListener(ProgressListener listener) {
    listeners.add(listener);
  }
  public synchronized void removeListener(ProgressListener listener) {
    listeners.remove(listener);
  }
  private synchronized void updateProgress(int n) {
    for (ProgressListener listener: listeners)
      listener.onProgress(n);
  }
  public void run() {
    ......
    try {
      while((n = in.read(buffer)) != -1) {
        out.write(buffer, 0, n);
        total += n;
        updateProgress(total);
      }
      out.flush();
    } catch (IOException e) { }
  }
}
```

3 public method are all synchronized, looks good !

The problem is that the updateProgress() calls an alien method - a method it knows nothing about.

listener.onProgress() could do anything, including acquiring another lock, which make this a multiple locks case, and dead lock could happen.

How to fixed it? Defensive copy!
``` java
private void updateProgress(int n) {
  ArrayList<ProgressListener> listenersCopy;
  synchronized(this) {
    listenersCopy = (ArrayList<ProgressListener>)listeners.clone();
  }
  for (ProgressListener listener: listenersCopy)
    listener.onProgress(n);
}
```

This fix kills several birds with one stone:

1. Avoid calling an alien method with a lock held.
2. Minimizes the period during which we hold the lock.
3. A listener could now call removeListener() within its onProgress() method without modifying the copy of listener that is mid-iteration.


*Drawback of Intrinsic Lock*

1. No way to interrupt a thread that's blocked as a result of trying to acquire an intrinsic lock.
2. No way to time out while trying to acquire an intrinsic lock.
3. Only one way to acquire an intrinsic lock: synchronized block, so lock acquisition and release have to take place in the same method and strictly nested.


**ReentrantLock to rescue**

Pattern:
``` java
Lock lock = new ReentrantLock();
lock.lock();
try {
  <<use shared resources>>
} finally {
  lock.unlock():
}
```


**Reentrant Lock is interruptable**
``` java
public class Interruptible {
  public static void main(String[] args) throws InterruptedException {
    final ReentrantLock l1 = new ReentrantLock();
    final ReentrantLock l2 = new ReentrantLock();
    Thread t1 = new Thread() {
      public void run() {
        try {
          l1.lockInterruptibly();
          Thread.sleep(1000);
          l2.lockInterruptibly();
        } catch (InterruptedException e) { System.out.println("t1 interrupted"); }
      }
    };
    Thread t2 = new Thread() {
      public void run() {
        try {
          l2.lockInterruptibly();
          Thread.sleep(1000);
          l1.lockInterruptibly();
        } catch (InterruptedException e) { System.out.println("t2 interrupted"); }
      }
    };
    t1.start(); t2.start();
    Thread.sleep(2000);
    t1.interrupt(); t2.interrupt();
    t1.join(); t2.join();
  }
}

```

It supposed to be deadlocked, but with the help of {$lock.}lockInterruptibly() and {$thead.}interrupt(), dead lock could be interrupted.


**Reentrant lock supports timeout**

Another solution fo Dining Philosopher problem -- Timeout if the philosophers could not acquire some of their chopstick.
``` java
class Philosopher extends Thread {
  private ReentrantLock leftChopstick, rightChopstick;
  ......
  public Philosopher(ReentrantLock leftChopstick, ReentrantLock rightChopstick) {
    this.leftChopstick = leftChopstick; this.rightChopstick = rightChopstick;
    random = new Random();
  }
  public void run() {
    try {
      while(true) {
        Thread.sleep(random.nextInt(1000)); // Think for a while
        leftChopstick.lock();
        try {
          if (rightChopstick.tryLock(1000, TimeUnit.MILLISECONDS)) {
            // Got the right chopstick
            try {
              Thread.sleep(random.nextInt(1000)); // Eat for a while
            } finally { rightChopstick.unlock(); }
          } else {
            // Didn't get the right chopstick - give up and go back to thinking
            System.out.println("Philosopher " + this + " timed out");
          }
        } finally { leftChopstick.unlock(); }
      }
    } catch(InterruptedException e) {}
  }
}
```

rightChopstick.tryLock(1000, TimeUnit.MILLISECONDS) supports timeout.

Although the tryLock() solution avoids infinite deadlock, that doesn't mean it is a good solution.

1. It doesn't avoid deadlock, but simple provides a way to recover when deadlock happens.
2. It is susceptible to *livelock* phenomenon -- if all the threads timeout at the same time, it is possible for them to immediately deadlock again.
  Although the deadlock doesn't last forever, no progress is made either. (solve livelock by using different timeout.)


**Hand-over-Hand Locking for a linked-list**

To insert a node, instead of lock the whole linked-list, we only lock the two nodes on either side of the point we're going to insert.

It is impossible to do it with Intrinsic Lock.
``` java
class ConcurrentSortedList {
  private class Node {
    int value;
    Node prev;
    Node next;
    ReentrantLock lock = new ReentrantLock();
    Node() {}
    Node(int value, Node prev, Node next) {
      this.value = value; this.prev = prev; this.next = next;
    }
  }
  private final Node head;
  private final Node tail;
  public ConcurrentSortedList() {
    head = new Node(); tail = new Node();
    head.next = tail; tail.prev = head;
  }

  public void insert(int value) {
    Node current = head;
    current.lock.lock();
    Node next = current.next;
    try {
      while (true) {
        next.lock.lock();
        try {
          if (next == tail || next.value < value) {
            Node node = new Node(value, current, next);
            next.prev = node;
            current.next = node;
            return;
          }
        } finally { current.lock.unlock(); }
        current = next;
        next = current.next;
      }
    } finally { next.lock.unlock(); }
  }

  public int size() {
    Node current = tail;
    int count = 0;
    while (current.prev != head) {
      ReentrantLock lock = current.lock;
      lock.lock();
      try {
        ++count;
        current = current.prev;
      } finally { lock.unlock(); }
    }
    return count;
  }
}
```

Each node has a Reentrant Lock.

insert() iterates linked-list from head to tail, whereas size() iterate from tail to head.

So, doesn't these different iterate directions violate the "Always acquire multiple locks in a fixed global order" rule? 

(Remember that this rule fixed Dining Philosophers problem)

No, it doesn't violate the rule, coz the size() method never holds more than a single lock at a time


**Condition Variable**

Concurrent programming often involves waiting for something to happen, and this type of situation is what condition variables are designed to address.

Pattern:
``` java
ReentrantLock lock = new ReentrantLock();
Condition condition = lock.newCondition();

lock.lock()
try {
  while (!<<condition is true>>)
    condition.await();
  <<use shared resources>>
} finally { lock.unlock(); }
```

A condition variable is associated with a lock, and a thread must hold that lock before being able to wait on the condition.

Once it holds the lock, it checks to see if the condition is already true,

- if true, use shared resources and then unlock
- otherwise, it calls await(), which automatically unlocks the lock and blocks on the condition variable. The unlock and blocks operation is atomic.
- when another thread calls signal() or signalAll() to set condition to true, await() unblocks and automatically reacquires the lock.
- await() is called within a loop, coz we need to go back and recheck whether the condition is true.

Dining Philosopher with condition
``` java
class Philosopher extends Thread {
  private boolean eating;
  private Philosopher left;
  private Philosopher right;
  private ReentrantLock table;
  private Condition condition;
  private Random random;

  public Philosopher(ReentrantLock table) {
    eating = false;
    this.table = table;
    condition = table.newCondition();
    random = new Random();
  }
  public void setLeft(Philosopher left) { this.left = left; }
  public void setRight(Philosopher right) { this.right = right; }
  public void run() {
    try {
      while (true) {
        think();
        eat();
      }
    } catch (InterruptedException e) {}
  }

  private void think() throws InterruptedException {
    table.lock();
    try {
      eating = false;
      left.condition.signal();
      right.condition.signal();
    } finally { table.unlock(); }
    Thread.sleep(1000);
  }

  private void eat() throws InterruptedException {
    table.lock();
    try {
      while (left.eating || right.eating)
        condition.await();
      eating = true;
    } finally { table.unlock(); }
    Thread.sleep(1000);
  }
}
```

Still, a thread represents a philosopher, yet in this version, a philosopher no longer tries to hold two chopsticks.

Instead, the lock is acted on the table, which means the status of all of the 5 philosophers (or 5 chopsticks).

In think(), a philosopher first lock the current status of table, then he send signal the the philosophers sit beside him that he will unlock the table.

In eat(), a philosopher first lock the current status, then he waits for his neighbors to finish eating; if so, he will enter eat stat, and unlock the table.


**Atomic Variables**
``` java
public class Counting {
  public static void main(String[] args) throws InterruptedException {
    final AtomicInteger counter = new AtomicInteger();

    class CountingThread extends Thread {
      public void run() {
        for(int x = 0; x < 10000; ++x)
          counter.incrementAndGet();
      }
    }
    CountingThread t1 = new CountingThread();
    CountingThread t2 = new CountingThread();
    t1.start(); t2.start();
    t1.join(); t2.join();
    System.out.println(counter.get());
  }
}
```

incrementAndGet() && getAndIncrement() are atomic functions for Atomic Variable counter. Using an atomic variable instead of locks has many benefits:

1. Not possible to forget to acquire locks when necessary.
2. No locks are involves, so dead-lock free.
3. Atomic varaibles are the foundation of non-blocking algorithm, for example the classes in java.util.concurrent model.


**Thread-Creation Redux**

A better way to create thread:
``` java
public class EchoServer {
  public static void main(String[] args) throws IOException {
    class ConnectionHandler implements Runnable {
      ......
      ConnectionHandler(Socket socket) throws IOException {
        ......
      }
      public void run() {
        ......
      }
    }
    ServerSocket server = new ServerSocket(4567);
    while (true) {
      Socket socket = server.accept();
      Thread handler = new Thread(new ConnectionHandler(socket));
      handler.start();
    }
  }
}
```

Works fine, but suffers from a couple of issues:

1. Although thread creation is cheap, it is not free, still pay the price for each connection.
2. It create as many threads as connections, and when connections come in faster than they could be handled, server will break -- DDOS

Better way to go?
``` java
    ServerSocket server = new ServerSocket(4567);
    int threadPoolSize = Runtime.getRuntime().availableProcessors() * 2;
    ExecutorService executor = Executors.newFixedThreadPool(threadPoolSize);
    while (true) {
      Socket socket = server.accept();
      executor.execute(new ConnectionHandler(socket));
    }
```

Using a thread pool with twice as many threads as there are available processors.

If connections come in fast, they will be queued until a thread become free.


**Copy on Write**

Recall the *The peril of Alien Method* section, we use a temporary list to hold listeners; Better way to go??
``` java
    private CopyOnWriteArrayList<ProgressListener> listeners;
    public Downloader(URL url, String outputFilename) throws IOException {
      ......
      listeners = new CopyOnWriteArrayList<ProgressListener>();
    }
    ......
    private void updateProgress(int n) {
      for (ProgressListener listener: listeners)
        listener.onProgress(n);
    } 
```

It results in very clear and concise code, and more important, it don't make a copy each time updateProgress() is called,
but only when listeners is modified (some listeners' updateProgress() func may not change the listeners).


**Word Count - Sequential**
``` java
  private static final HashMap<String, Integer> counts = new HashMap<String, Integer>();
  public static void main(String[] args) throws Exception {
    Iterable<Page> pages = new Pages(100000, "enwiki.xml");
    for(Page page: pages) {
      Iterable<String> words = new Words(page.getText());
      for (String word: words)
        countWord(word);
    }
  }
  private static void countWord(String word) {
    Integer currentCount = counts.get(word);
    if (currentCount == null)
      counts.put(word, 1);
    else
      counts.put(word, currentCount + 1);
  }
```

105 seconds to finish.


**Word Count - Producer & Consumer threads**

producer:
``` java
class Parser implements Runnable {
  private BlockingQueue<Page> queue;
  public Parser(BlockingQueue<Page> queue) {
    this.queue = queue;
  }
  public void run() {
    try {
      Iterable<Page> pages = new Pages(100000, "enwiki.xml");
      for (Page page: pages)
        queue.put(page);
    } catch (Exception e) { e.printStackTrace(); }
  }
}
```

consumer:
``` java
class Counter implements Runnable {
  private BlockingQueue<Page> queue;
  private Map<String, Integer> counts;
  public Counter(BlockingQueue<Page> queue, Map<String, Integer> counts) {
    this.queue = queue;
    this.counts = counts;
  }
  public void run() {
    try {
      while(true) {
        Page page = queue.take();
        if (page.isPoisonPill())
          break;
        Iterable<String> words = new Words(page.getText());
        for (String word: words)
          countWord(word);
      }
    } catch (Exception e) { e.printStackTrace(); }
  }
  private void countWord(String word) {
    ......
  }
}
```

main thread:
``` java
  public static void main(String[] args) throws Exception {
    ArrayBlockingQueue<Page> queue = new ArrayBlockingQueue<Page>(100);
    HashMap<String, Integer> counts = new HashMap<String, Integer>();
    Thread counter = new Thread(new Counter(queue, counts));
    Thread parser = new Thread(new Parser(queue));
    counter.start();
    parser.start();
    parser.join();
    queue.put(new PoisonPill());
    counter.join();
  }
```

95 seconds to finish, and we found that parsing wiki pages will only take 10 seconds to finish.

Since producer & consumer start at the same time, so producer(parse pages) take 10 seconds and consumer(count words) takes 95 seconds.

Good, but not good enough.


**Word Count -- multiple consumers using synchronized map**

Synchronized collections don't provide atomic read-modify-write methods. so locks are necessary.

consumer:
``` java
class Counter implements Runnable {
  ......
  private static ReentrantLock lock;
  public Counter(BlockingQueue<Page> queue, Map<String, Integer> counts) {
    ......
    lock = new ReentrantLock();
  }
  public void run() {
    ......
  }
  private void countWord(String word) {
    lock.lock();
    try {
      Integer currentCount = counts.get(word);
      if (currentCount == null)
        counts.put(word, 1);
      else
        counts.put(word, currentCount + 1);
    } finally { lock.unlock(); }
   }
}
```

Notes that the lock is static, so the lock is shared among all consumers.

main thread:
``` java
  private static final int NUM_COUNTERS = 2;
  public static void main(String[] args) throws Exception {
    ArrayBlockingQueue<Page> queue = new ArrayBlockingQueue<Page>(100);
    HashMap<String, Integer> counts = new HashMap<String, Integer>();
    ExecutorService executor = Executors.newCachedThreadPool();
    for (int i = 0; i < NUM_COUNTERS; ++i)
      executor.execute(new Counter(queue, counts));
    Thread parser = new Thread(new Parser(queue));
    parser.start();
    parser.join();
    for (int i = 0; i < NUM_COUNTERS; ++i)
      queue.put(new PoisonPill());
    executor.shutdown();
    executor.awaitTermination(10L, TimeUnit.MINUTES);
  }
```

212 seconds to finish using 2 consumers, even longer than the sequential version.

Why?? Excessive contention - too many threads are trying to access a single shared resource simultaneousely, so lock / unlock takes too many time.


**Word Count - Multi consumers using ConcurrentHashMap**

ConcurrentHashMap to rescue -- which provide atomic functions.
``` java
  private void countWord(String word) {
    while (true) {
      Integer currentCount = counts.get(word);
      if (currentCount == null) {
        if (counts.putIfAbsent(word, 1) == null)
          break;
      } else if (counts.replace(word, currentCount, currentCount + 1)) {
        break;
      }
    }
  }
```

It is faster cause it is lock free.

The workflow in countWord function is changed, the modification process of the varaible counts is held in a loop.

First it will get the current value of counts, then it will *update it based on the current value, for example:

When current is null, update it with putIfAbsent(). If putIfAbsent() is actually called AFTER some thread increases counts, it will fail.

When current is currentCount, update it with replace() parametrized with currentCount. If replace() is called AFTER current value is changed, it will fail.

In the case that the update function fails, the update process will loop again and get the updated current value of counts.

Even the putIfAbsent() or replace() could fail and re-run, this version is still much more faster because it is LOCK-FREE.

```
Consumers   Time(s)   Speedup
1           120       0.87
2           83        1.26
3           65        1.61
4           63        1.67    <-- best one
5           70        1.50
6           79        1.33
```

**Word Count - local counts and merge at last**

Instead of each consumers updating a shared set of counts concurrently, each should maintain its own local set, then merge these local sets in the end.

``` java
  private void mergeCounts() {
    for (Map.Entry<String, Integer> e: localCounts.entrySet()) {
      String word = e.getKey();
      Integer count = e.getValue();
      while (true) {
        Integer currentCount = counts.get(word);
        if (currentCount == null) {
          if (counts.putIfAbsent(word, count) == null)
            break;
        } else if (counts.replace(word, currentCount, currentCount + count)) {
          break;
        }
      }
    }
  }
```

mergeCounts is called by every consumer after it has already get to the end of the queue (page.isPoisonPill()).

So ConcurrentHashMap && Atomic functions are still necessary.

```
Consumers   Time(s)   Speedup
1           95        1.10
2           57        1.83
3           40        2.62
4           39        2.69
5           35        2.96
6           33        3.14    <-- best one
7           41        2.55
```
