---
title: Microservices in Go - Part I.
date: 2016-08-25 15:47:13
tags: [Go,Docker,Microservice,笔记,Influxdb,Grafana,Metrics]
categories: Microservice
---

Metrics in Go

<!-- more -->

### 初步搭建可运行环境  -- metrics_server

##### influxdb & grafana docker containers

- influxdb - json timeseries database
- grafana - charts

```
$ docker run --name influxdb -d -p 8083:8083 -p 8086:8086 -e PRE_CREATE_DB="metric" tutum/influxdb
$ docker run --name grafana -d --link influxdb:influxdb 
    -e INFLUXDB_HOST=influxdb 
    -e INFLUXDB_PORT=8086 
    -e INFLUXDB_NAME=metric         <--- 上面 influxdb container 启动时，预创建数据库 metric
    -e INFLUXDB_USER=root 
    -e INFLUXDB_PASS=root 
    -p 3300:80 hyperworks/grafana

$ docker images
docker.io/tutum/influxdb                           latest              5d606dc14680        6 months ago        275.2 MB
docker.io/hyperworks/grafana                       latest              c2d5108b41f0        15 months ago       260 MB

$ docker ps -a
CONTAINER ID        IMAGE                COMMAND             CREATED             STATUS              PORTS                                            NAMES
461158dad923        hyperworks/grafana   "/run.sh"           6 hours ago         Up 6 hours          0.0.0.0:3300->80/tcp                             grafana
48681f0bddd1        tutum/influxdb       "/run.sh"           6 hours ago         Up 6 hours          0.0.0.0:8083->8083/tcp, 0.0.0.0:8086->8086/tcp   influxdb
```

http://10.88.147.128:8083/ 可以看到 influxdb 的配置，以及运行一些查询和操作；另看到 influxdb 版本：v0.9.6

http://10.88.147.128:3300/ 可以看到 grafana 的登录页面，可以使用 admin/admin 登录；另看到 grafana 版本：version: 2.0.2

##### golang docker container

为了找个小一些的，找了 go-1.7.0 + alpine 
```
$ docker pull golang:1.7.0-alpine

$ docker images
docker.io/golang                                   1.7.0-alpine        52493611af1e        7 days ago          241.1 MB

$ docker run -it --rm --name metrics_server -p 3000:3000 --link influxdb:influxdb golang:1.7.0-alpine sh

查看环境
/go # go env
GOARCH="amd64"
GOHOSTARCH="amd64"
GOHOSTOS="linux"
GOOS="linux"
GOPATH="/go"
GOROOT="/usr/local/go"
GOTOOLDIR="/usr/local/go/pkg/tool/linux_amd64"
CC="gcc"
GOGCCFLAGS="-fPIC -m64 -pthread -fmessage-length=0"
CXX="g++"
CGO_ENABLED="1"
......

试试看下载安装一个 go model
/go # go get github.com/GeertJohan/go-metrics/influxdb
go: missing Git command. See https://golang.org/s/gogetcmd
失败了，原因是没有安装 git

关闭容器，然后去掉 --rm 命令，再次启动
$ docker run -it --name metrics_server -p 3000:3000 --link influxdb:influxdb golang:1.7.0-alpine sh

安装 git
/go # apk add --no-cache git
fetch http://dl-cdn.alpinelinux.org/alpine/v3.4/main/x86_64/APKINDEX.tar.gz
fetch http://dl-cdn.alpinelinux.org/alpine/v3.4/community/x86_64/APKINDEX.tar.gz
(1/5) Installing libssh2 (1.7.0-r0)
(2/5) Installing libcurl (7.50.1-r0)
(3/5) Installing expat (2.1.1-r1)
(4/5) Installing pcre (8.38-r1)
(5/5) Installing git (2.8.3-r0)
Executing busybox-1.24.2-r9.trigger
OK: 22 MiB in 17 packages

退出，然后使用 docker commit 把安装好 git 的容器提交为镜像
$ docker commit metrics_server golang:1.7.0-alpine-git
$ docker images
golang                                             1.7.0-alpine-git    4c832d88245a        11 seconds ago      258.1 MB
docker.io/golang                                   1.7.0-alpine        52493611af1e        7 days ago          241.1 MB
............

最后善后，把未 rm 的 metrics_server 容器 rm 掉
$ docker rm metrics_server
```

