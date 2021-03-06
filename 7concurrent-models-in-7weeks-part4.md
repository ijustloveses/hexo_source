---
title: 7周7并发模型 P4 - Actors with Elixir
date: 2016-06-29 02:27:25
tags: [并发,Actor,Elixir,7日7并发模型,笔记]
categories: 并发
---

Notes on 7 Concurrent Models in 7 Weeks - Part 4. Actors with Elixir

<!-- more -->

Functional programming avoids the problems associated with shared mutable state by avoiding mutable state. 

Actor programming, by contrast, retains mutable state but avoids sharing it.

An actor is like an object in an OO program, it encapsulates state and communicates with other actors by exchanging messages.

### Basic: Messages and Mailboxes

##### First Actor

```
defmodule Talker do
    def loop do
        receive do
            {:greet, name} -> IO.puts("Hello #{name}")
            {:celebrate, name, age} -> IO.puts("Here's to another #{age} years, #{name}")
        end
        loop                        --- Note: implements an infinite loop by calling itself recursively
    end
end

pid = spawn(&Talker.loop/0)
send(pid, {:greet, "Huey"})
send(pid, {:celebrate, "Louie", 16})
sleep(1000)
```

- Messages are sent asynchronously. Instead of being sent directly to an actor, they are placed in a mailbox.
- This means that actors are decoupled, actors run at their own speed and don’t block when sending messages.
- An actor runs concurrently with other actors but handles messages sequentially, in the order they were added to the mailbox.

Above sample uses sleeps(1000) for a second to allow messages to be processed before exiting. This is an unsatisfactory solution.

```
defmodule Talker do
    def loop do
        receive do
            {:greet, name} -> IO.puts("Hello #{name}")
            {:celebrate, name, age} -> IO.puts("Here's to another #{age} years, #{name}")
            {:shutdown} -> exit(:normal)        --- Note: an explicit way to stop an actor when it finishes all the messages in its queue.
        end                                               Remember, an actor will handle messages in its queue sequentially.
       loop
    end
end

Process.flag(:trap_exit, true)         --- Note: we’ll be notified when the spawned process terminates.
pid = spawn_link(&Talker.loop/0)                 The message that’s sent is a triple form {:EXIT, pid, reason}

send(pid, {:greet, "Huey"})
send(pid, {:praise, "Dewey"})
send(pid, {:celebrate, "Louie", 16})
send(pid, {:shutdown})

receive do
    {:EXIT, ^pid, reason} -> IO.puts("Talker has exited (#{reason})")            --- Note: block here, until it receives exit message
end
```

The ^ (caret) in the receive pattern indicates that instead of binding the second element of the tuple to pid, we want to match a message where the second element has the value that’s already bound to pid.


##### Stateful Actors
We don't need mutable variables to create a stateful actor, but in fact all we need is recursion.

```
defmodule Counter do
    def loop(count) do
        receive do
            {:next} ->
                IO.puts("Current count: #{count}")
                loop(count + 1)                     --- Note: loop with count + 1
        end
    end
end

iex(1)> counter = spawn(Counter, :loop, [1])        --- Note: initialize count to 1
iex(2)> send(counter, {:next})
Current count: 1
iex(3)> send(counter, {:next})
Current count: 2
```


##### Hiding Messages Behind an API
A common practice is to provide a set of API, and hide details behind the curtain

```
defmodule Counter do
    def start(count) do
        spawn(__MODULE__, :loop, [count])
    end
    def next(counter) do
        send(counter, {:next})
    end
    def loop(count) do
        receive do
            {:next} ->
                IO.puts("Current count: #{count}")
                loop(count + 1)
        end
    end
end

iex(1)> counter = Counter.start(42)
#PID<0.44.0>
iex(2)> Counter.next(counter)
Current count: 42
iex(3)> Counter.next(counter)
Current count: 43
```


##### Bidirectional Communication
What happens if we want to receive a reply? For example, we want Counter actor to return the next number rather than just printing it?

