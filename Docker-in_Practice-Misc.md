---
title: Docker in Practice - Misc
date: TODO
tags: [Docker,笔记]
categories: Docker
---

Some miscellenious topics on 《Manning Docker in Practice》

<!-- more -->

Docker Security
=================

### 限制 capabilities

即使你使用非 root 的用户来启动 Docker 容器，你在容器中仍然具有 root 权限。而 Docker 还可以 mount 宿主机的目录，这样就有漏洞了
```
$ docker run -v /sbin:/sbin busybox rm -rf /sbin    # 不要运行这个语句，你会删除 /sbin 目录下的文件，这很危险
$ docker run -v /etc/shadow:/etc/shadow busybox cat /etc/shadow    # 这个命令会展示用户密码文件内容
```

即使两个用户都是 root 用户，他们也会有不同的权限，Linux 通过 capabilities 提供 fine-grained 权限控制。

当容器启动时，如果不加控制，有些 capabilities 是默认打开的，有些再是默认关闭的，比如：
> CHOWN 开启
> DAC_OVERRIDE 开启
> FOWNER 开启
> SYS_CHROOT 开启
> ........
> IPC_LOCK 关闭
> MAC_ADMIN 关闭
> NET_ADMIN 关闭
> ........

一个极端的方案是，在容器启动时，通过 --cap-drop 选项关闭全部 capabilities，然后运行容器，如果不能正常运行，则开启导致错误的那些 capabilities，直到容器正常运行
```
$ docker run -ti --cap-drop=CHOWN --cap-drop=DAC_OVERRIDE \
--cap-drop=FSETID --cap-drop=FOWNER --cap-drop=KILL --cap-drop=MKNOD \
--cap-drop=NET_RAW --cap-drop=SETGID --cap-drop=SETUID \
--cap-drop=SETFCAP --cap-drop=SETPCAP --cap-drop=NET_BIND_SERVICE \
--cap-drop=SYS_CHROOT --cap-drop=AUDIT_WRITE debian /bin/bash
```
最后，还是要说明一下，capabilities 是控制 root 用户如何使用其他用户资源的权限，而 root 用户对其自身的资源仍有完全的控制权限

### 通过 HTTP auth 来访问 Docker 容器

