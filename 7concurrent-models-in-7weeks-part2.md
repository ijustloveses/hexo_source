---
title: 7周7并发模型 P2 - FP with Clojure
date: 2016-06-29 02:27:25
tags: [并发,FP,Clojure,7日7并发模型,笔记]
categories: 并发
---

Notes on 7 Concurrent Models in 7 Weeks - Part 2. Functional Programming with Clojure

The rules about locking apply only to data that is both shared between threads and might change, in other words shared mutable state.

Functional programs have no mutable state, so they cannot suffer from any of the problems associated with shared mutable state.

<!-- more -->

### The Perils of Mutable State

**Hidden Mutable State**
``` java
class DateParser {
    private final DateFormat format = new SimpleDateFormat("yyyy-MM-dd");
    public Date parse(String s) throws ParseException {
        return format.parse(s);
    }
}
```

It looks like thread-safe, but it is **NOT**, because SimpleDateFormat() has mutable state buried deep within.

In Java or C, there’s no way to tell from its API that SimpleDateFormat isn’t thread-safe.


**Escapologist Mutable State**
``` java
public class Tournament {
    private List<Player> players = new LinkedList<Player>();
    public synchronized void addPlayer(Player p) {
        players.add(p);
    }
    public synchronized Iterator<Player> getPlayerIterator() {
        return players.iterator();
    }
}
```

It looks like thread-safe, single private variable protected by synchronized functions, but it is **NOT**.

The iterator returned by getPlayerIterator() still references the mutable state contained within players.

If another thread calls addPlayer() while the iterator is in use, we’ll see a ConcurrentModificationException or worse. 


### Parallelism with Clojure

##### different version of sum

recursive version
```
(defn recursive-sum [numbers]
    (if (empty? numbers)
        0(
        + (first numbers) (recursive-sum (rest numbers)))))
```

full reduce version
```
(defn reduce-sum [numbers]
    (reduce (fn [acc x] (+ acc x)) 0 numbers))
```

simple reduce version
```
(defn sum [numbers]
    (reduce + numbers))
```

parallel version
```
(ns sum.core
    (:require [clojure.core.reducers :as r]))
(defn parallel-sum [numbers]
    (r/fold + numbers))
```

performance compare between sum & parallel-sum
```
sum.core=> (def numbers (into []  (range 0 10000000)))
sum.core=> (time (sum numbers))
"Elapsed time: 1099.154 msecs"
49999995000000
sum.core=> (time (sum numbers))
"Elapsed time: 125.349 msecs"
49999995000000
sum.core=> (time (parallel-sum numbers))
"Elapsed time: 236.609 msecs"
49999995000000
sum.core=> (time (parallel-sum numbers))
"Elapsed time: 49.835 msecs"
49999995000000
```

As is often the case with code on JVM, we have to run more than once to give the JIT optimizer a chance to kick in and get a representative time.


##### Counting Words Functionally - sequential version

**Map basis**
```
user=> (def counts {"apple" 2 "orange" 1})
#'user/counts
user=> (get counts "apple" 0)
2
user=> (get counts "banana" 0)           <-- get simply looks up a key in the map and either returns its value or returns a default
0
user=> (assoc counts "banana" 1)
{"banana" 1, "orange" 1, "apple" 2}
user=> (assoc counts "apple" 3)
{"orange" 1, "apple" 3}                  <-- assoc takes a map with a key/value and returns a NEW map with the key mapped to the value.
```

**Frequency**
```
(defn word-frequencies [words]
    (reduce
        (fn [counts word] (assoc counts word (inc (get counts word 0))))           <-- for each word, update frequency from counts and make a new map
        {} words))                                                                 <-- init value = {}
```

And actually clojure has a standard lib called frequencies(), does the exactly same thing.

**get words**
```
(defn get-words [text] (re-seq #"\w+" text))
```

**map && mapcat**
```
user=> (map get-words ["one two three" "four five six" "seven eight nine"])
(("one" "two" "three") ("four" "five" "six") ("seven" "eight" "nine"))

user=> (mapcat get-words ["one two three" "four five six" "seven eight nine"])
("one" "two" "three" "four" "five" "six" "seven" "eight" "nine")
```

**finally, sequential counting function**
```
(defn count-words-sequential [pages]
    (frequencies (mapcat get-words pages)))
```

