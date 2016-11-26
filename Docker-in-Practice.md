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
当所有 Data-Only 容器的引用容器都退出之后，Data-Only 容器才会清空 Volumn；然而，这一点并不能保证，经常会看到 /data/docker/volumes/ 目录 (或者其他指定的 docker volumes 目录) 中遗留着历史 volumes 文件，导致空间占满的现象，可以清理如下：
```
docker volume ls -qf dangling=true | xargs -r docker volume rm
或者 docker volume rm $(docker volume ls -qf dangling=true)
```
参考 [Cleaning up docker to reclaim disk space](https://lebkowski.name/docker-volumes/)

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

### 通过 SSHFS 直接 mount 远程 volume

前面的技巧是通过同步来把远程数据源更新到本地，再使用 Data-Only 容器来访问；那么能否直接 mount 远程数据源呢？答案是肯定的，通常有两种方法：NFS 或者 SSHFS。这里先介绍 SSHFS，下面一节介绍 NFS

SSHFS 的方法需要 root 权限，并安装 FUSE (Linux’s “Filesystem in Userspace” kernel module)，后者可以根据是否存在 /dev/fuse 来判断。SSHFS + FUSE kernel 通过内部的 SSH 连接，提供了一套文件系统的标准接口，让你可以正常访问远程文件。由于直连远程文件，故此不会有 local container-level persistence，文件修改都发生在远程数据源。

```
$ docker run -t -i --privileged debian /bin/bash        <-- 如上面所说，需要 root 权限

容器内部
$ apt-get update && apt-get install sshfs        <-- 安装 sshfs
$ sshfs user@host:/path/to/local/directory ${localpath}    <-- 把远程文件数据源 mount 到本地
```
搞定，远程数据源服务器只要有 ssh service 就可以了，基本不需要做额外配置

当然，还可以把 ${localpath} 暴露出去，让本地容器成为 Data-Only 容器，供其他 app 容器 link。总之 ${localpath} 就和本地目录完全同等看待

如果要 unmount，非常简单
```
fusermount -u ${localpath}
```

### 通过 NFS 直接 mount 远程 volume

前面 SSHFS 的方法优点在于配置简单：远程数据源有 SSH 即可；本地需要预先安装 FUSE；本地容器内安装 SSHFS。

NFS 方法的话，很多大些的公司内部都已经在使用 NFS 共享目录，那么可以配置一个容器来安装 NFS 客户端 mount 远程共享目录，同时作为 Data-Only 容器向其他 app 容器提供 volume 数据服务

数据源 host 的配置 (比较简略，详细的需要查询 NFS 文档
```
# apt-get install nfs-kernel-server      <-- 安装 nfs 服务，注意是 root 权限
# mkdir /export && chmod 777 /export && mount --bind /opt/test/db /export      <-- 把数据目录 mount 到 /export

在 /etc/fstab file 文件中加入
/opt/test/db /export none bind 0 0       <-- 重启后仍然保持 mount

在 /etc/exports 文件中加入
/export 127.0.0.1(ro,fsid=0,insecure,no_subtree_check,async)     <-- 配置 NFS
这里限制为本地访问；现实中可以配置能够访问的 ip 段，或者 * 号不设置权限；还可以设置 ro 为 rw 等，配置不同的权限粒度

# exportfs -a
# service nfs-kernel-server restart
```

本地 Data-Only 容器的配置
```
# mount -t nfs 127.0.0.1:/export /mnt     <-- 本例中是本地配置的 NFS 服务，现实中改为真实 ip
# docker run -ti --name nfs_client --privileged -v /mnt:/mnt busybox /bin/true      <-- 注意有 privileged 选项
```

本地其他 app 容器连接
```
# docker run -ti --volumes-from nfs_client debian /bin/bash
```

相比起来，服务器端的配置麻烦，而 Data-Only 容器的配置简单很多，因为 mount 过来后，就可以完全当作本地文件了

### 容器使用 host 的资源

```
docker run -t -i \
-v /var/run/docker.sock:/var/run/docker.sock \     <-- 容器内可以使用 docker 命令，充当 host 的 Docker Daemon
-v /tmp/.X11-unix:/tmp/.X11-unix \                 <-- 容器内可以使用 host 的显示设备来访问 GUI 程序
-e DISPLAY=$DISPLAY \
--net=host --ipc=host \                            <-- 容器内可以使用本地网络和 ipc；--net 默认值为 bridge
-v /opt/workspace:/home/dockerinpractice \
dockerinpractice/docker-dev-tools-image
```

### 查看容器

```
docker inspect 0808ef13d450
docker inspect --format '{{.NetworkSettings.IPAddress}}' 0808ef13d450
docker ps -q | xargs docker inspect --format='{{.NetworkSettings.IPAddress}}' | xargs -l1 ping -c1
```

### 清理容器

一句话：使用 docker stop 而不要用 docker kill

具体的说，docker stop 和 unix 命令 kill 一样，都会给进程发送 TERM 信号；而 docker kill 则是发送 KILL 信号

区别在于，进程收到 TERM 信号后可以做一些 cleanup 工作再退出；而 KILL 信号则强迫进程立即退出，可能导致问题

### Docker Machine

docker-machine 是一个工具，用来方便的创建和管理 docker host。它支持多种 docker host 环境，并提供统一的命令，方便用户的使用 (不同类型的 host 可用命令数会不同，比如 virtualbox 只有 3 个命令，而 openstack 则支持 17 个)

比如创建 virtualbox 环境
```
$ docker-machine create --driver virtualbox host1        <-- 会创建 virtualbox + boot2docker.iso 镜像环境
$ eval $(docker-machine env host1)           <-- 设置默认 docker 环境变量
$ docker ps -a
CONTAINER ID IMAGE COMMAND CREATED STATUS PORTS NAMES    <-- 还没有 docker 容器
$ docker-machine ssh host1                   <-- 登录 host1
```

### Dockerfile 之 ADD

```
$ curl https://www.flamingspork.com/projects/libeatmydata/libeatmydata-105.tar.gz > my.tar.gz

在 Dockerfile 中加入
FROM debian
RUN mkdir -p /opt/libeatmydata
ADD my.tar.gz /opt/libeatmydata/
RUN ls -lRt /opt/libeatmydata

$ docker build --no-cache .
.................
Step 3 : RUN ls -lRt /opt/libeatmydata
---> Running in e3283848ad65
/opt/libeatmydata:
total 4
drwxr-xr-x 7 1000 1000 4096 Oct 29 23:02 libeatmydata-105       <-- 看到 tar.gz 文件自动解压了
.................
```
Docker 会自动解压常见的标准压缩文件，包括并不仅限于 gz, bz2, xz, tar。但是，前提是你已经下载为本地文件了才行，否则不会自动解压，比如

```
FROM debian
RUN mkdir -p /opt/libeatmydata
ADD https://www.flamingspork.com/projects/libeatmydata/libeatmydata-105.tar.gz /opt/libeatmydata/
RUN ls -lRt /opt/libeatmydata
```
这个就不会自动解压

另外，注意到 ADD 的目的目录最后有一个斜杠 /，表示这是个目录；如果没有这个斜杠，那么 Docker 会把 /opt/libeatmydata 当作一个文件名，并把下载的文件保存为这个文件名

如果你就不希望自动解压缩，那么使用 Dockerfile 的 COPY 命令即可

### Docker build 过程中的 cache

在重新 build Docker 镜像的时候，如果 Dockerfile 没有发生改变，那么 Docker 会默认使用 cache，不会真正重新执行命令

如果不希望使用 cache，那么使用 --no-cache 选项，如 
> docker build --no-cache .

如果希望有一些更 fine-grained 的控制呢？比如希望从某个点开始不使用 cache? 可以通过修改 Dockerfile 的方式来实现

Docker 的规则是，只要 Dockerfile 发生了字符级别的改变，那么就从改变发生的位置起，不再使用 cache。注意，字符级别的改变，意味着即使我们只是添加了一条 comment、一个空格，那么也会被认为 Dockerfile 改变，继而不再使用 cache

### Running Docker without sudo

通常用户在前台需要使用 sudo 来调用 docker 命令。如何避免使用 sudo 呢？加入 docker group

> $ sudo addgroup -a username docker

退出 shell 再进，就可以不使用 sudo 了

### 清理 Containers 

清理全部容器
```
$ docker ps -a -q | xargs --no-run-if-empty docker rm -f
```
--no-run-if-empty 选项表示如果前面的命令没有返回，那么就不运行后面的命令； -f 表示即使运行中的容器也 force remove

清理 exited 的容器，既然已经 exited 了，就不需要 -f 了
```
docker ps -a -q --filter status=exited | xargs --no-run-if-empty docker rm
```

查看全部错误退出的容器
```
comm -3 \                  <-- comm 命令比较两个文件内容，-3 选项清除两个文件中都有的行
<(docker ps -a -q --filter=status=exited | sort) \     <-- "<(command)" 会执行命令，并把结果看待为一个文件
<(docker ps -a -q --filter=exited=0 | sort) | \        <-- 得到 exited 和 exited=0 的两个结果文件，作为 comm 参数
xargs --no-run-if-empty docker inspect > error_containers    <-- 两个文件去除重复行后，剩下的就是 exited 非零的容器了
```

### Detaching containers without stopping them

有时运行 docker 容器时，会遇到这种情况：如果退出 shell，那么容器也会跟着退出。如何 detach 而不会 stop 容器呢？

> Press Ctrl-P and then Ctrl-Q to detach.

### 使用 DockerUI 来管理 Docker Daemon

```
$ docker run -d -p 9000:9000 --privileged -v /var/run/docker.sock:/var/run/docker.sock dockerui/dockerui
```

### 生成 Docker images 间的依赖图

```
$ docker run --rm -v /var/run/docker.sock:/var/run/docker.sock dockerinpractice/docker-image-graph > docker_images.png
```

### docker exec 的三种模式

- Basic mode -- Runs the command in the container synchronously on the command line
```
$ docker exec sleeper echo "hello host from container"
```

- Daemon mode -- Runs the command in the background on the container
```
$ docker exec -d sleeper find / -ctime 7 -name '*log' -exec rm {} \;
```

- Interactive mode -- Runs the command and allows the user to interact with it
```
$ docker exec -i -t sleeper /bin/bash
```

### ENTRYPOINT v.s. CMD

例如一个脚本 clean_log 用于清理 N 天没有变动过的文件，天数 N 为传入参数
```
#!/bin/bash
echo "Cleaning logs over $1 days old"
find /log_dir -ctime "$1" -name '*log' -exec rm {} \;
```

容器化
```
FROM ubuntu:14.04
ADD clean_log /usr/bin/clean_log
RUN chmod +x /usr/bin/clean_log
ENTRYPOINT ["/usr/bin/clean_log"]
CMD ["7"]
```
这里面有几个重点需要注意：

- 清理的目标目录 /log_dir，这个是在 docker run 的时候，通过 -v 选项动态 mount 即可
- 当 Dockerfile 中同时具有 ENTRYPOINT 和 CMD 的时候，CMD 定义为 ENTRYPOINT 的默认参数
- 当容器运行时，如果有 ENTRYPOINT，那么一定会被运行；此时如果 docker run 还提供了 command 参数，那么这个 command 不会执行，而是作为 ENTRYPOINT 的参数，替代 CMD 指令中的参数
- 如果就不要 ENTRYPOINT，唯一的办法是使用 docker run 的 --entrypoint 选项
- 无论 ENTRYPOINT 还是 CMD 都采用了 array 模式，而不是 shell 命令模式，这是因为后者会自动加入 /bin/bash -c 前缀，也许有时这是你需要的，但是大部分情况下可能会引起未知的结果，故此倾向于 array 模式

比如：
```
docker build -t log-cleaner .
docker run -v /var/log/myapplogs:/log_dir log-cleaner 365
```
这个 docker run 的 command 参数 365 成为了 ENTRYPOINT 的参数；故此如果把 365 改为 /bin/bash，是不会运行 bash 

shell的，而是把 '/bin/bash' 作为 clean_log 的参数，这显然会报错 
> find: invalid argument `-name' to `-ctime'

### 保证镜像中的软件版本 (for debian based images)

例如 nginx
```
$ apt-cache show nginx | grep ^Version:
Version: 1.4.6-1ubuntu3
```
于是 Dockerfile 中可以指定版本
```
RUN apt-get -y install nginx=1.4.6-1ubuntu3
```

然而依赖库怎么办呢？
```
$ apt-cache --recurse depends nginx
```
通过 --recurse 参数迭代检测依赖软件的版本，然后一一指定在 Dockerfile 中

似乎有些麻烦啊？作者提供了一个容器来简化
```
$ docker run -ti dockerinpractice/get-versions vim
RUN apt-get install -y \
vim=2:7.4.052-1ubuntu3 vim-common=2:7.4.052-1ubuntu3 \
vim-runtime=2:7.4.052-1ubuntu3 libacl1:amd64=2.2.52-1 \
..........
```
看到，docker run 中指定需要处理的软件，容器运行之后会输出 vim 自身及所有依赖的软件的全部版本

### Dockerfile 中要替换多个文件中的文本？使用 perl -pie

sed -i 也可以做类似的替换工作，那么为什么要使用 perl -pie 呢？

- 天生可以作用于多个文件，一个文件即使处理失败也不会异常退出
- 可以使用其他的符号代替通常的 '/' forward slashes 符号

```
$ perl -p -i -e 's/127\.0\.0\.1/0.0.0.0/g' *       <-- 通配符，处理多个文件

$ perl -p -i -e 's/\/usr\/share\/www/\/var\/www\/html/g' /etc/apache2/*
$ perl -p -i -e 's@/usr/share/www@/var/www/html/@g' /etc/apache2/*     <-- 这个和上面的一样，但是使用 @ 代替 /
```

### Flattening images

有时 Dockerfile 中会涉及一些隐私信息或者重要信息文件不想泄漏，那么在 Dockerfile 的最后几步把这些文件删除，有用么？

没有！因为 Docker 中镜像是分层的，这些文件在比较老的 layers 中依旧存在，只是在删除之后的 layers 中不存在而已

可以通过 docker history 命令查看 layers 信息，然后对老的 layers 调用 docker run 导出隐私文件
```
$ docker history mysecret
..........
$ docker run 5b376ff3d7cd cat /tmp/secret_key
My Big Secret
```

如何解决这个问题？
```
$ docker run -d mysecret /bin/true
28cde380f0195b24b33e19e132e81a4f58d2f055a42fa8406e755b2ef283630f
$ docker export 28cde380f | docker import - mysecret     <--- 先 export 在 import，去掉 layers 信息
$ docker history mysecret
IMAGE CREATED CREATED BY SIZE             <--- 看到最后只有一个 layer了
fdbeae08751b 13 seconds ago 85.01 MB
```

### 让镜像更小之 Dockerfile 篇

- FROM 指令选择比较小的 base 镜像，虽然这意味着会少一些软件，只要保证需要的软件都在即可；甚至使用 BusyBox 或 Alpine
- 镜像构建完成之前，删除不再需要的中间文件或者源软件安装包。需要指出，这些删除也需要使用 RUN 指令调用，而由于 Docker 的 layer 机制，每个 RUN 指令都会在最终的镜像上添加一个 copy-on-write layer，这会间接的增大最终镜像，故此，可能最终镜像看起来并没有减少
- 紧跟前一条，显然我们需要减少 RUN 指令的个数。一种方法是把所需要的命令都放到同一个 RUN 指令中；另一种方法是把命令写到一个 shell 脚本中，然后在 Dockerfile 中 RUN 这个脚本即可

Side Note 1. 把全部命令集中到同一个 RUN 指令中的方法，在优化了镜像空间的同时，牺牲了 Docker layer 带来的 build flexibility 以及 build time (layer cache)

Side Note 2. 什么是 copy-on-write? 这是 Docker 最小化资源使用的重要方法，当多个容器都要读取同一个文件时，他们都会去包含该文件的 topmost layer 去找，也就是说，读取的是同一个文件，这就避免的把这个文件 copy 到每个容器中，极大的减小了容器的体积。只有当某个容器需要修改文件时，该文件才被 copy 到该容器中。

### 让镜像更小之 Tricks 篇

- 通常在 Dockerfile 中 RUN 的 apt-get/yum 等命令并不会做清理工作，那么我们这样做

> build 镜像 => 运行容器 => 进容器删除不需要的文件 => docker commit 为新的镜像 => flatten 镜像 (export再import)

其中，删除部分通常包括
```
dpkg -l | awk '{print $2}' 查看安装包
apt-get purge -y ${unnecessary_package_name} (如果警告 potentially harmful，就返回继续其他 package)
apt-get autoremove && apt-get clean  来清理 cache
rm -rf /usr/share/doc/* /usr/share/man/* /usr/share/info/*  来删除文档
find /var | grep '\.log$' | xargs rm -v  来删除 logs
```

### 让镜像更小之 Nuclear 武器篇 - inotifywait

nifty 工具可以监控哪些文件被容器引用，然后清除掉它们；这个方法有很大风险，尽量不要应用到生产环境；但是能让我们更好的了解系统
```
$ apt-get update && apt-get install -y inotify-tools
$ inotifywait -r -d -o /tmp/inotifywaitout.txt /bin /etc /lib /sbin /var
```
-r 递归查看子目录； -o 输出文件

### 使用 C 或者 Go 创建没有外部依赖的 Docker 镜像 

C、Go 等编译语言可以制作 statically linked binary，比如

- gcc -static hi.c -w -o hi
- CGO_ENABLED=0 go get -a -ldflags '-s' -installsuffix cgo github.com/docker-in-practice/go-web-server

对于没有外部依赖的镜像，可以使用 scratch (空镜像) 作为 base 镜像，ADD binary，然后 CMD/ENTRYPOINT binary 即可

### DEBIAN_FRONTEND=noninteractive apt-get install -qy ${package}

保证安装过程不提示输入