##### 本地创建 HttpServer 测试项目 metrics_server.go

目录 /home/vagrant/workspace/myproj/microservices_in_go/
```
$ cd /home/vagrant/workspace/myproj
$ mkdir microservices_in_go
$ cd microservices_in_go/
```

Http 服务器代码，这里只是搭建一个 metric 数据定时导入 influxdb 的框架，然而并没有添加任何事件的监控
```
$ vim metrics_server.go
package main

import (
    "github.com/GeertJohan/go-metrics/influxdb"
    "github.com/rcrowley/go-metrics"
    "net/http"
    "time"
)

func MetricToInfluxDB(d time.Duration) {
    go influxdb.Influxdb(metrics.DefaultRegistry, d, &influxdb.Config{    ==>  goroutine to monitor metric and save into influxdb every d duration
        Host: "influxdb:8086",                                            ==>  这个 go 脚本会放到容器中运行，而这个容器会 link influxdb 容器
        Database: "metric",
        Username: "root",
        Password: "root",
    })
}

func IndexHandler(w http.ResponseWriter, r *http.Request) {          ==>  handler to handle http request
    w.WriteHeader(http.StatusOK)
    w.Write([]byte("Hello World!"))
}

func main() {
    MetricToInfluxDB(time.Second * 1)
    http.HandleFunc("/", IndexHandler)
    http.ListenAndServe(":3000", nil)                               ==> 外部可以通过 10.88.147.128 访问
}
```

##### 在 docker 中启动 golang 容器运行 HttpServer 

```
$ docker run -it --rm --name metrics_server 
    -p 3000:3000                      ==> 为了容器外访问 http server 的端口
    --link influxdb:influxdb          ==> 为了访问 influxdb
    -v ${PWD}:/go                     ==> 把当前目录映射到容器的默认初始目录 /go，目的是可以访问前面实现的 metrics_server.go
    golang:1.7.0-alpine-git sh        ==> 使用刚刚安装好 git 的 golang 镜像

/go # go get github.com/GeertJohan/go-metrics/influxdb
/go # go get github.com/rcrowley/go-metrics

/go # ls
metrics_server.go  pkg                src

/go # go run metrics_server.go  报错！
2016/08/25 01:41:57 Server returned (404): 404 page not found
2016/08/25 01:41:57 Server returned (404): 404 page not found
2016/08/25 01:41:57 Server returned (404): 404 page not found
2016/08/25 01:41:57 Server returned (404): 404 page not found
...............

退出容器，容器由于 --rm 选项自动清除
```