There might be a problem: If pages are huge, since count-words starts by collating words into a huge sequence, maybe end up running out of memory.

As long as the pages variables (returned by get_pages(), which is not covered in this doc) is LAZY, there will be no problem.


##### Counting Words Functionally - parallel version

**count pages in parallel**

```
(pmap #(frequencies (get-words %)) pages)
```

1. pmap function is applied in parallel and semi-lazy, in that the parallel computation stays ahead of the consumption, but it won’t realize result unless required.
2. \#(…) reader macro is short for an anonymous function. Arguments are specified with %1, %2, ..., and % if it takes only a single argument


**merge maps with specific merge function**

```
user=> (def merge-counts (partial merge-with +))
#'user/merge-counts
user=> (merge-counts {:x 1 :y 2} {:y 1 :z 1})
{:z 1, :y 3, :x 1}
```

**put them together**

```
(defn count-words-parallel [pages]
    (reduce (partial merge-with +)
        (pmap #(frequencies (get-words %)) pages)))
```

**performance**

The sequential version takes 140 seconds to count 100,000 pages while the parallel version takes 94 s -- a 1.5x speedup. Not very Ideal, why ?

We’re counting and merging on a page-by-page basis, which results in a large number of merges. We can reduce those merges by counting batches of pages instead of a single page at a time.


##### Counting Words Functionally - batch parallel version

**100 pages at a time**

```
(defn count-words [pages]
    (reduce (partial merge-with +)
        (pmap count-words-sequential (partition-all 100 pages))))
```

This version counts the same 100,000 pages in forty-four seconds -- a 3.2x speedup. Perfect!


##### Fold

```
(defn parallel-frequencies [coll]
    (r/fold
        (partial merge-with +)
        (fn [counts x] (assoc counts x (inc (get counts x 0))))
        coll))
```

1. fold - divide and conquer
2. 1st func is the combine function
3. 2nd func is the reduce function
4. Above function doesn't work for word count problem, coz no way to perform binary chop on a lazy sequence (pages)


##### Same Structure, Different Evaluation Order

That is why functional programming allows us to parallelize code so easily.

The following code snippets all perform the same calculation, return the same result, but they execute in very different orders.

```
1.  (reduce + (map (partial * 2) (range 10000)))

2.  (reduce + (doall (map (partial * 2) (range 10000))))

3.  (reduce + (pmap (partial * 2) (range 10000)))

4.  (reduce + (r/map (partial * 2) (range 10000)))

5.  (r/fold + (r/map (partial * 2) (into [] (range 10000))))
```

1. lazy sequence, map & reduce sequentially
2. doall forces a lazy sequence to fully realized, then reduce on it
3. reduces a semi-lazy sequence, which is generated in parallel
4. reduce a single lazy sequence with reduce function constructed by + & (partial*2)
5. into force to realize a full sequence, and then reduce in parallel by r/fold which creates a tree of reduce and combine ops


### Future && Promise 

##### Definition

A future takes a body of code and executes it **in another thread**. Its return value is a future object

```
user=> (def sum (future (+ 1 2 3 4 5)))
user=> sum
#<core$future_call$reify__6110@5d4ee7d0: 15>
```

We can retrieve the value of a future by dereferencing it with either deref or the shorthand @:

```
user=> (deref sum)
15
user=> @sum
15
```

Dereferencing a future will block until the value is available (or realized).


A promise is similar to a future in that it’s a value that’s realized asynchronously and accessed with deref or @, which will block until it’s realized. The difference is that creating a promise does not cause any code to run, instead its value is set with deliver.

```
user=> (def meaning-of-life (promise))
user=> (future (println "The meaning of life is:" @meaning-of-life))
#<core$future_call$reify__6110@224e59d9: :pending>
user=> (deliver meaning-of-life 42)
#<core$promise$reify__6153@52c9f3c7: 42>
The meaning of life is: 42
```


##### Service with Future & Promise

To create a service that accepts data labled by id number, and processes the data sequentially.

Problem is that the data don't arrive at server sequentially.


```
(def snippets (repeatedly promise))

(defn accept-snippet [n text]
    (deliver (nth snippets n) text))

(future
    (doseq [snippet (map deref snippets)]
        (println snippet)))
```

This uses doseq, which processes a sequence sequentially. In this case, the sequence it’s processing is a lazy sequence of dereferenced promises, each one of which is bound to snippet.