```
defmodule Counter do
    def start(count) do
        spawn(__MODULE__, :loop, [count])
    end
    def next(counter) do
        ref = make_ref()                      --- Note: ref is a unique reference generated by the sender with make_ref(), which is used to ensure that
        send(counter, {:next, self(), ref})             the reply will be correctly identified even if there are multiple messages waiting in the client’s mailbox.
        receive do
            {:ok, ^ref, count} -> count
        end
    end
    def loop(count) do
        receive do
            {:next, sender, ref} ->
                send(sender, {:ok, ref, count})
                loop(count + 1)
        end
    end
end
```


##### Naming Processes
What if we don't know the identifier of the actor to communicate ?

```
iex(1)> pid = Counter.start(42)
#PID<0.47.0>
iex(2)> Process.register(pid, :counter)
true
iex(3)> counter = Process.whereis(:counter)
#PID<0.47.0>
iex(4)> Counter.next(counter)
42
iex(6)> send(:counter, {:next, self(), make_ref()})
{:next, #PID<0.45.0>, #Reference<0.0.0.107>}
iex(7)> receive do msg -> msg end
{:ok, #Reference<0.0.0.107>, 43}
```

Use this to modify Counter’s API so that it doesn’t require a process identifier each time we call it

```
def start(count) do
    pid = spawn(__MODULE__, :loop, [count])
    Process.register(pid, :counter)
    pid
end

def next do
    ref = make_ref()
    send(:counter, {:next, self(), ref})
    receive do
        {:ok, ^ref, count} -> count
    end
end
```


### Parallel Map

##### First class functions

```
iex(1)> Enum.map([1, 2, 3, 4], fn(x) -> x * 2 end)
[2, 4, 6, 8]
iex(2)> Enum.map([1, 2, 3, 4], &(&1 * 2))
[2, 4, 6, 8]
iex(3)> Enum.reduce([1, 2, 3, 4], 0, &(&1 + &2))
10
iex(4)> double = &(&1 * 2)
#Function<erl_eval.6.80484245>
iex(5)> double.(3)                   <-- call anonymous function with the . (apply) operator
6
iex(6)> twice = fn(fun) -> fn(x) -> fun.(fun.(x)) end end        <-- a function that takes an anonymous function as parameter and returns a function
#Function<erl_eval.6.80484245>
iex(7)> twice.(double).(3)             <--- equals (twice.(double)).(3)
12
```

##### Parallel Map Implementation

Enum.map can be used to map a function over a collection sequentially, and here's an alternative that do it in parallel

```
defmodule Parallel do
    def map(collection, fun) do
        parent = self()

        processes = Enum.map(collection, fn(e) ->
            spawn_link(fn() ->                          <-- create a process for each element.
                send(parent, {self(), fun.(e)})         <-- each of processes applies fun to the element and sends result back
            end)                                        <-- self() here is the process, not parent
        end)

        Enum.map(processes, fn(pid) ->                  <-- in parent process, block and wait
            receive do                                  <-- parent wait result sequentially
                {^pid, result} -> result
            end
        end)
    end
end
```

The whole logic is like spawning multiple process to handle multiple elements, and join the result together. Test it:

```
iex(1)> slow_double = fn(x) -> :timer.sleep(1000); x * 2 end
#Function<6.80484245 in :erl_eval.expr/5>
iex(2)> :timer.tc(fn() -> Enum.map([1, 2, 3, 4], slow_double) end)
{4003414, [2, 4, 6, 8]}
iex(3)> :timer.tc(fn() -> Parallel.map([1, 2, 3, 4], slow_double) end)
{1001131, [2, 4, 6, 8]}
```


### Error Handling and Resilience

##### Example of HashDict

```
iex(1)> d = HashDict.new
#HashDict<[]>
iex(2)> d1 = Dict.put(d, :a, "A value for a")           <-- we can see, d is immutable, and Dict.put will create a new HashDict instance
#HashDict<[a: "A value for a"]>
iex(4)> d1[:a]
"A value for a"
```

##### A Cache Actor