我们通过 -H 命令，使用 http 方式来启动 docker，代理后台的 unix domain socket；然后加入 http authentication，只允许有权限的用户访问 http 接口。所对应的代码在[这里](https://github.com/docker-in-practice/docker-authenticate)

dockerinpractice/docker-authenticate 镜像的 Dockerfile 如下
```
FROM debian
RUN apt-get update && apt-get install -y nginx apache2-utils
RUN htpasswd -c /etc/nginx/.htpasswd username               # 创建用户 username 的密码文件
RUN htpasswd -b /etc/nginx/.htpasswd username password      # 设置密码为 password
RUN sed -i 's/user .*;/user root;/' /etc/nginx/nginx.conf   # nginx 文件中的 user 指定为 root，因为需要 root 权限来访问 docker unix socket
ADD etc/nginx/sites-enabled/docker /etc/nginx/sites-enabled/docker     # 把 etc/nginx/sites-enabled/docker 加到 nginx site 中，后面介绍
CMD service nginx start && sleep infinity                   # 启动 nginx
```

nginx 站点 etc/nginx/sites-enabled/docker 如下
```
upstream docker {
  server unix:/var/run/docker.sock;      # 定义 docker unix socket 为 upstream，名为 docker
}
server {
  listen 2375 default_server;      # 监听 2357
  location / {
    proxy_pass http://docker;      # 把请求转到上面定义的 docker socket
    auth_basic_user_file /etc/nginx/.htpasswd;    # 请求的用户密码文件
    auth_basic "Access restricted";               # 严格执行密码访问
   }
}

```
好了，现在可以启动容器了
```
$ docker run -d --name docker-authenticate -p 2375:2375 \     # 监听 2375 端口
-v /var/run:/var/run dockerinpractice/docker-authenticate     # mount /var/run/docker.sock 所在的目录，以获取 docker daemon 和 socket 的访问权
```

对宿主机 2375 端口的请求，通过端口映射发送给容器 2375 端口的 http 代理，进而转发给容器的 unix:/var/run/docker.sock，通过 mount，这个请求其实就是发送给宿主机的 docker unix socket；经过这一番曲折，实现了 http authentication

现在尝试对这个 web service 访问
```
$ curl http://username:password@localhost:2375/info
{"Containers":115,"Debug":0,
 "DockerRootDir":"/var/lib/docker","Driver":"aufs",
 "DriverStatus":[["Root Dir","/var/lib/docker/aufs"],
 ..........
```

我们看到此时对 docker 的访问，需要使用 restful api 的方式；那么能直接使用 docker 命令么？比如下面这样？
```
$ docker -H tcp://username:password@localhost:2375 ps
```

目前 docker 命令本身还不支持 user/password authentication，原著笔者创建了一个镜像来解决这个问题
```
$ docker run -d --name docker-authenticate-client -p 127.0.0.1:12375:12375 \
dockerinpractice/docker-authenticate-client 192.168.1.74:2375 username:password

$ docker -H localhost:12375 ps    # 问题解决！
```

思考一下这个的实现方式：用户通过 -H 的方式访问 12375 端口，通过端口映射把请求传给容器的 12375 端口；我们知道，Docker 内部其实就是使用 restful api 来通讯的，那么容器 12375 端口的 web service 只需要把 docker 的 restful api 加上 username:password 再转发给前面实现的 http 认证的 web service 即可



### 使用密钥确保 Docker API 的安全

类似 ssh，Docker 也可以通过密钥来保证连接和 API 调用的安全

具体的密钥生成命令就省略了，简单的说，服务器端生成以下密钥文件
```
ca.key.pem / server-key.pem / ca.pem / server-cert.pem / cert.pem / key.pem
```

然后 docker daemon 的配置文件中加入如下选项，并重启
```
DOCKER_OPTS="$DOCKER_OPTS --tlsverify"
DOCKER_OPTS="$DOCKER_OPTS --tlscacert=/etc/docker/ca.pem"
DOCKER_OPTS="$DOCKER_OPTS --tlscert=/etc/docker/server-cert.pem"
DOCKER_OPTS="$DOCKER_OPTS --tlskey=/etc/docker/server-key.pem"
DOCKER_OPTS="$DOCKER_OPTS -H tcp://0.0.0.0:2376"
DOCKER_OPTS="$DOCKER_OPTS -H unix:///var/run/docker.sock"
```

把其中的 ca.pem / cert.pem / key.pem 发布给客户端

然后 Docker 的客户端就可以使用这些密钥安全调用 Docker Server 了
```
$ docker --tlsverify --tlscacert=/etc/docker/ca.pem \
--tlscert=/etc/docker/cert.pem --tlskey=/etc/docker/key.pem \     # 指定密钥文件
-H myserver.localdomain:2376 info       # 通过 -H 选项调用 docker 命令
```

### 使用 MAC tool 比如 Selinux

除了前面介绍的 capabilities 外，还可以用 MAC(mandatory access control) 工具如 Selinux 来做 fine-grained 访问限制

Selinux 是 NSA 开发的工具用来保护其系统，使用于 Red Hat 相关的 linux 系统，而 Debian-based 系统对应工具为 AppArmor

首先需要安装 Selinux，可以通过 sestatus 命令查看系统是否已经安装过 Selinux；然后要 yum 安装 selinuxpolicy-devel 包

运行 sestatus 看看
```
# sestatus
SELinux status: enabled    # 已开启
SELinuxfs mount: /sys/fs/selinux
SELinux root directory: /etc/selinux
Loaded policy name: targeted
Current mode: permissive     # permissive 模式，指 selinux 会记录所有违规行为，但是不会约束它们；很适合测试环境
Mode from config file: permissive      # 如果当前不是 permissive 模式，可以调用 setenforce Permissive 命令设置
Policy MLS status: enabled
Policy deny_unknown status: allowed
Max kernel policy version: 28
```

另外，还要确保 docker daemon 设置了 --selinux-enabled 选项，可以通过下面命令确认
```
ps -fe|grep 'docker -d.*--selinux-enabled'
```

OK，准备工作完毕。以 root 身份创建一个策略文件目录，进入目录创建策略文件，名为 docker_apache.te，如下
```
policy_module(docker_apache,1.0)    # 策略名为 docker_apache，版本 1.0
virt_sandbox_domain_template(docker_apache)   # 此模版创建 docker_apache_t 类型，运行为 docker 容器，初始权限很少
allow docker_apache_t self: capability { chown dac_override kill setgid setuid net_bind_service sys_chroot sys_nice sys_tty_config } ;         # 这里添加新的 capability 权限
allow docker_apache_t self:tcp_socket create_stream_socket_perms;   # 以下添加 Apache 监听的相关网络权限
allow docker_apache_t self:udp_socket create_socket_perms;
corenet_tcp_bind_all_nodes(docker_apache_t)
corenet_tcp_bind_http_port(docker_apache_t)
corenet_udp_bind_all_nodes(docker_apache_t)
corenet_udp_bind_http_port(docker_apache_t)
sysnet_dns_name_resolve(docker_apache_t)
#permissive docker_apache_t    # 可选项，指定 permissive 模式。可在不改变主机模式的情况下，为策略重载主机模式
```

编译策略文件
```
$ make -f /usr/share/selinux/devel/Makefile docker_apache.te     # 这会得到一个 docker_apache.pp 二进制策略文件
Compiling targeted docker_apache module
.........
Creating targeted docker_apache.pp policy package
rm tmp/docker_apache.mod tmp/docker_apache.mod.fc

$ semodule -i docker_apache.pp      # 安装编译好的策略文件
```

试试看
```
$ setenforce Enforcing          # 设置为 enforcing，对违反策略的行为，不仅报错，而且会阻止其运行
$ docker run -ti --name selinuxdock --security-opt \     # 指定了容器运行时应用 docker_apache_t 策略 
  label:type:docker_apache_t httpd                       # 容器的运行将会被阻止，因为违规
.........
Status: Downloaded newer image for httpd:latest
permission denied
$ docker rm -f selinuxdock      # 清理未正常启动的容器
```

研究为何出错
```
$ setenforce Permissive       # 设置会 permissive 模式
$ docker run -d --name selinuxdock --security-opt label:type:docker_apache_t httpd    # 再次运行，应该会记录错误

$ grep -w denied /var/log/audit/audit.log         # 查看错误日志
..........
```

上面会报出很多错误的信息和描述。如果你并不熟悉 selinux，那么研究这些错误并针对性的更新原有策略是很复杂的事情，那么有一个工具帮助你根据错误日志来生成新的策略以修正这些错误，这个工具是 audit2allow
```
mkdir -p /root/selinux_policy_httpd_auto        # 创建新的策略目录，并进入目录
cd /root/selinux_policy_httpd_auto
audit2allow -a -w                               # 通过 -M 选项指明你给新策略所起的名字
audit2allow -a -M newmodname create policy
semodule -i newmodname.pp                       # 生成新的策略文件，并安装之
```


日志 Logging & Monitering
===========================

### 把容器的日志记录在宿主机的 syslog 里

Linux 系统通常都运行着 syslog daemon，应用程序把日志发送到 syslogd 的 touchpoint (/dev/log)，由 syslogd 来可靠的记录和保存日志 (/var/log/syslog)。Docker 容器默认并不安装 syslogd，这不妨碍我们在每个容器中安装和启动 syslogd，不过这样每个容器的日志都保存在各自的文件系统中，我们希望有一个集中的 central location 来保存所有的日志，便于收集和管理

central syslog daemon 容器的 Dockerfile 如下：
```
FROM ubuntu:14.04
RUN apt-get update && apt-get install rsyslog    # syslogd 服务容器安装 rsyslog 软件包，r stands for reliable
VOLUME /dev               # 创建 /dev volume，是 syslogd 的 touchpoint
VOLUME /var/log           # 创建 /var/log volume，mount 宿主机目录，用于集中保存 syslog 日志文件，
CMD rsyslogd -n           # 启动 syslod daemon
```

创建和启动容器
```
docker build -t syslogger .
docker run --name syslogger -d -v /tmp/syslogdev:/dev syslogger     # touch point 映射到宿主 /tmp/syslogdev/log
```

这样，在宿主机上，可以看到 /tmp/syslogdev 目录已经 mount 好了
```
$ ls -1 /tmp/syslogdev/
fd
full
..........
zero
```

现在可以启动应用容器来调用 syslog 记录日志了
```
for d in {1..100}
do
    docker run -d -v /tmp/syslogdev/log:/dev/log ubuntu logger hi_$d      # mount /tmp/syslogdev/log 到 /dev/log
done
```

注意，ubuntu 的 logger 程序会把消息记录到 syslogd 的 touch point /dev/log 文件，进而通过 mount 记录到宿主的 /tmp/syslogdev/log，进而再通过 mount 记录到 central syslogd service 容器的 /dev/log，然后会被 rsyslog 记录到容器的 /var/log/syslog 中去，达成集中收集 syslog 日志的目标
```
$ docker exec -ti syslogger tail -f /var/log/syslog
May 25 11:55:15 f4fb5d829699 logger: hi_1
May 25 11:55:15 f4fb5d829699 logger: hi_2
[...]
May 25 11:57:39 f4fb5d829699 logger: hi_99     # 看到不同应用容器的系统日志都集中记录到 central syslog 服务容器
```

几点说明如下：

- 本节介绍了通过宿主机的同一个目录分别 mount 在 syslog 服务容器和所有应用容器，达到系统日志集中到一起的目标
- /var/log volumne 也可以 mount 到宿主机中去，这样应用容器的日志就可以在宿主机上去管理了
- 使用本技巧的应用容器只能使用 logger 等记录 syslog 的程序，而不能用于集中管理普通的程序日志
- 最后，本技巧仅限于集中管理单宿主机上的应用容器，无法应用于跨宿主机容器的情况
- 这里我忽然有个想法，是不是通过宿主的同一个目录分别去 mount 各容器的 /var/log 目录会更简单，且不限于 syslog 呢？想象中，这个方法可能要解决不同容器中的同名文件的访问冲突问题


### 把 Docker logs 日志记录由宿主的应用程序接管

Docker 提供 docker logs 命令来捕获容器启动程序的日志，而且还提供 --log-driver 选项来指定宿主机的特定应用程序管理 docker logs 日志，包括 syslog / journald / json-file。syslog 在上一节中介绍过了，它使用 /dev/log 作为 touch point 来接收日志并输出到 /var/log/syslog；journald 是一个收集和管理结构化索引日志的系统服务，通过 journalctl 命令来查询日志

把日志输出到 syslog
```
$ docker run --log-driver=syslog ubuntu echo 'outputting to syslog'    # 容器 start 程序为 echo

$ grep 'outputting to syslog' /var/log/syslog        # 查看
```

把日志输出到 journald
```
$ docker run --log-driver=journald ubuntu echo 'outputting to journald'

$ journalctl | grep 'outputting to journald'         # 查看
```

可以设置在 Docker 的默认配置 (/etc/default/docker or /etc/sysconfig/docker) 的 DOCKER_OPTS 中
```
DOCKER_OPTS="--dns 8.8.8.8 --dns 8.8.4.4 --log-driver syslog"
```
这样就不用每次使用 --log-driver 选项来启动容器了

注意，这个方法只对容器启动时执行的 start 程序的日志有效，其他容器内应用的日志不会记录到 docker logs 中，自然无效

另外，还可以使用容器实现一个 ELK logging 框架，好处是对跨宿主机的容器有效


### 使用 cAdvisor 监控容器

你想要监控大量容器的资源使用率、性能？Google 开源的 [cAdvisor](https://github.com/google/cadvisor) 是个不错的选择

启动 cAdvisor
```
$ docker run --volume /:/rootfs:ro \       # 只读 mount 系统 root 目录，以收集文件系统信息
--volume /var/run:/var/run:rw \            # 读写 mount /var/run 目录
--volume /sys:/sys:ro \                    # 只读 mount 系统 sys 目录，以收集 kernel 和 devices 信息
--volume /var/lib/docker/:/var/lib/docker:ro \    # 只读 mount docker 目录，收集 docker 信息
-p 8080:8080 -d --name cadvisor \          # 8080 web interface
--restart on-failure:10 google/cadvisor    # 失败自动重启，共计 10 次
```

登录 http://localhost:8080 可以查看宿主、容器的 cpu、内存等信息；数据保存在系统中，也可以配置保存在 InfluxDB


Resource Control
===================

Docker 使用 cgroups 来管理容器资源使用，默认的策略是简单的平均分配算法，然而有时这样是不够的

### 限制容器运行在特定的 cpu cores 上

默认的，Docker 可以执行在宿主的任何 cpu cores 上，容器如果是单进程的，那么只会占用一个 core，而如果是多线程的，那么可以使用所有的 cores；而 Docker 还可以通过 --cpuset-cpus 硬性指定容器运行在哪个 cores 上

运行两个容器
```
docker run ubuntu:14.04 sh -c 'cat /dev/zero >/dev/null'
```
你会发现两个 cpu cores 占用率 100%

如果这样运行两个容器
```
docker run --cpuset-cpus=0 ubuntu:14.04 sh -c 'cat /dev/zero >/dev/null'
```
你会发现第一个 core 占用率 100%，因为两个容器全部运行在第一个 core 上

--cpuset-cpus 选项允许指定多个 cores，格式如 (0,1,2)，或者 (0-2)，或者 (0-1,3)

### 给重要的容器更多的 cpu 资源

在 docker run 启动容器时，可以使用 -c/--cpu-shares 选项来分配容器对 cpu 资源的优先级；如果不指定，那么默认为 1024。要注意，当只有一个容器时，那么它一定 100% 占有 cpu 资源，不管 -c/--cpu-shares 选项的值是多少。-c/--cpu-shares 选项只有当多个容器竞争 cpu 资源时，才发生作用。特别的，假如两个容器都是单线程应用，运行在 2-cores 宿主上，那么无论它俩的 -c/--cpu-shares 值是多少，都只会各占 1 个 core

假如现在有 A,B,C 3 个容器，那么

- 都不设置 -c/--cpu-shares，那么每个容器 1/3 
- A & B 512, C 1024，那么 A & B 各占 1/4，C 占 1/2
- A 10, B 100, C 1000，那么 A 占不到 1%，B 占不到 10%，C 占不到 90%

### 限制容器内存的使用

当容器运行时，可以分配宿主机上全部的内存；同时，我们还可以通过 -m/--memory 选项限制容器能分配的内存

注意，对于 Ubuntu 系统，这个 capability 并不是默认 enable 的，可以调用 docker info 查看是否有 "No swap limit support" 警告，如果有那么就需要做一些设置，让 kernel 知道在系统启动时 enable memory-limiting capability。具体如下：
```
# 修改 /etc/default/grub 文件，加入
GRUB_CMDLINE_LINUX="cgroup_enable=memory swapaccount=1"

$ sudo update-grub
```
然后重启系统即可

接下来可以做一些测试了
```
$ docker run -it -m 4m ubuntu:14.04 bash        # 限制内存分配最大 4m
root@cffc126297e2:/# python3 -c 'open("/dev/zero").read(10*1024*1024)'    # 尝试通过 python 分配 10m
Killed                                          # 脚本运行失败
root@e9f13cacd42f:/# A=$(dd if=/dev/zero bs=1M count=10 | base64)         # 尝试通过命令行分配 10m
$                                               # Bash 被 killed，容器退出，回到宿主机的 Bash
$ echo $?
137                                             # 返回错误码 137
```

接下来的测试使用 jess/stress 镜像中的 stress 工具，来测试系统的极限
```
$ docker run -m 100m jess/stress --vm 1 --vm-bytes 150M --vm-hang 0
```
上面的命令指定容器内存分配 limit 为 100m，容器启动时会使用 stress 分配 150m，会失败么？答案是不会

可以使用下面的命令来验证 stress 确实分配了 150m 内存
```
docker top <container_id> -eo pid,size,args
```
那么，为什么没有失败呢？原来 Docker 会 double-reserve 内存，其中一半为实际物理内存，一半为 swap；故此，-m 100m 选项其实指定的 limit 为 200m 内存，故此没有失败。那么如果让 stress 分配 250m 肯定会失败吧
```
docker run -m 100m jess/stress --vm 1 --vm-bytes 250M --vm-hang 0
```
上面这条命令确实立刻就 terminate 了

double-reservation 策略是默认的设置，不过我们可以通过 --memory 和 --memory-swap 的配合设置来调整。比如两个选项设置为相同的值，你就完全禁掉了 swap 内存，或者说 swap 内存 limit 为 0


### 访问宿主机的资源

容器使用 kernel 的 namespace 来做到资源的隔离，然而我们还是有很多方式来 bypass namespace，直接访问宿主机的资源

##### -v volumn mounting

volumes 是最常见的访问宿主机资源的方式，主要的好处有两点

- 方便的共享宿主机的文件，而不需要把文件加到镜像的 layers 中，减少镜像的体积
- 访问宿主机的文件系统，比访问容器内部的文件系统要快，性能更好

##### --net=host 直接共享宿主机网络

是完全的使用宿主机的网络，比如通过 netstat 命令可以看到宿主机上的网络应用和端口信息，而不会生成 veth 接口和 bridge 虚拟局域网 ip。主要的好处如下：

- 更容易的 connect 容器，直接当做宿主机来访问就行，代价是失去了端口映射功能 (比如两个容器都想监听 80 端口，就不能都使用 --net=host 了，否则就要端口冲突了)
- 网络连接的速度和性能更好
 + --net=host 方式，直接使用宿主机网络，那么网络数据包就直接走 TCP/IP 到达 NIC (network interface card)
 + 容器常规方式下，数据包则要经过 TCP/IP -> Veth pair -> Bridge -> NAT 最终到达 NIC

##### 其他方式

- --pid=host 用于共享宿主机的 pid 信息
- --ipc=host 用于共享宿主机的共享内存、ipc 等资源
- --uts=host 用于共享宿主机的 hostname, NIS domain 等资源


### Device Mapper storage driver 和默认容器磁盘空间

Docker 自带一些 storage drivers 的支持，比如 Centos & Red Hat 默认的 devicemapper，Ubuntu 默认的 AUFS 等；相比起来，devicemapper bug 少一些，而且在一些方面上更加灵活

Devicemapper 默认的行为是分配一个文件，把它视为 "device" 来读写，比如我们上面提到过的 syslog 使用 /dev/log 文件来写入。这个设备文件是有 capability limit 的，不能自动增加文件尺寸

比如下面的 Dockerfile
```
FROM ubuntu:14.04
RUN truncate --size 11G /root/file
```
在 build 镜像时会出错，11G 太大了，无论你的宿主机有多大的磁盘空间也都会失败，因为 devicemapper 对容器的限制是 10G

通过 --storage-opt dm.basesize=xxx 来修改，我们还可以把它放到 DOCKER_OPTIONS 中，避免每次都带上这个选项
```
DOCKER_OPTIONS="-s devicemapper --storage-opt dm.basesize=20G"
```


调试容器
===========

### 使用 nsenter 调试容器网络

之前介绍过使用 socat 作为 proxy，代理对其他容器服务的请求，此时可以通过 socat 来调试和诊断对容器的网络连接；然而如果仅为了调试来 setup socat proxy 还是稍嫌复杂了，使用 nsenter 可以更加便捷的完成调试任务。

##### 容器化安装
```
$ docker run -v /usr/local/bin:/target jpetazzo/nsenter
```
此时，nsenter 会被安装在宿主的 /usr/local/bin 目录，可以在宿主机上直接被调用，就像直接安装在宿主机上一样

##### 通过宿主机的 bash 访问容器

我们知道 BusyBox 镜像是不带 bash 的，下面通过 nsenter 来达成使用宿主机 bash 进入容器的目标
```
$ docker run -ti busybox /bin/bash     # 这个会失败， busybox 不带 /bin/bash
FATA[0000] Error response from daemon: Cannot start container
$ CID=$(docker run -d busybox sleep 9999)    # 启动 busybox 并进入 sleep，把容器 id 保存到 CID 中
$ PID=$(docker inspect --format {{.State.Pid}} $CID)    # 获取 busybox 容器的 PID
$ sudo nsenter --target $PID \       # 在宿主机运行 nsenter，通过 --target 指定欲进入的容器 PID
--uts --ipc --net /bin/bash          # 其他选项指定 capability 以及进入容器后要启动的程序，也即宿主机的 /bin/bash
root@781c1fed2b18:~#                 # 看到进入容器了，而且启动了 /bin/bash
```

##### 使用宿主的 tcpdump 来调试网络应用

要使用 tcpdump，在启动 nsenter 的时候需要指定 --net 选项，以允许在宿主机上看到容器的网络，进而才能通过 tcpdump 调试

这里假定我们还在上一节启动的 busybox 容器中
```
root@781c1fed2b18:/# tcpdump -XXs 0 -w /tmp/google.tcpdump &      # 容器中使用宿主的 bash，后台启动宿主的 tcpdump

root@781c1fed2b18:/# wget google.com      # 然后调用一个 wget 命令，让 tcpdump 来 dump 网络连接信息
Resolving google.com (google.com)... 216.58.208.46, 2a00:1450:4009:80d::200e
............
http://www.google.co.uk/?gfe_rd=cr&ei=tLzEVcCXN7Lj8wepgarQAQ
Resolving www.google.co.uk (www.google.co.uk)... 216.58.208.67, 2a00:1450:4009:80a::2003
............
Saving to: ‘index.html’
2015-08-07 15:12:05 (2.18 MB/s) - ‘index.html’ saved [18720]
............
```

##### 找出宿主机中目标容器所对应的 veth interface 设备

有时我们需要很快的 down 掉目标容器的网络，一般的做法是使用一些网络工具来模拟网络 breakage，非常麻烦。我们看看用 nsenter 怎么完成这个任务
```
$ docker run -d --name offlinetest ubuntu:14.04.2 sleep infinity        # 重新启动一个目标容器
fad037a77a2fc337b7b12bc484babb2145774fde7718d1b5b53fb7e9dc0ad7b3

$ docker exec offlinetest ping -q -c1 8.8.8.8
1 packets transmitted, 1 received, 0% packet loss, time 0ms     # 验证容器内部 ping 是 ok 的

$ docker exec offlinetest ifconfig eth0 down      # 验证我们不能直接在宿主机上 down 掉容器的网络
SIOCSIFFLAGS: Operation not permitted

$ PID=$(docker inspect --format {{.State.Pid}} offlinetest)     # 找到容器 PID 准备 nsenter 进入容器
$ nsenter --target $PID --net ethtool -S eth0      # 进入容器，且指定 --net 选项，并启动宿主的 ethtool 工具
NIC statistics:
peer_ifindex: 53         # 容器的 eth0 是 veth pair 的一端，另一端在宿主机上

$ ip addr | grep '^53'   # 宿主机上查找 53 interface
53: veth2e7d114: <BROADCAST,MULTICAST,UP,LOWER_UP> mtu 1500 qdisc noqueue master docker0 state UP

$ sudo ifconfig veth2e7d114 down      # 上面看到 53 接口对应的 veth pair 就是 veth2e7d114 ，down 掉它！

$ docker exec offlinetest ping -q -c1 8.8.8.8
1 packets transmitted, 0 received, 100% packet loss, time 0ms      # 看到容器断网了！
```

综上 3 个例子，nsenter 的作用集中体现在可以进入容器内，同时使用或者说保留宿主机的诊断工具，非常方便，因为容器不需要做任何设置和修改


### 使用 tcpflow 调试容器网络

前面的技巧中，我们可以通过 nsenter 使用宿主机的 tcpdump 来研究容器的网络包信息，然而 tcpdump 属于比较底层的调试工具，上手难度高，而且如果要调试更上层的应用程序，会比较复杂和麻烦；可以考虑使用 tcpflow

容器安装和启动
```
$ IMG=dockerinpractice/tcpflow
$ docker pull $IMG
$ alias tcpflow="docker run --rm --net host $IMG"        # 注意到 --net=host 选项，共享宿主机的网络
```

有两种方法来调试容器的网络包

- 监控 docker0 interface，此时宿主机和容器全部网络包都会被监控，需要使用 packet-filtering expression 来过滤
- 类似上一节的方法，找到并监控对应容器的 veth pair interface，这样监控到的都是容器的网络包

第一种就很好用，看下例
```
$ docker run -d --name tcpflowtest alpine:3.2 sleep 30d               # 启动一个 alpine 容器做测试
$ docker inspect -f '{{ .NetworkSettings.IPAddress }}' tcpflowtest    # 容器的内部局域网 ip
172.17.0.1
$ tcpflow -c -J -i docker0 'host 172.17.0.1 and port 80'              # 监控 docker0 接口，并过滤内网 ip 和端口
tcpflow: listening on docker0
```

看到，使用起来非常方便，而且同样不需要修改和配置目标容器


### 容器应用失败的调试

当容器中有应用程序运行失败，又找不出原因时，可以考虑使用 strace 工具来跟踪系统调用，然后和正常运行的应用程序做比较，可能会帮助找到问题所在

比如在 ubuntu:12.04 的容器中，常常会遇到下面的错误，而 ubuntu:14.04 就没问题
```
[root@centos vagrant]# docker run -ti ubuntu:12.04
root@afade8b94d32:/# useradd -m -d /home/dockerinpractice dockerinpractice       # useradd 会失败
root@afade8b94d32:/# echo $?               # 返回非 0 值
12
```

使用 strace 工具来追踪
```
# strace -f \          # -f 选项跟踪进程以及进程的派生进程
useradd -m -d /home/dockerinpractice dockerinpractice     # 后面跟着需要跟踪的进程

execve("/usr/sbin/useradd", ["useradd", "-m", "-d", ... "dockerinpractice"], ... = 0       # 以下是输出，execve 表示执行参数中的命令，最后的 0 表示这条命令的返回值
[...]
open("/proc/self/task/39/attr/current", O_RDONLY) = 9      # 到这个命令时，打开文件，返回的 9 是文件的 handle
read(9, "system_u:system_r:svirt_lxc_net_"...,4095) = 46   # 读 handle 9 对应的文件
close(9) = 0                                               # 关闭文件
[...]
open("/etc/selinux/config", O_RDONLY) = -1 ENOENT (No such file or directory)    # 运行到这里出错了，未找到文件
open("/etc/selinux/targeted/contexts/files/ file_contexts.subs_dist", O_RDONLY) = -1 ENOENT (No such file or directory)
open("/etc/selinux/targeted/contexts/files/
file_contexts.subs", O_RDONLY) = -1 ENOENT (No such file or directory)
open("/etc/selinux/targeted/contexts/files/ file_contexts", O_RDONLY) = -1 ENOENT (No such file or directory)
[...]
exit_group(12)         # 这个就是整个应用最终的返回值 12
```

那么我们找到了问题，是因为应用要访问 selinux 的文件，然而并没有这个文件







