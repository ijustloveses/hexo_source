---
title: Docker in Practice - Etcd
date: TODO
tags: [Docker,笔记,Etcd,Orchestration,Deploy,ServiceDiscovery]
categories: Docker
---

Some notes about Docker and Etcd on 《Manning Docker in Practice》

<!-- more -->

Part I. Etcd with Docker
==========================

Etcd 是一个分布式的 key-value 存储工具，通常用于存储配置信息等轻量级 (小于 512 K) 的数据，类似 ZK 和 Consul.

### Etcd Cluster and Etcd proxy setup

首先需要获取外网 ip，以供其他应用访问
```
$ ip addr | grep 'inet ' | grep -v 'lo$\|docker0$'         <-- 过滤掉 lo 和 docker 网络，得到真实 ip
inet 10.194.12.221/20 brd 10.194.15.255 scope global eth0
```

然后启动 Etcd 集群容器
```
$ IMG=quay.io/coreos/etcd:v2.0.10
$ docker pull $IMG
$ HTTPIP=http://10.194.12.221         <-- 外网 ip
$ CLUSTER="etcd0=$HTTPIP:2380,etcd1=$HTTPIP:2480,etcd2=$HTTPIP:2580"   <-- 使用外网 ip 定义集群；由于容器在同一个 host 上，故此分配了不同的 port 以避免冲突

$ ARGS=
$ ARGS="$ARGS -listen-client-urls http://0.0.0.0:2379"    <-- 用于监听和处理 client 的请求
$ ARGS="$ARGS -listen-peer-urls http://0.0.0.0:2380"      <-- 用于和集群内的其他节点相互访问，和集群定义部分一致
$ ARGS="$ARGS -initial-cluster-state new"
$ ARGS="$ARGS -initial-cluster $CLUSTER"     <-- Arguments 中指定了集群定义，而定义里使用了外网 ip

$ docker run -d -p 2379:2379 -p 2380:2380 --name etcd0 $IMG \      <-- 启动第一个节点 etcd0
$ARGS -name etcd0 -advertise-client-urls $HTTPIP:2379 \
-initial-advertise-peer-urls $HTTPIP:2380

$ docker run -d -p 2479:2379 -p 2480:2380 --name etcd1 $IMG \      <-- etcd1，容器内部仍然使用 2379 2380 端口
$ARGS -name etcd1 -advertise-client-urls $HTTPIP:2479 \            <-- 然而要映射到宿主的 2479 和 2480 端口
-initial-advertise-peer-urls $HTTPIP:2480                          <-- 参数中还有宿主的外部 ip 和映射的端口

$ docker run -d -p 2579:2379 -p 2580:2380 --name etcd2 $IMG \      <-- 同 etcd2
$ARGS -name etcd2 -advertise-client-urls $HTTPIP:2579 \
-initial-advertise-peer-urls $HTTPIP:2580
```

!!!! TODO  docker run 的参数中指定的宿主外部 ip 以及映射的端口能被 docker run 正常的识别么？还是说 etcd 不需要识别，只要配置了就行？然而，至少 peer 级别的端口是需要相互访问的啊？那么说一定可以连接到宿主的外部 ip 的喽？？

### 宿主中进行测试

```
$ curl -L $HTTPIP:2579/version
etcd 2.0.10

# put something 
$ curl -L $HTTPIP:2579/v2/keys/mykey -XPUT -d value="test key"
{"action":"set","node": {"key":"/mykey","value":"test key", "modifiedIndex":7,"createdIndex":7}}

# 等待集群同步完成
$ sleep 5

# 杀掉 etcd2
$ docker kill etcd2

# 访问 etcd2 会失败
$ curl -L $HTTPIP:2579/v2/keys/mykey
curl: (7) couldn't connect to host

# 但是刚刚 put 进去的数据应该还在集群中
$ curl -L $HTTPIP:2379/v2/keys/mykey
{"action":"get","node": {"key":"/mykey","value":"test key", "modifiedIndex":7,"createdIndex":7}}

重启 etcd2，数据还会同步回来的，略
$ docker start etcd2
```

### 安装 Etcd proxy

前面看到了要 put 数据等操作，需要知道 etcd 集群的内部服务器 ip 和端口，这很不好；安装 Etcd proxy 来解决
```
$ docker run -d -p 8080:8080 --restart always --name etcd-proxy $IMG \
-proxy on -listen-client-urls http://0.0.0.0:8080 -initial-cluster $CLUSTER 
```

这样，只对这个 proxy:8080 访问就可以了，测试：
```
$ curl -L $HTTPIP:8080/v2/keys/mykey2 -XPUT -d value="t"
{"action":"set","node": {"key":"/mykey2","value":"t", "modifiedIndex":12,"createdIndex":12}}

# etcd1 & etcd2 都删掉
$ docker kill etcd1 etcd2

# 依旧可读，因为 etcd0 还在
$ curl -L $HTTPIP:8080/v2/keys/mykey2
{"action":"get","node": {"key":"/mykey2","value":"t", "modifiedIndex":12,"createdIndex":12}}

# 然而，写入是不可能了，因为 majority 的服务器已经 offline 了
$ curl -L $HTTPIP:8080/v2/keys/mykey3 -XPUT -d value="t"
{"message":"proxy: unable to get response from 3 endpoint(s)"}

# 重启 etcd2，重新可写
$ docker start etcd2
$ curl -L $HTTPIP:8080/v2/keys/mykey3 -XPUT -d value="t"
{"action":"set","node": {"key":"/mykey3","value":"t", "modifiedIndex":16,"createdIndex":16}}
```

### 外部访问 Etcd 集群的应用容器使用 Etcd Proxy 的模式 -- Ambassador