```
defmodule Cache do
    def loop(pages, size) do
        receive do
            {:put, url, page} ->
                new_pages = Dict.put(pages, url, page)
                new_size = size + byte_size(page)
                loop(new_pages, new_size)            <-- loop with new pages and size
            {:get, sender, ref, url} ->
                send(sender, {:ok, ref, pages[url]})
                loop(pages, size)
            {:size, sender, ref} ->
                send(sender, {:ok, ref, size})
                loop(pages, size)
            {:terminate} ->                  <-- Terminate request - don't recurse
         end
    end

    def start_link do
        pid = spawn_link(__MODULE__, :loop, [HashDict.new, 0])     <-- init values
        Process.register(pid, :cache)               <-- register a name
        pid
    end

    def put(url, page) do
        send(:cache, {:put, url, page})
    end

    def get(url) do
        ref = make_ref()
        send(:cache, {:get, self(), ref, url})
        receive do
            {:ok, ^ref, page} -> page
        end
    end

    def size do
        ......         <-- just like get()
    end

    def terminate do
        send(:cache, {:terminate})
    end
end
```

##### Actor fails when putting cache invalid data

```
......
iex(5)> Cache.put("paulbutcher.com", nil)         <-- use nil as value to put
{:put, "paulbutcher.com", nil}
iex(6)>
=ERROR REPORT==== 22-Aug-2013::16:18:41 ===
Error in process <0.47.0> with exit value: {badarg,[{erlang,byte_size,[nil],[]} …
** (EXIT from #PID<0.47.0>) {:badarg, [{:erlang, :byte_size, [nil], []}, …
```

How Elixir handle failures? Separating error handling out into a separate supervisor process.

To see how to write such a supervisor, we need to understand links between processes in more detail.

##### Links Propagate Abnormal Termination

```
defmodule LinkTest do
    def loop do
        receive do
            {:exit_because, reason} -> exit(reason)
            {:link_to, pid} -> Process.link(pid)         <-- link two processes
            {:EXIT, pid, reason} -> IO.puts("#{inspect(pid)} exited because #{reason}")
        end
        loop
    end
end

iex(1)> pid1 = spawn(&LinkTest.loop/0)
iex(2)> pid2 = spawn(&LinkTest.loop/0)
iex(3)> send(pid1, {:link_to, pid2})
iex(4)> send(pid2, {:exit_because, :bad_thing_happened})
{:exit_because, :bad_thing_happened}          <--- no message printed by pid1 describing why pid2 exited
iex(5)> Process.info(pid2, :status)
nil
iex(6)> Process.info(pid1, :status)
nil                                           <--- both our processes have terminated, not just pid2.

iex(1)> pid1 = spawn(&LinkTest.loop/0)
iex(2)> pid2 = spawn(&LinkTest.loop/0)
iex(3)> send(pid1, {:link_to, pid2})
iex(4)> send(pid1, {:exit_because, :another_bad_thing_happened})        <-- links are bidirectional
iex(5)> Process.info(pid1, :status)
nil
iex(6)> Process.info(pid2, :status)
nil                                           <--- both our processes have terminated, not just pid1.

iex(1)> pid1 = spawn(&LinkTest.loop/0)
#PID<0.47.0>
iex(2)> pid2 = spawn(&LinkTest.loop/0)
#PID<0.49.0>
iex(3)> send(pid1, {:link_to, pid2})
{:link_to, #PID<0.49.0>}
iex(4)> send(pid2, {:exit_because, :normal})    <--- pid2 exit normally
{:exit_because, :normal}
iex(5)> Process.info(pid2, :status)
nil
iex(6)> Process.info(pid1, :status)
{:status, :waiting}                             <--- normal termination does not result in linked processes terminating.
```

##### System Processes

A process to trap another’s exit by setting its :trap_exit flag, and this is making it into a system process

