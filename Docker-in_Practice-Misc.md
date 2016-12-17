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
-v /var/run:/var/run dockerinpractice/docker-authenticate     # 
```
