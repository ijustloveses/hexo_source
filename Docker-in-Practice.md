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

通常 Linux 下的 Daemon 服务程序都是通过 nohup \<your command> & 这种方式来实现的；不过，docker run 提供了 -d 选项，使得容器可以轻松运行为 Daemon

Docker run 命令提供 --restart 选项控制服务失败的后处理

- --restart=no  容器退出后不重启
- --restart=always  容器退出后总是重启
- --restart=on-failure[:max-retry]  容器因为失败退出后重启，如果给定数字，那么只重启有限次数

### 设置 Docker Storage 的位置

1. 停止当前 Docker Daemon 服务
2. docker daemon -g <新的 Storage 目录>

你会发现之前 Docker 的镜像和容器全部都消失掉了，没有关系，只要你杀掉调整位置后的 Docker 服务，再以通常的方式重启 Docker，一切恢复原样

### 使用 socat 作为 proxy 来监控 Docker API traffic

如果想要调试或者研究 Docker 内部的 API 调用流，可以使用 socat 在 Docker Client 和 Docker Daemon 之间搭一个代理：

> Docker Client <==> Socat Unix Domain Socket Proxy <==> Docker Defaut Unix Domain Socket <==> Docker Daemon

使用如下命令
```
sudo socat -v UNIX-LISTEN:/tmp/dockerapi.sock UNIX-CONNECT:/var/run/docker.sock &
```
-v 使输出可读；UNIX-LISTEN 表示 socat 监听 /tmp/dockerapi.sock；UNIX-CONNECT 表示 socket 连接默认 docker 的 socket，把监听到的请求转发过去；反过来，当接收到 docker 的 response 后，再通过 /tmp/dockerapi.sock 返回给 Docker Client，完成一个成功的请求响应过程

以 docker ps -a 命令为例，Docker Client 的调用方法为：
```
docker -H unix:///tmp/dockerapi.sock ps -a
```

### Linking containers

```
docker run --name wp-mysql -e MYSQL_ROOT_PASSWORD=yoursecretpassword -d mysql
docker run --name wordpress --link wp-mysql:mysql -p 10003:80 -d wordpress
```
注意，第二条命令要在第一条命令执行之后再等一下才能运行，否则第一个容器还没准备好，第二个命令中的 link 就无效了；使用 docker-compose to rescue !

另外，像上面命令中这样的 setup，需要在 Dockerfile 中 EXPOSE 端口号

### Setting up a local Docker registry

```
docker run -d -p 5000:5000 -v $HOME/registry:/var/lib/registry registry:2
```
如果 Docker Client 在 docker push 或者其他访问时出问题，试试看在启动 Docker Daemon 时加入 --insecure-registry HOSTNAME 选项

### 管理容器内服务的启动

通常我们使用 crontab 来控制系统服务的启动，然而这个对于容器来说并不理想，会导致很多问题

相应的，应该使用 Supervisor 来控制服务的启动，通过 pip install supervisor 安装，并通过 /etc/supervisord.conf 配置文件来管理

### docker commit 只能提交文件系统的修改

docker commit 只能提交文件系统的修改，不能保存容器内正在运行中的进程，也不能保存数据库、Docker volumns 等外部依赖组件

### Referring to a specific image in builds

在 build Docker 镜像时，我们都是基于 (FROM) 一个已有的镜像。我们知道，通过镜像 name/repository/tag 都无法真正限制一个镜像保持不变，那么如何让 build 时真正基于一个确定性的镜像呢？

答案是使用镜像 id，比如 FROM 8eaa4ff06b53

更厉害的是，这个镜像 id 甚至不需要有 name/repositary/tag，可以是本地 build 任何镜像时任意步骤所产生的中间镜像 id

### Volumn 的一些注意事项

比如 docker run -v /var/db/tables:/var/data1 -it debian bash

- host 的 /var/db/tables 目录 mount 到容器中的 /var/data1 目录
- 如果 /var/db/tables 或者 /var/data1 目录不存在，那么在容器启动的时候会创建
- 如果 /var/data1 目录本来就在镜像中存在，那么在容器启动时，该目录会被消失，并被重新 mount 到 host 的 /var/db/tables；故此切忌使用容器的关键目录
- Selinux 可能会影响 volumn，比如报错 permission denied

### 如何保留容器中的 Bash History

方法是使用 Volumn 把容器中的 History 文件共享给 host
```
docker run -e HIST_FILE=/root/.bash_history \               # 这里设置 History 文件环境变量
  -v=$HOME/.bash_history:/root/.bash_history \              # 这里把 History 文件 mount 到 host 的 ~/.bash_history 文件
  -ti ubuntu /bin/bash
```
如果想避免把容器的 History 文件和 host 的 History 文件混合在一起，可以把 host 目录设置到另外的地方即可

