---
title: Docker in Practice
date: TODO
tags: [Docker,笔记]
categories: Docker
---

Some tips on Docker in Practice

<!-- more -->

### Tips

- docker diff 命令可以查看容器启动后修改了哪些文件
- Docker 安装后，其实在机器上安装了 daemon 和 client 两个程序
    + client 用于接收用户的命令行输入，并传给 daemon
    + daemon 真正维护 docker image/container/registry 等相关的内部逻辑
    + client 和 daemon 之间的调用竟然是 HTTP RESTFUL API，daemon 和 DockerHub/其他Registry 之间当然也是 HTTP
