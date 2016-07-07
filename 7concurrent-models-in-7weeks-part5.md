---
title: 7周7并发模型 P5 - CSP with Clojure
date: 2016-07-01 08:27:25
tags: [并发,CSP,7日7并发模型,笔记]
categories: 并发
---

Notes on 7 Concurrent Models in 7 Weeks - Part 5. Communicating Sequential Processes

<!-- more -->

### core.async in Clojure

CSP's (Short for Communicating Sequential Processes) recent popularity is largely due to the Go language. 

We’re going to cover CSP by examining the core.async library, which brings Go’s concurrency model to Clojure.

core.async defines a few functions with names that clash with core Clojure library functions.

We could import core.async like below:
```
(ns channels.core
    (:require [clojure.core.async :as async :refer :all
        :exclude [map into reduce merge partition partition-by take]]))
```

### Channels

A channel is a thread-safe queue, which messages could be added to one end and removed from the other.

Unlike actors, where messages are sent to and from specific actors, senders don’t have to know about receivers, or vice versa.

We can write to a channel with >!! and read from it with <\!\!
```
channels.core=> (def c (chan))
#'channels.core/c

channels.core=> (thread (println "Read:" (<!! c) "from c"))
#<ManyToManyChannel clojure.core.async.impl.channels.ManyToManyChannel@78fcc563>

channels.core=> (>!! c "Hello thread")
Read: Hello thread from c
nil
```

core.async provides the handy thread utility macro which runs its code on a separate thread.

The thread prints a message containing whatever it reads from the channel, and will blocks until we actually write to the channel.

##### Buffering

By default, channels are synchronous (or unbuffered), which means writing to a channel blocks until something reads from it.
```
channels.core=> (thread (>!! c "Hello") (println "Write completed"))
#<ManyToManyChannel clojure.core.async.impl.channels.ManyToManyChannel@78fcc563>

channels.core=> (<!! c)
Write completed
"Hello"
```

We can create a buffered channel by passing a buffer size to chan:
```
channels.core=> (def bc (chan 5))
#'channels.core/bc
channels.core=> (>!! bc 0)
nil
channels.core=> (>!! bc 1)
nil
channels.core=> (close! bc)
nil
channels.core=> (<!! bc)
0
channels.core=> (<!! bc)
1
channels.core=> (<!! bc)
nil
```

As above, we can close a chan with close!. Reading from an empty closed channel returns nil, and writing to a closed channel silently discards the message.

##### readall!! and writeall!!

```
(defn readall!! [ch]
    (loop [coll []]
        (if-let [x (<!! ch)]
            (recur (conj coll x))
            coll)))
```

This loops with coll initially bound to the empty vector []. Each iteration reads a value from ch.

If the value is not nil, it’s added to coll and go to the next iteration; otherwise (the channel has been closed), coll is returned.

```
(defn writeall!! [ch coll]
    (doseq [x coll]
        (>!! ch x))
    (close! ch))
```

core.async provides utilities that perform similar tasks to save us the trouble of writing our own:
```
channels.core=> (def ch (chan 10))
#'channels.core/ch

channels.core=> (onto-chan ch (range 0 10))
#<ManyToManyChannel clojure.core.async.impl.channels.ManyToManyChannel@6b16d3cf>

channels.core=> (<!! (async/into [] ch))
[0 1 2 3 4 5 6 7 8 9]
```

##### Full Buffer Strategies

By default, writing to a full channel will block. But we can choose an alternative strategy by passing a buffer to chan:

A dropping buffer doesn't block, even though the channel cannot hold so many messages, and drops all subsequent messages when channel is full.
```
channels.core=> (def dc (chan (dropping-buffer 5)))
#'channels.core/dc

channels.core=> (onto-chan dc (range 0 10))
#<ManyToManyChannel clojure.core.async.impl.channels.ManyToManyChannel@147c0def>

channels.core=> (<!! (async/into [] dc))
[0 1 2 3 4]
```

A sliding buffer will drop oldest message to hold recent message.
```
channels.core=> (def sc (chan (sliding-buffer 5)))
#'channels.core/sc

channels.core=> (onto-chan sc (range 0 10))
#<ManyToManyChannel clojure.core.async.impl.channels.ManyToManyChannel@3071908b>

channels.core=> (<!! (async/into [] sc))
[5 6 7 8 9]
```


