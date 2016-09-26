---
title: Functional Programing Principles in Scala
date: TODO
tags: [FP,Scala,sbt,Docker]
categories: FP
---

Notes on [Course](https://class.coursera.org/progfun-2012-001/class/index)

<!-- more -->

Tools Setup
=============

没有采用课程中的安装方法，而是采用了容器化的方法

> docker pull hseeberger/scala-sbt

运行

> docker run -d -v ${absolute_path_in_host}:/root/workspace/scala docker.io/hseeberger/scala-sbt sleep infinity

