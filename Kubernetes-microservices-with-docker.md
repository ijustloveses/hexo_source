---
title: Kubernetes Microservices with Docker
date: 2016-07-27 14:18:13
tags: [Kubernetes,Docker,Microservice,分布式,笔记]
categories: Docker
---

Some tips on Kubernetes Microservices with Docker

<!-- more -->

### Why Kubernetes

- 便于把多个协作的应用整合为服务
- 便于 Scale 服务
- 解决 Docker 跨机器容器之间的通讯问题

### 基本概念

- Docker 的 Image / Container 不说了
- Node 就是不同的物理/虚拟机器，同分布式中的概念
- Pods 就是相互协作并提供服务的若干容器的组合，Pod 内的容器运行在同一个 Minion 上，视为统一管理单元，共享 volumes & network & ip
- RC (Replication Controllers) 控制 Pods 的 replica 数量，实现 Rescheduling & Scaling.
- 同一个 Node 同一个 Pods 的不同 replica 分别有各自的 network，**提供完全相同的功能** ；看起来就像是多个不同 Nodes 上分别启动了同一个 Pods
- Service 是真实应用服务的抽象，是由 RC 暴露出来的 Endpoints，同一个 Pods 的不同 Replica 分别有各自的 Endpoint
- Pods / RC / Service 通过标签 Labels (key/value pair in nature) 相互识别
- Kubernetes 创建和管理服务的方式有两种：命令式和声明式；声明式采用配置文件，更灵活，更可控，功能也更多

### 集群中使用 Kubernates

先说下 Docker 的 network: 在 1.9 之后，Docker 通过 network 可以使得同一个 Node 节点上的多个容器通过其名字相互识别，相当于多个容器各自有不同的 ip

但是，多个 Nodes 上启动多个容器，能够通过 network 相互识别并协同工作么？目前我还没找到合适的方法。

使用 Kubernetes 集群，并 hack Docker 的默认网络设置，可以达到这个目的！具体步骤简略的说，如下：

- Install Master Kubernetes Node
    + Install Docker
    + Setup bootstrap instance of Docker, 这是指启动一个新的 Docker instance, 方法是指定 -H unix:///xxxxxx.sock，和默认的 Docker Instance 并存
    + 通过新的 Docker Instance 安装 Etcd，一个分布式 key/value Store，用于维护分布式集群的网络信息
    + 停止默认的 Docker Instance，然后通过新的 Docker Instance 安装 Flannel，后者会启动一个网络环境，记录在 /run/flannel/subnet.env
    + 修改默认的 Docker 配置，把 DOCKER_OPT 中的网络配置成 Flannel 启动的网络
    + 安装 bridge-util，并删除 Docker 安装上的默认的 docker0 subnet
    + 重新启动默认的 Docker Instance，那么此时这个 Docker 使用的就是 Flannel 创建的子网络
    + 安装 Kubernetes 及其 Service Proxy
- Install Worker Kubernetes Node，完全类似上面，只是有一些不同
    + 不需要安装 Etcd 了
    + 安装 Flannel 的时候需要指定 Master Ip

至此，Kubernetes 集群安装完毕，可以使用 kubectl get nodes 来确认集群节点都 OK，并可以管理 Pods 和 Services 了

### 评价

这本书 3+ 分吧，不能再多了，基本上就是操作手册，对于熟悉工具还是有一定帮助的，只不过实在是太啰嗦了，全书的知识点有限得很