经 google，发现 [How to setup Docker Monitoring](https://www.brianchristner.io/how-to-setup-docker-monitoring/) 评论区中有人说这是 influxdb:0.9 的问题

故此尝试调整到 influxdb:0.8.8
```
关闭已有 0.9.6 的 influxdb 容器
$ docker rm -f influxdb

尝试运行 0.8.8 版本的 influxdb
$ docker run --name influxdb -d -p 8083:8083 -p 8086:8086 -e PRE_CREATE_DB="metric" tutum/influxdb:0.8.8

然后再次运行 golang with git 容器
$ docker run -it --rm --name metrics_server -p 3000:3000 --link influxdb:influxdb -v ${PWD}:/go golang:1.7.0-alpine-git sh

这次不需要再 go get github models 了，因为上次 get 到的新 models 都放到当前目录的 pkg & src 子目录下，而这个目录是通过 -v 和宿主连接，自动 persist
/go # go run metrics_server.go
```
这次 http server 不再报错了

然后在其他 shell 执行  $ curl 10.88.147.128:3000，看到输出  Hello World!  说明 Http Server 正常运行； 浏览器上也可以正常看到输出 Hello World! 了

再次查看 influxdb web 管理界面，使用 root/root 可以正常登录，并看到 metric 数据库已经在列

善后，清除不好用的 influxdb 0.9.6 版本： $ docker rmi ${label of influxdb:latest}

至此，框架已经搭好，后面就是看看如何通过这个框架添加对事件的监控和统计了


### 监控事件

##### HttpServer 中添加事件  

修改 metrics_server.go 中的代码

定义两个全局变量，分别代表首页访问次数，以及首页响应时间
```
var requestCounter metrics.Counter
var responseTime metrics.Timer
```

IndexHandler() 函数中，加入两个全局变量的更新操作
```
    requestCounter.Inc(1)
    startReqTime := time.Now()
    defer responseTime.Update(time.Since(startReqTime))
    ......
```
显然， requestCounter 比较简单，每次访问直接 inc(1) 即可； responseTime 这个用到了 defer，这样在函数结束之前会根据函数开始时的时间来计算响应时间

main() 函数中，初始化并注册事件
```
    requestCounter = metrics.NewCounter()
    metrics.Register("count_request", requestCounter)

    responseTime = metrics.NewTimer()
    metrics.Register("response_time", responseTime)

    MetricToInfluxDB(time.Second * 1)
    ......
```
看到，事件注册之后，调用 MetricToInfluxDB 函数，这个函数会把 metrics.DefaultRegistry 中注册的事件数据传到 influxdb 的对应数据库 metric 中

##### 启动 HttpServer

这次在 docker run 中直接调用 go run metrics_server.go, 而不是像以前那样只是调用 sh
```
方法一：去掉 --rm，加上 -d，这样启动的容器在后台运行；之所以要去掉 --rm，是因为它和 -d 不兼容
$ docker run -it -d --name metrics_server -p 3000:3000 --link influxdb:influxdb -v ${PWD}:/go golang:1.7.0-alpine-git go run metrics_server.go

方法二：保留 --rm，不使用 -d，这样容器将直接在前台运行，整个 shell 阻塞住，一直等待接收请求
$ docker run -it --rm --name metrics_server -p 3000:3000 --link influxdb:influxdb -v ${PWD}:/go golang:1.7.0-alpine-git go run metrics_server.go
```

这里采用方法一，于是可以直接在同一个 shell 中查询容器状态
```
$ docker ps -a
CONTAINER ID        IMAGE                     COMMAND                  CREATED             STATUS              PORTS                                                      NAMES
3bcec22768d0        golang:1.7.0-alpine-git   "go run metrics_serve"   14 seconds ago      Up 13 seconds       0.0.0.0:3000->3000/tcp                                     metrics_server
db0eb6d58f8d        tutum/influxdb:0.8.8      "/run.sh"                3 hours ago         Up 3 hours          0.0.0.0:8083->8083/tcp, 0.0.0.0:8086->8086/tcp, 8084/tcp   influxdb
461158dad923        hyperworks/grafana        "/run.sh"                29 hours ago        Up 29 hours         0.0.0.0:3300->80/tcp                                       grafana
```

##### 测试事件监控效果

浏览器中连续访问 http://10.88.147.128:3000/ 6 次

influxdb 网站 http://10.88.147.128:8083/ ，首页 Databases 部分，点击 metric 对应的 Explore Data，在查询页面中运行 **list series**，得到全部 series
> list_series_result
> time  name
> 0     count_request.count
> 0     response_time.timer

查询页面中查询 select value from response_time.timer，报错：ERROR: Field value doesn't exist in series response_time.timer

查询 select * from response_time.timer 得到一些图和表，发现 fields 中没有 value 字段，有的是 count 字段  (这个查询比较慢，要耐心 ...)

查询 select count from response_time.timer 得到 count 字段相关的图和表；下面去 grafana 中查看

grafana 网站 http://10.88.147.128:3300/ ，
> Home -> New (to create new dashboard) 进入新的 dashboard 页面
> 点击左边的小条处 -> Add Panel -> Graph -> Save Dashboard
> Graph 中间上方 "no title (click here)" 点击 -> Edit 打开 Edit form
> 选中 Metric Tab 页， Series 框输入 response_time.timer，select 框中选择 mean(count)，保持其他选项不变 (比如 group by time = 30s)
> 得到时序图，哈哈哈

同理还可以查看 count_request.count 相关的图表和数据，从略