如果想避免写这么长的 docker run 命令
```
alias dockbash='docker run -e HIST_FILE=/root/.bash_history \
  -v=$HOME/.bash_history:/root/.bash_history
```
然而，这样就得用 dockbash 代替 docker run 了，这样不够好，不够 seamless；

可以在 ~/.bashrc 中添加下面代码，就完美无缝了
```
function basher() {            # 定义函数 basher
  if [[ $1 = 'run' ]]          # 如果首个参数是 run
  then
    shift                      # 移除第一个参数，也就是说去掉 run 参数，剩下的就是 docker run 的其他参数了
    /usr/bin/docker run \      # 运行 docker run，注意使用绝对路径去找 docker
      -e HIST_FILE=/root/.bash_history \
      -v $HOME/.bash_history:/root/.bash_history "$@"        # 配置 History 目录，并指定 docker run 剩下的参数
  else
    /usr/bin/docker "$@"       # 否则，不移除首参数，正常运行原命令；仍然指定 docker 绝对路径
  fi
}
alias docker=basher            # 最后，docker 设置为别名，每次调用 docker 命令就会调用 basher 函数，实现对 docker 的覆盖 
```

最后，推出当前 bash session，重进，更新 History 设置

### Data-Only Container -- Docker 常见模式之一

启动 Data-Only Container
```
$ docker run -v /shared-data --name dc busybox touch /shared-data/somefile
```

- -v /shared-data 由于没有指定 host 目录，故此它只是在容器中创建一个目录，以供 mount 使用。(相当于 Dockerfile 中指定 VOLUMN 指令的值)
- touch /shared-data/somefile 在指定的目录中创建一个文件，然后命令就结束了，也就是说容器退出了(exit)；要注意，即使容器退出，Data-Only 容器仍然会起作用
- 为了减少容器 size，使用了 busybox

通过 --volumns-from 选项，启动 Data-Only Container 的引用容器，自动 mount 上 /share-data 目录
```
docker run -t -i --volumes-from dc busybox /bin/sh
/ # ls /shared-data
somefile
```
当所有 Data-Only 容器的引用容器都退出之后，Data-Only 容器才会清空 Volumn

当存在多个引用容器，尽量使每个容器访问独立的文件或目录，以避免数据损失；也要注意避免名称冲突

### 通过 BitTorrent Sync 让 data container 的 volumn 能够自动和远程数据源同步

远程数据源 host 启动一个 btsync 容器
```
[host1]$ docker run -d -p 8888:8888 -p 55555:55555 --name btsync ctlc/btsync
[host1]$ docker logs btsync
Starting btsync with secret: ALSVEUABQQ5ILRS2OQJKAOKCU5SIIP6A3            <-- key，供客户端连接使用
.................
```

远程数据源 host 启动容器，link btsync 容器，用于控制和配置需要同步的数据源
```
[host1]$ docker run -i -t --volumes-from btsync ubuntu /bin/bash
[host1]$ touch /data/shared_from_server_one          <--- 看到，默认的 mount 目录为 /data/，我们在目录中配置了一个新数据文件
```

类似的，本地 host 启动 btsync 客户端容器用于同步 btsync 服务器容器，再启动一个容器 link 客户端容器，用于读取和更改数据源
```
[host2]$ docker run -d --name btsync-client -p 8888:8888 -p 55555:55555 \
ctlc/btsync ALSVEUABQQ5ILRS2OQJKAOKCU5SIIP6A3             <--- 这里指定了 key，用于连接 btsync 服务器

[host2]$ docker run -i -t --volumes-from btsync-client ubuntu bash     <-- 启动容器 link btsync 客户端
[host2]$ ls /data
shared_from_server_one
[host2]$ touch /data/shared_from_server_two         <--- 创建新文件
[host2]$ ls /data
shared_from_server_one shared_from_server_two
```

回到数据源 host，应该也能看到新建的 shared_from_server_two，从略

简单的说，就是启动 btsync server & client 容器用于同步数据，然后 server & client 所在 host 各启动一个 app 容器，link 对应的 btsync 容器，操纵已经同步的数据

本方法不能保证时间上的可靠性，可能需要等待一段时间以供同步所需，在安全性、可扩展性和性能上，都有一定的局限

### 通过 sshfs 直接 mount 远程 volume

和前面的技巧相比，这个更为直接，不必搞什么同步，直接 mount 远程数据源就好了

本方法需要 root 权限，需要安装 FUSE (Linux’s “Filesystem in Userspace” kernel module)，后者可以根据是否存在 /dev/fuse 来判断

