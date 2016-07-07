---
title: 7周7并发模型 P3 - Seperate Identity from State in Clojure Way
date: 2016-06-29 02:27:25
tags: [并发,Clojure,7日7并发模型,笔记]
categories: 并发
---

Notes on 7 Concurrent Models in 7 Weeks - Part 3. Seperate Identity from State in Clojure Way

<!-- more -->

### Atoms and Persistent Data Structures

A pure functional language provides no support for mutable data whatsoever. Clojure, by contrast, provides a number of different types of concurrency-aware mutable variables. Clojure’s mutable variables allow us to handle real-world side effects while
remaining safe and consistent.

##### Atom Intro

An atom is an atomic variable, which is built on top of java.util.concurrent.atomic

```
user=> (def my-atom (atom 42))
#'user/my-atom
user=> (deref my-atom)
42
user=> @my-atom
42

user=> (swap! my-atom inc)
43
user=> @my-atom
43
user=> (swap! my-atom + 2)     --->  + my-atom 2
45

user=> (reset! my-atom 0)
0
user=> @my-atom
0

user=> (def session (atom {}))
#'user/session
user=> (swap! session assoc :username "paul")
{:username "paul"}
user=> (swap! session assoc :session-id 1234)
{:session-id 1234, :username "paul"}
```

##### A Multithreaded Web Service with Mutable State

```
(def players (atom ()))

(defn list-players []
	(response (json/encode @players)))

(defn create-player [player-name]
	(swap! players conj player-name)
	(status (response "") 201))

(defroutes app-routes
    (GET "/players" [] (list-players))
    (PUT "/players/:player-name" [player-name] (create-player player-name)))

(defn -main [& args]
    (run-jetty (site app-routes) {:port 3000}))
```

The embedded Jetty server is multithreaded, so our code will need to be thread-safe.

What happens if one thread adds an entry to the players list while another is iterating over it?

This code is thread-safe because all of Clojure’s data structures and collections are persistent, ectors, maps, and sets ...

**They makes use of structure sharing, makes use of part of the original, and try to avoid copying when necessary**

They provide similar performance bounds to their nonpersistent equivalents in languages like Ruby and Java.

Once a thread has a reference to a data structure, it will see no changes made by any other thread. (works just like Copy On Write)

Persistent data structures separate identity from state.

##### Retries

Atoms can be lockless, internally they make use of the compareAndSet() method in java.util.concurrent.AtomicReference, fast and don’t block.

swap! needs to handle the case where the atom has been changed by another thread in between it generating a new value and trying to change that value.

If that case happens, swap! will retry, discard the value returned by the function and call it again with the atom’s new value.

We saw something very similar to this already when using ConcurrentHashMap

##### validators

```
user=> (def non-negative (atom 0 :validator #(>= % 0)))
#'user/non-negative
user=> (reset! non-negative 42)
42
user=> (reset! non-negative -1)
IllegalStateException Invalid reference state
```

A validator is a function that’s called whenever an attempt is made to change the value of the atom.

The validator is called before the value of the atom has been changed and, just like the function that’s passed to swap!,

it might be called more than once if swap! retries. Therefore, validators also must not have any side effects.

##### Watchers

```
user=> (def a (atom 0))
#'user/a
user=> (add-watch a :print #(println "Changed from " %3 " to " %4))
#<Atom@542ab4b1: 0>
user=> (swap! a + 2)
Changed from 0 to 2
2
```

A watcher is added by providing both a key and a watch function. The key is used to identify the watcher (to delete etc ...). 

The watch function is called whenever the value of the atom changes. 

It is given four arguments — the key that was given to add-watch, a reference to the atom, the previous value, and the new value.

Watch functions are called after the value has changed and will only be called once, no matter how often swap! retries. So side effect is allowed.

When the watch function is called, the atom's value may already have changed again, so watch functions should always use the values passed as arguments and never dereference the atom.


##### hybrid web server example

**session id**

```
(def last-session-id (atom 0))

(defn next-session-id []
    (swap! last-session-id inc))
```

**session, renew**

```
(def sessions (atom {}))

(defn now []
    (System/currentTimeMillis))

(defn new-session [initial]           <-- it is a hash map
    (let [session-id (next-session-id)
          session (assoc initial :last-referenced (atom (now)))]
    (swap! sessions assoc session-id session)
    session-id))                     <-- return 

(defn get-session [id]
    (let [session (@sessions id)]
        (reset! (:last-referenced session) (now))    <-- renew
        session))                    <-- return
```

**session expire scheduler**

```
(defn session-expiry-time []
    (- (now) (* 10 60 1000)))

(defn expired? [session]
    (< @(:last-referenced session) (session-expiry-time)))

(defn sweep-sessions []
    (swap! sessions #(remove-vals % expired?)))

(def session-sweeper
    (schedule {:min (range 0 60 5)} sweep-sessions))
```

**put snippet into session**

