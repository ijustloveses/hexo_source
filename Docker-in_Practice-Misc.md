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