### Go Blocks

- Threads have both an overhead and a startup cost, so modern programs avoid creating threads directly and use a thread pool instead.
- Thread pools are a great way to handle CPU-intensive tasks which often tie a thread up for a brief period and then return it to the poll to be reused.
- Typically event-driven model will be used for IO-intensive task involving communication, but it breaks up the natural flow of control, and worse it leads to an excess of global state.
- Go blocks to rescue, which provide an alternative that gives us efficiency of event-driven code without having to compromise its structure or readability.
- Code within a go block is transformed into a state machine. 
    + Instead of blocking when it reads from or writes to a channel, the state machine parks, relinquishing control of the thread it’s executing on. 
    + When it’s next able to run, it performs a state transition and continues execution, potentially on another thread.

##### Parking

```
channels.core=> (def ch (chan))
#'channels.core/ch

channels.core=> (go
           #_=>     (let [x (<! ch)
           #_=>           y (<! ch)]
           #_=>         (println "Sum:" (+ x y))))
#<ManyToManyChannel clojure.core.async.impl.channels.ManyToManyChannel@13ac7b98>

channels.core=> (>!! ch 3)
nil
channels.core=> (>!! ch 4)
nil
Sum: 7
```

The go block that reads two values from ch, then prints their sum. The single exclamation mark (<\! or >!) means parking version instead of blocking version.

The go macro converts this sequential code into a state machine with three states:
```
      parking  -------->  [  <!ch  ]  ------->  [  <!ch  ]  -------> [  output results  ]  ------->  terminating
```

So what is the different between parking and blocking version of channel reading/writing? 

If we use blocking >!! or <\!\! in go block, we might deadlock because too many go blocks are running and blocking enough threads so no more are available.

The point of all the go macro’s cleverness is efficiency, go blocks are cheap, we can create many of them without running out of resources.

##### Go Blocks Are Cheap

Go macro returns a channel.
```
channels.core=> (<!! (go (+ 3 4)))
7
```

We can use this fact to create a function that creates a very large number of go blocks, allowing us to see just how inexpensive go blocks are
```
(defn go-add [x y]
    (<!! (nth (iterate #(go (inc (<! %))) (go x)) y)))
```

That is the world’s most inefficient addition function:

1. #(go (inc (<\! %))) is an anonymous func which reads a single value from input chan %, then incr the value and create a new chan to return the increased value.
2. (iterate $ANONYMOUSFUNC (go x)) will return a lazy sequence of the form (x, f(x), f(f(x)), ....), where the initial value is (go x)
3. nth $LAZYSEQ y will return the y-th element of the lazy sequence, which is the return value of a go block (so, it is a channel)
4. Finally, read the result from the chan.

```
channels.core=> (time (go-add 10 10))
"Elapsed time: 1.935 msecs"
20
channels.core=> (time (go-add 10 100000))
"Elapsed time: 734.91 msecs"
100010
```

So, it takes 734.91 msecs to create 100000 go blocks! Wonderful!

##### Operations over Channels

Mapping over a Channel
```
(defn map-chan [f from]
    (let [to (chan)]
        (go-loop []
            (when-let [x (<! from)]
                (>! to (f x))
                (recur))
            (close! to))
        to))
```
go-loop is an utility function that’s equivalent to (go (loop …)).

```
channels.core=> (def ch (chan 10))
#'channels.core/ch

channels.core=> (def mapped (map-chan (partial * 2) ch))
#'channels.core/mapped

channels.core=> (onto-chan ch (range 0 10))
#<ManyToManyChannel clojure.core.async.impl.channels.ManyToManyChannel@9f3d43e>

channels.core=> (<!! (async/into [] mapped))
[0 2 4 6 8 10 12 14 16 18]
```

core.async provides its own version of map-chan, called map< as well as channel-oriented filter called filter<, mapcat called mapcat<, and so on.
```
channels.core=> (def ch (to-chan (range 0 10)))
#'channels.core/ch
channels.core=> (<!! (async/into [] (map< (partial * 2) (filter< even? ch))))
[0 4 8 12 16]
```
to-chan (range 0 10) is an utility function that is equivalent to ((def ch (chan 10)) (onto-chan ch (range 0 10)))