```
(defn create-session []
    (let [snippets (repeatedly promise)
          translations (delay (map translate
                                   (strings->sentences (map deref snippets))))]
        (new-session {:snippets snippets :translations translations})))          <-- snippets and transactions in session
```

Still using an infinite lazy sequence of promises to represent incoming snippets and a map over snippets to represent translations

But these are now both stored in a session, together with the :last-referenced

```
(defn accept-snippet [session n text]
    (deliver (nth (:snippets session) n) text))

(defn get-translation [session n]
    @(nth @(:translations session) n))
```


### gents 

##### Agents Intro
```
user=> (def my-agent (agent 0))
#'user/my-agent
user=> @my-agent
0

user=> (send my-agent inc)
#<Agent@2cadd45e: 1>
user=> @my-agent
1
user=> (send my-agent + 2)
#<Agent@2cadd45e: 1>              <-- still 1, return before agent has been changed by the asynchronous func
user=> @my-agent
3
```

send() returns immediately (before the value of the agent has been changed), the function passed to send is called sometime afterward.

If multiple threads call send concurrently, execution of the functions passed to send is serialized: only one will execute at a time. 

This means that they will not be retried and can therefore contain side effects.

```
user=> (def my-agent (agent 0))
#'user/my-agent
user=> (send my-agent #((Thread/sleep 2000) (inc %)))
#<Agent@224e59d9: 0>
user=> @my-agent
0
user=> (await my-agent)   <-- use await(), which blocks until all actions dispatched from the current thread to the given agent(s) have completed
nil
user=> @my-agent
1
```

##### Error Handling

```
user=> (def non-negative (agent 1 :validator (fn [new-val] (>= new-val 0))))
#'user/non-negative
user=> (send non-negative dec)
#<Agent@6257d812: 0>
user=> @non-negative
0
user=> (send non-negative dec)
#<Agent@6257d812: 0>
user=> @non-negative
0
```

As we hoped, the value won’t go negative. But what happens if we try to use an agent after it’s experienced an error?

```
user=> (send non-negative inc)
IllegalStateException Invalid reference state clojure.lang.ARef.validate…
user=> @non-negative
0
```

Once an agent experiences an error, it enters a failed state by default, and attempts to dispatch new actions fail. 

We can find out if an agent is failed (and if it is, why) with agent-error, and we can restart it with restart-agent:

```
user=> (agent-error non-negative)
#<IllegalStateException java.lang.IllegalStateException: Invalid reference state>
user=> (restart-agent non-negative 0)
0
user=> (agent-error non-negative)
nil
user=> (send non-negative inc)
#<Agent@6257d812: 1>
user=> @non-negative
1
```

By default, agents are created with the :fail error mode. 

Alternatively, you can set the error mode to :continue, in which case you don’t need to call restart-agent to recover an agent.

The :continue error mode is the default if you set an error handler which is automatically called whenever the agent experiences an error.

##### Example: An In-Memory Log

```
(def log-entries (agent []))
(defn log [entry]
    (send log-entries conj [(now) entry]))
```


### Ref - Software Transactional Memory

##### Ref Intro

Refs are more sophisticated than atoms and agents, providing software transactional memory (STM). 

STM allows us to make concurrent, coordinated changes to multiple variables, much like a database's transaction.

```
user=> (def my-ref (ref 0))
#'user/my-ref
user=> @my-ref
0

user=> (ref-set my-ref 42)
IllegalStateException No transaction running       <-- Modifying the value of a ref is possible only inside a transaction.
user=> (alter my-ref inc)
IllegalStateException No transaction running
```

STM transactions are atomic, consistent, and isolated:

- Atomic: Either all of the side effects of a transaction take place, or none of them do.
- Consistent: Transactions guarantee preservation of invariants specified through validators.
    If any of the changes attempted by a transaction fail to validate, none of the changes will be made.
- Isolated: multiple transactions can execute concurrently, the effect of concurrent transactions will be the same as they are running sequentially.

The missing property is durability, STM data will not survive power loss or crashes.

A transaction is created with dosync

```
user=> (dosync (ref-set my-ref 42))
42
user=> @my-ref
42
user=> (dosync (alter my-ref inc))
43
user=> @my-ref
43
```

##### Example - Retry Transactions

```
(def attempts (atom 0))       <-- atom
(def transfers (agent 0))     <-- agent

(defn transfer [from to amount]
    (dosync
        (swap! attempts inc)   // Side-effect in transaction - DON'T DO THIS
        (send transfers inc)
        (alter from - amount)
        (alter to + amount)))
```

stress-tests

```
(def checking (ref 10000))
(def savings (ref 20000))

(defn stress-thread [from to iterations amount]
    (Thread. #(dotimes [_ iterations] (transfer from to amount))))

(defn -main [& args]
    (println "Before: Checking =" @checking " Savings =" @savings)
    (let [t1 (stress-thread checking savings 100 100)
          t2 (stress-thread savings checking 200 100)]
        (.start t1)
        (.start t2)
        (.join t1)
        (.join t2))
    (await transfers)
    (println "Attempts: " @attempts)
    (println "Transfers: " @transfers)
    (println "After: Checking =" @checking " Savings =" @savings))
```