```
def loop_system do
    Process.flag(:trap_exit, true)
    loop
end

iex(1)> pid1 = spawn(&LinkTest.loop_system/0)
#PID<0.47.0>
iex(2)> pid2 = spawn(&LinkTest.loop/0)
#PID<0.49.0>
iex(3)> send(pid1, {:link_to, pid2})
{:link_to, #PID<0.49.0>}
iex(4)> send(pid2, {:exit_because, :yet_another_bad_thing_happened})
{:exit_because, :yet_another_bad_thing_happened}
#PID<0.49.0> exited because yet_another_bad_thing_happened      <-- pid1 gets :EXIT message
iex(5)> Process.info(pid2, :status)
nil
iex(6)> Process.info(pid1, :status)
{:status, :waiting}
```

##### cache supervisor 

```
defmodule CacheSupervisor do
    def start do
        spawn(__MODULE__, :loop_system, [])
    end

    def loop do
        pid = Cache.start_link        <-- create Cache Actor and link it with spawn_link
        receive do
            {:EXIT, ^pid, :normal} -> 
                IO.puts("Cache exited normally")
                :ok                <-- don't loop when Cache exited normally
            {:EXIT, ^pid, reason} ->
                IO.puts("Cache failed with reason #{inspect reason} - restarting it")
                loop               <-- loop when Cache exited abnormally, and that will recreate Cache Actor
        end
    end

    def loop_system do
        Process.flag(:trap_exit, true)
        loop
    end
end
```

##### Timeout

Automatically restarting the cache is great, but it’s not a panacea. See example below:

1. Process 1 sends a :put message to the cache.
2. Process 2 sends a :get message to the cache.
3. The cache crashes while processing process 1’s message.
4. The supervisor restarts the cache, but process 2’s message is lost.
5. Process 2 is now deadlocked in a receive, waiting for a reply that will never arrive.

Timeout to rescue

```
def get(url) do
    ref = make_ref()
    send(:cache, {:get, self(), ref, url})
    receive do
        {:ok, ^ref, page} -> page
        after 1000 -> nil              <-- timeout here
    end
end
```


### OTP Intro

##### Functions and Pattern Matching

```
defmodule Patterns do
    def foo({x, y}) do
        IO.puts("Got a pair, first element #{x}, second #{y}")
    end

    def foo({x, y, z}) do
        IO.puts("Got a triple: #{x}, #{y}, #{z}")
    end
end

iex(1)> Patterns.foo({:a, 42, "yahoo"})
Got a triple: a, 42, yahoo
iex(2)> Patterns.foo({:x, :y})
Got a pair, first element x, second y
iex(3)> Patterns.foo("something else")
** (FunctionClauseError) no function clause matching in Patterns.foo/1  ......
```

##### Reimplementing Cache with GenServer

```
defmodule Cache do
    use GenServer.Behaviour
    def handle_cast({:put, url, page}, {pages, size}) do
        new_pages = Dict.put(pages, url, page)
        new_size = size + byte_size(page)
        {:noreply, {new_pages, new_size}}
    end

    def handle_call({:get, url}, _from, {pages, size}) do
        {:reply, pages[url], {pages, size}}
    end

    def handle_call({:size}, _from, {pages, size}) do
        {:reply, size, {pages, size}}
    end
end
```

- handle_cast(), handles messages that do not require a reply. 
- It takes two arguments: the first is the message and the second is the current actor state. 
- The return value is a pair of the form {:noreply, new_state}.

- handle_call(), handles messages that require a reply. 
- It takes three arguments, the message, the sender, and the current state. 
- The return value is a triple of the form {:reply, reply_value, new_state}.

- Elixir uses variable names that start with an underscore (“_”) to indicate that they’re unused here, say _from.

wrapper API to call a GenServer
```
def start_link do
    :gen_server.start_link({:local, :cache}, __MODULE__, {HashDict.new, 0}, [])
end

def put(url, page) do
    :gen_server.cast(:cache, {:put, url, page})
end

def get(url) do
    :gen_server.call(:cache, {:get, url})
end

def size do
    :gen_server.call(:cache, {:size})
end
```

##### An OTP Supervisor

```
defmodule CacheSupervisor do
    def init(_args) do
        workers = [worker(Cache, [])]
        supervise(workers, strategy: :one_for_one)
    end
end
```