##### Example: A Concurrent Sieve of Eratosthenes

```
(defn factor? [x y]
    (zero? (mod y x)))

(defn get-primes [limit]
    (let [primes (chan)
          numbers (to-chan (range 2 limit))]
        (go-loop [ch numbers]
            (when-let [prime (<! ch)]
                (>! primes prime)
                (recur (remove< (partial factor? prime) ch)))
            (close! primes))
        primes))
```

In the go-loop, we copy numbers to ch, then iteratelly read a prime from ch; whenever a prime is read, remove numbers which matches (partial factor? prime).


### Handling Multiple Channels

##### the alt! function

The alt! function allows us to write code that can deal with more than one channel at a time.
```
channels.core=> (def ch1 (chan))
#'channels.core/ch1
channels.core=> (def ch2 (chan))
#'channels.core/ch2

channels.core=> (go-loop []
                #_=> (alt!
                #_=>    ch1 ([x] (println "Read" x "from channel 1"))
                #_=>    ch2 ([x] (println "Twice" x "is" (* x 2))))
                #_=> (recur))
#<ManyToManyChannel clojure.core.async.impl.channels.ManyToManyChannel@d8fd215>

channels.core=> (>!! ch1 "foo")
Read foo from channel 1
nil
channels.core=> (>!! ch2 21)
Twice 21 is 42
nil
```

The alt! macro takes pairs of arguments, the first is a channel and the second is code executed if there’s anything to read from that channel.

##### Timeouts

The timeout function returns a channel that closes after a certain number of milliseconds.
```
channels.core=> (time (<!! (timeout 10000)))
"Elapsed time: 10001.662 msecs"
nil
```

Use timeout in conjunction with alt! to allow timeout operations.
```
channels.core=> (let [t (timeout 10000)]
             #_=> (go (alt!
             #_=>   ch ([x] (println "Read" x "from channel"))
             #_=>   t (println "Timed out"))))
```

##### Reified Timeouts

Normally timeouts are used on a per-request basis, but what if we want to limit the total time taken by a series of requests? Reified timeout to rescue!

Let's modify the sieve example, instead of taking a numeric limit, it will simply generates as many prime numbers as it can in a given number of seconds.

Instead of initializing channel by (range 2 limit), we use the infinite sequence (iterate inc 2)
```
(defn get-primes []
    (let [primes (chan)
          numbers (to-chan (iterate inc 2))]
        (go-loop [ch numbers]
            (when-let [prime (<! ch)]
                (>! primes prime)
                (recur (remove< (partial factor? prime) ch)))
            (close! primes))
        primes))
```

Here is how we call this function:
```
(defn -main [seconds]
    (let [primes (get-primes)
          limit (timeout (* (edn/read-string seconds) 1000))]
        (loop []
            (alt!! :priority true
                limit nil
                primes ([prime] (println prime) (recur))))))
```

Here we use blocking version of alt!, which blocks until either a new prime is available or limit hits, in which case it simply return nil without recur.

The :priority true option ensures that the clauses passed to alt!! are evaluated in order (by default, if two clause both meet, one is chosen randomly)

This avoids the event of primes being generated so quickly that there’s always one available and the timeout clause never gets evaluated.

##### Asynchronous Polling

It seems simple enough to implement a timely polling
```
(defn poll-fn [interval action]
    (let [seconds (* interval 1000)]
        (go (while true
            (action)
            (<! (timeout seconds))))))

polling.core=> (poll-fn 10 #(println "Polling at:" (System/currentTimeMillis)))
#<ManyToManyChannel clojure.core.async.impl.channels.ManyToManyChannel@6e624159>
polling.core=>
Polling at: 1388827086165
Polling at: 1388827096166
Polling at: 1388827106168
............
```

But the problem is, the action can't call parking functions
```
polling.core=> (def ch (to-chan (iterate inc 0)))
#'polling.core/ch

polling.core=> (poll-fn 10 #(println "Read:" (<! ch)))
Exception in thread "async-dispatch-1" java.lang.AssertionError:
Assert failed: <! used not in (go ...) block
nil
```

As the error says: The problem is that parking calls need to be made directly within a go block.