result:

```
Before: Checking = 10000 Savings = 20000
Attempts: 638      --- side effect, break the idea of transaction, a big change that it will change when running the stress-test again
Transfers: 300     --- 638 - 300 = 338 retires happened
After: Checking = 20000 Savings = 10000
```

Good news: agents are transaction-aware.

If you use send to modify an agent within a transaction, that send will take place only if the transaction succeeds.

Clojure uses an exclamation mark to indicate that functions like swap! and reset! are not transaction-safe. 

We can safely update an agent within a transaction because the function that updates an agent’s value is send instead of send!.

##### Atoms, Agents and Refs

An atom allows you to make synchronous changes to a single value, synchronous because when swap! returns, the update has taken place. 

Updates to one atom are not coordinated with other updates.

An agent allows you to make asynchronous changes to a single value, asynchronous because the update takes place after send returns. 

Updates to one agent are not coordinated with other updates.

Refs allow you to make synchronous, coordinated changes to multiple values.


### In Depth - Dining Philosophers Problem

##### Dining Philosophers with STM

```
(def philosophers (into [] (repeatedly 5 #(ref :thinking))))         <-- a ref per philosopher

(defn think []
    (Thread/sleep (rand 1000)))
(defn eat []
    (Thread/sleep (rand 1000)))

(defn philosopher-thread [n]
    (Thread.
        #(let [philosopher (philosophers n)
               left (philosophers (mod (- n 1) 5))
               right (philosophers (mod (+ n 1) 5))]
        (while true
            (think)
            (when (claim-chopsticks philosopher left right)
                (eat)
                (release-chopsticks philosopher))))))

(defn -main [& args]
    (let [threads (map philosopher-thread (range 5))]
        (doseq [thread threads] (.start thread))
        (doseq [thread threads] (.join thread))))

(defn release-chopsticks [philosopher]
    (dosync (ref-set philosopher :thinking)))
```

**A First Attempt**

```
(defn claim-chopsticks [philosopher left right]
    (dosync
        (when (and (= @left :thinking) (= @right :thinking))
            (ref-set philosopher :eating))))
```

This solution is wrong, and the problem is that we’re accessing the values of left and right with @.

STM guarantees that no two transactions will make inconsistent modifications to the same ref, but we’re not modifying left or right, just examining their values.

So, some other transaction could modify them, invalidating the condition that adjacent philosophers can’t eat simultaneously.

**Ensuring ref doesn't change in STM**

```
(defn claim-chopsticks [philosopher left right]
    (dosync
        (when (and (= (ensure left) :thinking) (= (ensure right) :thinking))
            (ref-set philosopher :eating))))
```

ensure ensures that the value of the ref it returns won’t be changed by another transaction. 

It is significantly simpler than lock-based solution, and it’s impossible to deadlock coz it is lockless.

##### Dining Philosophers Without STM

Previous section we represents each philosopher as a ref and using transactions to ensure that updates to those refs are coordinated.

This section we use a single atom to represent the state of all the philosophers as below:

```
(def philosophers (atom (into [] (repeat 5 :thinking))))

(defn philosopher-thread [philosopher]
    (Thread.
        #(let [left (mod (- philosopher 1) 5)
               right (mod (+ philosopher 1) 5)]
        (while true
            (think)
            (when (claim-chopsticks! philosopher left right)
                (eat)
                (release-chopsticks! philosopher))))))

(defn release-chopsticks! [philosopher]
    (swap! philosophers assoc philosopher :thinking))        <-- assoc use be apply to array too          
```

The most interesting function to implement is chaim-chopsticks!

```
(defn claim-chopsticks! [philosopher left right]
    (swap! philosophers
        (fn [ps]
            (if (and (= (ps left) :thinking) (= (ps right) :thinking))
                (assoc ps philosopher :eating)
                ps)))
    (= (@philosophers philosopher) :eating))
```

Works but not elegant. Can we avoid the check after calling swap! to see if the chopsticks are claimed?

```
(defn claim-chopsticks! [philosopher left right]
    (swap-when! philosophers
        #(and (= (%1 left) :thinking) (= (%1 right) :thinking))
        assoc philosopher :eating))
```

```
(defn swap-when!
    "If (pred current-value-of-atom) is true, atomically swaps the value
    of the atom to become (apply f current-value-of-atom args). Note that
    both pred and f may be called multiple times and thus should be free
    of side effects. Returns the value that was swapped in if the
    predicate was true, nil otherwise."
    [a pred f & args]      <-- arr, predictor and arguments (& means any number of arguments)
    (loop []
        (let [old @a]
            (if (pred old)
                (let [new (apply f old args)]
                    (if (compare-and-set! a old new)
                        new
                        (recur)))    <-- if compare-and-set! fails, recur to loop back
                nil))))
```