```
$ docker run -it --rm --link etcd-proxy:etcd ubuntu:14.04.2 bash      <-- 其实就是 link 了 proxy
root@8df11eaae71e:/# apt-get install -y wget
root@8df11eaae71e:/# wget -q -O- http://etcd:8080/v2/keys/mykey3
{"action":"get","node": {"key":"/mykey3","value":"t", "modifiedIndex":16,"createdIndex":16}}
```
也就是说，应用服务器通过 link，把 Etcd proxy 服务器作为 Ambassador (proxy) 来访问背后的 Etcd 集群

### 还可以使用 etcdctl Docker 镜像来访问 Etcd proxy

前面，无论是直接访问 Etcd proxy 还是容器内通过 link 访问，都采用了 http 的方式，比较麻烦，命令和参数复杂

可以使用 etcdctl 镜像来简化操作

```
$ IMG=dockerinpractice/etcdctl
$ docker pull dockerinpractice/etcdctl

$ alias etcdctl="docker run --rm $IMG -C \"$HTTPIP:8080\""       <-- 这里通过参数指定了 Etcd proxy 的地址
$ etcdctl set /test value                   <-- 后续访问就简单的多了
value
$ etcdctl ls
/test
```

Part II. Zero-downtime Switchover with Confd
==================================================

当服务升级，需要更改配置和重启时，通常做法无非删除 old 服务器容器，然后启动 new 服务器容器。这很快，但是仍做不到无缝切换

利用 Nginx 的 "reloading config files without dropping connections to the server" 属性，配合 Etcd，可以实现 Web-facing 应用的 zero-downtime 热升级

### 准备工作

- Etcd 集群，按 Part I. 部分安装即可；同时记录宿主外网 ip: HTTPIP=http://10.194.12.221

- 启动一个 python http 服务
```
$ docker run -d --name py1 -p 80 ubuntu:14.04.2 sh -c 'cd / && python3 -m http.server 80'

# 查看 http 服务在宿主机端的端口
$ docker inspect -f '{{.NetworkSettings.Ports}}' py1     <-- 查看 容器所 expose 的端口在宿主机上对应的端口
map[80/tcp:[map[HostIp:0.0.0.0 HostPort:49156]]]

# 查看主页
$ curl -s localhost:49156 | tail
{content of the py1 http server index page}     <-- 内容是列出 / 目录中的文件
```

### 配置 confd-nginx 容器来中转 py1 容器内的 http 服务

首先安装运行 confd-nginx 容器
```
$ IMG=dockerinpractice/confd-nginx
$ docker pull $IMG
$ docker run -d --name nginx -p 8000:80 $IMG $HTTPIP:8080      <-- nginx 监听的端口为 80，映射到宿主的 8000
```

不过此时还没有给 nginx 配置它所要中转的 http 服务，这个配置是 confd-nginx 这个镜像内部固化好的，配置方法如下：
```
$ etcdctl set /app/upstream/py1 10.194.12.221:49156
10.194.12.221:49156
$ sleep 10
```
查看日志
```
$ docker logs nginx
Using http://10.194.12.221:8080 as backend
ERROR 100: Key not found (/app) [14]
......
INFO Target config /etc/nginx/conf.d/app.conf out of sync
Target config /etc/nginx/conf.d/app.conf has been updated       <-- 配置更新

$ curl -s localhost:8000 | tail
{content of the py1 http server index page}     <-- 成功中转了 py1 容器内的 http 服务
```

### 测试热升级

类似 py1 http server，我们再创建一个 py2 http server，作为待切换的新服务
```
$ docker run -d --name py2 -p 80 ubuntu:14.04.2 sh -c 'cd /etc && python3 -m http.server 80'

# 查看 http 服务在宿主机端的端口
$ docker inspect -f '{{.NetworkSettings.Ports}}' py1
map[80/tcp:[map[HostIp:0.0.0.0 HostPort:49161]]]        <-- 和 py1 不同的宿主端口

# 查看主页
$ curl -s localhost:49161 | tail
{content of the py2 http server index page}     <-- 内容是列出 /etc 目录中的文件
```

调用 etcdctl 来更新配置，把中转的服务指向 py2 http server
```
$ etcdctl set /app/upstream/py2 $HTTPIP:49161
10.194.12.221:49161
$ etcdctl ls /app/upstream
/app/upstream/py1
/app/upstream/py2         <-- /app/upstream 中有了两个服务，confd-nginx 镜像配置为选取版本号大的为最新版本
```

查看 http 服务
```
$ curl -s localhost:8000
{content of the py2 http server index page}    <-- 已经更新为 py2 服务的内容了！成功！
```

### 清理工作

作为 two-stage switchover 的最后一步，需要清理 old 服务
```
$ etcdctl rm /app/upstream/py1
$ etcdctl ls /app/upstream
/app/upstream/py2

$ docker rm -f py1
```

### 整个流程的小结

- 用户访问 $HTTPIP:8000，这个端口映射到 confd-nginx 容器的 80 端口
- - confd-nginx 容器中，confd 配置为读取 etcd 集群的 /app/upstream 的最新服务地址，然后把用户请求转发到这个地址
- - 这个地址配置为宿主机的一个端口，如 10.194.12.221:49161；而 49161 端口映射到 python http server 容器的 80 端口
- - 用户的请求发送到 python http server，顺利返回结果

看到，confd-nginx 容器和 python http server 容器之间没有直接关联，是通过 etcd 集群的配置，以及宿主机上映射的端口来发生间接的关联

其实，这个和服务发现也有一些类似，confd-nginx 并不知道具体的服务在哪里，通过 etcd 来间接的找到具体提供服务的容器

