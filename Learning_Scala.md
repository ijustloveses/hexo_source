---
title: First Step on Scala
date: 2016-10-08 09:37:14
tags: [Scala,sbt,Docker]
categories: Scala
---

Some tips while learning scala

<!-- more -->

Env Setup
==========

> docker pull hseeberger/scala-sbt

> docker run -it --rm hseeberger/scala-sbt

然后 docker exec 进到容器里面去；然而，发现该镜像竟然没有 vi，那么可以通过目录映射，在容器外面编辑，在容器内部调试运行

> docker run -d -v ${absolute_path_in_host}:/root/workspace/scala docker.io/hseeberger/scala-sbt sleep infinity


How to run a scala script
============================

参考 [这篇文章](http://www.scala-lang.org/documentation/getting-started.html)

### Scala 命令行中交互运行

```
> scala
This is a Scala shell.
Type in expressions to have them evaluated.
Type :help for more information.

scala> object HelloWorld {
     |   def main(args: Array[String]): Unit = {
     |     println("Hello, world!")
     |   }
     | }
defined module HelloWorld

scala> HelloWorld.main(Array())
Hello, world!

scala>:q
>
```

### 编译运行，类似 java

编译
```
> scalac HelloWorld.scala
```

指定编译目录
```
> scalac -d classes HelloWorld.Scala
```

运行
```
> scala HelloWorld
```

指定 classpath 运行
```
> scala -cp classes HelloWorld
```

通过 scala 命令运行的程序，比如是顶级 scala object，也即满足以下条件中的一个：

1. object 扩展自 App
```
object HelloWorld extends App {
    println("Hello, world!")
}
```

2. 含有 main 函数，如前面命令行交互模式中的那段程序

### 脚本化运行

比如脚本文件 script.sh 如下
```
#!/usr/bin/env scala

object HelloWorld extends App {
    println("Hello, world!")
}
HelloWorld.main(args)
```

那么，命令行下直接调用
```
> ./script.sh
```