Fix it with Clojure's macro
```
(defmacro poll [interval & body]
    `(let [seconds# (* ~interval 1000)]
        (go (while true
            (do ~@body)
            (<! (timeout seconds#))))))
```

Like c/cpp, the macro is directly replaced into the code level, so the action body will be put directly within the go block.

- The backtick (`) is the syntax quote operator. It takes source code and, instead of executing it, returns a representation of it that can be subsequently compiled.
- Within that code, we can use the ~ (unquote) and ~@ (unquote splice) operators to refer to arguments passed to the macro.
- The # (auto-gensym) suffix indicates that Clojure should automatically generate a unique name to avoid name conflict.

Let's see it in action
```
polling.core=> (poll 10
            #_=> (println "Polling at:" (System/currentTimeMillis))
            #_=> (println (<! ch)))
#<ManyToManyChannel clojure.core.async.impl.channels.ManyToManyChannel@1bec079e>
polling.core=>
Polling at: 1388829368011
0
Polling at: 1388829378018
1
..........
```


### A Practical Example -- Asynchronous IO

##### Basic Http Get Url

```
(require '[org.httpkit.client :as http])

(defn http-get [url]
    (let [ch (chan)]
        (http/get url (fn [response]
                        (if (= 200 (:status response))
                            (put! ch response)
                            (do (report-error response) (close! ch)))))
        ch))
```

- httpkit is an asynchronous IO library, and http/get expects a callback function to run when GET request is processed.
- put! doesn't have to be called within a go block, and implements a 'fire and forget' write to a channel (so, it neither block nor park)
- This func creates a channel, calls http/get, which return immediately. When GET completes, callback is called, which put response in channel if OK, report error otherwise.
- Return the channel at last.

##### Feed polling

With http-get & poll, we can do below:
```
(def poll-interval 60)

(defn poll-feed [url]
    (let [ch (chan)]
        (poll poll-interval
            (when-let [response (<! (http-get url))]
                (let [feed (parse-feed (:body response))]
                    (onto-chan ch (get-links feed) false))))
        ch))
```

parse-feed & get-links functions use the Rome library to parse XML returned by the news feed response, and I will not discuss them here.

The list of links returned by get-links is written to ch with onto-chan, and the last argument is set to false to not to auto-close chanenl by onto-chan.

Normally onto-chan will close ch when the source is exhausted, we disable this behavior by passing false to the final argument.

##### Unique Links

poll-feed function could return duplicate urls. We need a channel that contains just the new links.
```
(defn new-links [url]
    (let [in (poll-feed url)
          out (chan)]
        (go-loop [links #{}]
            (let [link (<! in)]
                (if (contains? links link)
                    (recur links)
                    (do
                        (>! out link)
                        (recur (conj links link))))))
        out))
```

Use a temporary dict variable link (initialized to empty set #{}) to check whether a link is a new one. If so, do nth; otherwise, put link into out chan and dict.

##### Word Counting

```
(defn get-counts [urls]
    (let [counts (chan)]
        (go (while true
            (let [url (<! urls)]
                (when-let [response (<! (http-get url))]
                    (let [c (count (get-words (:body response)))]
                        (>! counts [url c]))))))
        counts))
```

This code will return a channel whose elements are pairs of url and its word-count.

##### Put it all together

```
(defn -main [feeds-file]
    (with-open [rdr (io/reader feeds-file)]
        (let [feed-urls (line-seq rdr)
              article-urls (doall (map new-links feed-urls))
              article-counts (doall (map get-counts article-urls))
              counts (async/merge article-counts)]
            (while true
                (println (<!! counts))))))
```

- with-open opens and reads a file containing a list of news-feed urls, one on each line, and ensure the file will e closed safely. 
- line-seq convert file content into feed-urls list.
- Mapping new-links over feed-urls, turn it into a sequence of channels, each of which contains links to new articles.
- Mapping get-counts over that channel sequence, give us a sequence of channels, each of which contains word-counts of the links.
- Finally, async/merge merge this sequence of channels into a single channel that contains anything written to any of its source channels.

```
file content ==> 
    [feed1, feed2, ...] ==> 
        [(link11, link12, ..), (link21, link22, ...), ..] ==> 
            [([link11, count11], [link12, count12], ..), ([link21, count21], ..), ..] ==>
                final result after merging
```

The code loops forever, printing anything that's written to that merged channel.