- init() function is called during startup. It takes a single argument (unused here) and simply creates a number of workers and sets them up to be supervised.
- The OTP supervisor behaviour supports a number of different restart strategies, the two most common being one-for-one and one-for-all.
- If a single worker fails, a supervisor using the one-for-all strategy will stop and restart all its workers (even those that didn’t fail). 
- A supervisor using a one-for-one strategy, by contrast, will only restart the failed worker.

wrapper API
```
def start_link do
    :supervisor.start_link(__MODULE__, [])
end
```

##### Nodes

Whenever we create an instance of the Erlang virtual machine, we create a node. Now we’ll see how to create and connect multiple nodes.

For one node to connect to another, they both need to be named. We name a node by starting the Erlang VM with the --name or --sname options.

create two nodes
```
iex --sname node1@10.99.1.50 --cookie yumyum         <-- an Erlang node will accept connection requests only from nodes that have the same cookie.
iex --sname node2@10.99.1.92 --cookie yumyum
```

from the first node
```
iex(node1@10.99.1.50)1> Node.self                   <-- query its name
:"node1@10.99.1.50"
iex(node1@10.99.1.50)2> Node.list                   <-- list the other nodes it knows about, so 1.50 don't know about 1.92 yet
[]
```

connect nodes
```
iex(node1@10.99.1.50)3> Node.connect(:"node2@10.99.1.92")
true
iex(node1@10.99.1.50)4> Node.list
[:"node2@10.99.1.92"]                               <-- now it knows
iex(node2@10.99.1.92)1> Node.list                   <-- Connections are bidirectional
[:"node1@10.99.1.50"]
```

remote execution
```
iex(node1@10.99.1.50)5> whoami = fn() -> IO.puts(Node.self) end
#Function<20.80484245 in :erl_eval.expr/5>
iex(node1@10.99.1.50)6> Node.spawn(:"node2@10.99.1.92", whoami)
#PID<8242.50.0>
node2@10.99.1.92                   <-- not only has one node executed code on another, but the output appeared on the first node.
```

remote messaging
```
iex(node2@10.99.1.92)1> pid = spawn(Counter, :loop, [42])
#PID<0.51.0>
iex(node2@10.99.1.92)2> :global.register_name(:counter, pid)       <-- similar to Process.register(), except that the name is cluster-global
:yes
iex(node1@10.99.1.50)1> Node.connect(:"node2@10.99.1.92")
true
iex(node1@10.99.1.50)2> pid = :global.whereis_name(:counter)
#PID<7856.51.0>
iex(node1@10.99.1.50)3> send(pid, {:next})
{:next}
iex(node1@10.99.1.50)4> send(pid, {:next})
{:next}
```


### Distributed Word Count

- Our solution is divided into three types of actors: one Parser, multiple Counters, and one Accumulator. 
- The Parser is responsible for parsing a Wikipedia dump into pages.
- Counters count words within pages.
- The Accumulator keeps track of total word counts across pages.

###### Counter -- counting words

```
defmodule Counter do
    use GenServer.Behaviour
    def start_link do
        :gen_server.start_link(__MODULE__, nil, [])
    end

    def deliver_page(pid, ref, page) do                <-- will be called by Parser to reply request_page() request
        :gen_server.cast(pid, {:deliver_page, ref, page})
    end

    def init(_args) do
        Parser.request_page(self())      <-- kicks things off by calling Parser.request_page() during initialization
        {:ok, nil}
    end

    def handle_cast({:deliver_page, ref, page}, state) do
        Parser.request_page(self())          <-- starts by requesting another page
        words = String.split(page)
        counts = Enum.reduce(words, HashDict.new, fn(word, counts) ->
            Dict.update(counts, word, 1, &(&1 + 1))
            end)
        Accumulator.deliver_counts(ref, counts)
        {:noreply, state}
    end
end
```

##### Counter Supervisor

