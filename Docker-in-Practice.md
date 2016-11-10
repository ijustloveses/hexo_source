---
title: Docker in Practice
date: TODO
tags: [Docker,笔记]
categories: Docker
---

Some Notes on 《Manning Docker in Practice》

<!-- more -->

Docker fundamentals
=====================

### docker diff 

docker diff 命令用于查看容器启动后修改了哪些文件

### docker daemon & client

Docker 安装后，其实在机器上安装了 daemon 和 client 两个程序
- client 用于接收用户的命令行输入，并传给 daemon
- daemon 真正维护 docker image/container/registry 等相关的内部逻辑
- client 和 daemon 之间的调用竟然是 HTTP RESTFUL API，daemon 和 DockerHub/其他Registry 之间当然也是 HTTP

### 允许 Docker Daemon 被远程访问

默认 Docker 只能本地访问，这是因为 Docker Daemon 和 Docker Client 是通过 /var/run/docker.sock 本地文件 sock 来通信的

Docker Client 也可以通过 TCP Socket 来访问 Docker Daemon，不过此时 Docker Daemon 暴露在网络中，处于不安全的状态，需谨慎使用这种情况

- 关掉当前 Docker Daemon
```
$ sudo service docker stop 或者
$ systemctl stop docker
```
使用下面的命令确认没有任何 Docker Daemon 输出
```
ps -ef | grep -E 'docker (-d|daemon)\b' | grep -v grep
```

- 通过 TCP Socket 启动新的 Docker Daemon (故此不能使用 service docker start 这样的命令来启动)
```
$ docker daemon -H tcp://0.0.0.0:2375    # 如果不行，试试 [-d] 选项
```

- Docker Client 连接
```
$ docker -H tcp://<your host's ip>:2375
```

### 容器运行为后台服务

通常 Linux 下的 Daemon 服务程序都是通过 nohup xxxx & 这种方式来实现的；不过，docker run 提供了 -d 选项，使得容器可以轻松运行为 Daemon

Docker run 命令提供 --restart 选项控制服务失败的后处理

- --restart=no  容器退出后不重启
- --restart=always  容器退出后总是重启
- --restart=on-failure[:max-retry]  容器因为失败退出后重启，如果给定数字，那么只重启有限次数

### 设置 Docker Storage 的位置

1. 停止当前 Docker Daemon 服务
2. docker daemon -g <新的 Storage 目录>

你会发现之前 Docker 的镜像和容器全部都消失掉了，没有关系，只要你杀掉调整位置后的 Docker 服务，再以通常的方式重启 Docker，一切恢复原样