```
defmodule CounterSupervisor do
    use Supervisor.Behaviour
    def start_link(num_counters) do
        :supervisor.start_link(__MODULE__, num_counters)
    end

    def init(num_counters) do
        workers = Enum.map(1..num_counters, fn(n) ->
            worker(Counter, [], id: "counter#{n}")         <-- create num_counters Counters with distince id
        end)
        supervise(workers, strategy: :one_for_one)
    end
end
```

##### Accumulator keeps track of Totals

```
defmodule Accumulator do
    use GenServer.Behaviour
    def start_link do
        :gen_server.start_link({:global, :wc_accumulator}, __MODULE__,       <-- it is global
        {HashDict.new, HashSet.new}, [])           <-- hashdict for total counts & hashset for processed pages
    end

    def deliver_counts(ref, counts) do         <-- called by Counter after it counts the page
        :gen_server.cast({:global, :wc_accumulator}, {:deliver_counts, ref, counts})
    end

    def handle_cast({:deliver_counts, ref, counts}, {totals, processed_pages}) do
        if Set.member?(processed_pages, ref) do       <-- in case that the count are received multi times
            {:noreply, {totals, processed_pages}}
        else
            new_totals = Dict.merge(totals, counts, fn(_k, v1, v2) -> v1 + v2 end)
            new_processed_pages = Set.put(processed_pages, ref)
            Parser.processed(ref)              <-- call this after processing pages
            {:noreply, {new_totals, new_processed_pages}}
        end
    end
end
```

##### Parser - Parsing and Fault Tolerance

```
defmodule Parser do
    use GenServer.Behaviour
    def start_link(filename) do
        :gen_server.start_link({:global, :wc_parser}, __MODULE__, filename, [])    <-- global
    end

    def request_page(pid) do      <-- called by Counter, pid is the id of Counter
        :gen_server.cast({:global, :wc_parser}, {:request_page, pid})
    end

    def processed(ref) do      <-- called by Accumulator after merge page count
        :gen_server.cast({:global, :wc_parser}, {:processed, ref})
    end

    def init(filename) do
        xml_parser = Pages.start_link(filename)
        {:ok, {ListDict.new, xml_parser}}     <-- ListDict for pending pages which have been sent but not yet processed
    end

    def handle_cast({:request_page, pid}, {pending, xml_parser}) do
        new_pending = deliver_page(pid, pending, Pages.next(xml_parser))     <-- implemented below
        {:noreply, {new_pending, xml_parser}}
    end

    def handle_cast({:processed, ref}, {pending, xml_parser}) do
        new_pending = Dict.delete(pending, ref)      <-- if processed, removed from pending pages
        {:noreply, {new_pending, xml_parser}}
    end

    defp deliver_page(pid, pending, page) when nil?(page) do    <-- abnormal workflow when no new pages to sent, in this case send pending pages
        if Enum.empty?(pending) do         <-- no pages pending, do nothing
            pending # Nothing to do
        else
            {ref, prev_page} = List.last(pending)      <-- ref is also saved in pending, so ref will always be the same for the same page
            Counter.deliver_page(pid, ref, prev_page)
            Dict.put(Dict.delete(pending, ref), ref, prev_page)
        end
    end

    defp deliver_page(pid, pending, page) do     <-- normal workflow when there are still pages not sent yet
        ref = make_ref()
        Counter.deliver_page(pid, ref, page)
        Dict.put(pending, ref, page)            <-- not processed yet, so add to pending pages
    end
end
```

From the code, the whole framework works in "At Least Once" strategy for false tolerance, which means the page could be sent to different Counter for multiple times.

To avoid duplicity, the same page is always linked to the same ref, which is kept by Parser's pending page variable and Accumulator's proessed pages variable.

```
Counter (multiple)                                 Parser (Only one global)                      Accumulator (Only one global)
   |   --------- Parser.request_page --------->      |
   |                                                 |
   |   <--- Counter.deliver_page(pid, ref, page) --- |
   |  (could be new page or old page with old ref)   |
   |                                                                                                  |
   |   ----------------------------------------Accumulator.deliver_counts(ref, counts) ------------>  |
   |                                                                                                  |
   |                                                 |  <------- Parser.processed(ref)  ------------  |
   |                                                 |                                                |
```









