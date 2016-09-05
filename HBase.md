---
title: Architecting HBase Applications
date: TODO
tags: [Architect,HBase,NoSql]
categories: HBase
---

Notes on "Architecting HBase Applications"

<!-- more -->

HBase Principles
=================

### Table Layout

HBase 有两类表：

1. Systems Tables - used internally by HBase to keep track of meta information like the table’s access control lists (ACLs), metadata for the tables and regions, namespaces, and so on.
2. User Table - what you will create for your use cases.

Table Layout: HBase ==> column families (CF) ==> column (CQ) ==> Cell

row 是由有相同 row key 的多个 columns 组成的，每个 column 加上对应的 row key 称为 cell

每个 cell 可能有多个不同的版本，对应不同的时间戳，cell 还可以被称为 KeyValue 对儿

于是，换句话说，row 又可以定义为具有相同 row key 的一组 cells

和传统 RDBMSs 不同，HBase 是稀疏存储的，如果 row key 对应的某 column 的值不存在，那么在 HBase 中就确实不会存储这个 column，而不是存储 null

在定义 HBase table 的时候，只需要指定表名和 column families，不需要指定 columns，因为 columns 完全是动态生成和配置的

在 HBase 中，row keys 和 columns 都是 alphabetically 排序好的，无论是在内存中还是在表文件中，故此如果要按数字排序，需要把数字转为 byte representation

比如 9 和 1234 直接排序的话，1234 会在前面；如果想按大小排序，需要把两个数字分别保存为 0x00 0x00 0x04 0xD2 和 0x00 0x00 0x00 0x09，这样 9 就在前面了

### Table Storage

每个 HBase 表包含若干 Regions，每个 Region 包含若干 column families，每个 column family 包含一个 Store

Store 包含一个 memstore 和若干个 HFiles，每个 HFile 包含若干 blocks，每个 block 包含若干 cells

HBase 为了提供 scalability 和快速随机访问，把表数据分布到多个 RegionServers(对应 Hadoop的 Workers) 来存储

每个 Region 只存储一个特定范围的表数据，对应的 start row key 和  end row key 保存在 hbase:meta 表中

在同一个 Region 中，不同 column families 的数据被存储在不同的文件中，可以被区别配置，那么具有相似访问模式和相似格式的数据应该被放在同一个 column family 中

- 可压缩的文本信息数据和不可压缩的图像数据应该放在不同的 column families 中
- 多读少写的数据和多写少读的数据，应该放到不同的 column families 中

滥用 column family 会导致生成过多的小文件 (前面说了不同 CF 的数据会放在不同文件中)，给 memstore 带来很大压力，同时会导致更多的 Compaction 压力 (后面介绍 Compaction)

理论和 HBase 设计上，并没有限制 column family 的个数；然而实践上，column family 基本上不会多于 3 个；如果确实需要多个 column family，不妨把表拆成多个表来的更有效率

每个 Region 上的 column family 都对应一个 Store，其中数据会存储在 memstore 中，如果 memstore 满了，就 flush 到若干 HFiles 中

HFile 被存储在 HDFS 上，由若干 Blocks 组成，每个 Block 默认 size 为 64 KB，可以被压缩存储；Blocks 按类型顺序存储：Data Block -> Index Block -> BloomFilter Block -> Trailer Block

最终，HBase 表数据的最小存储单位是 cell，其实 row / column 这些都是逻辑概念，真正存储的是 cells 的集合

比如下面的 HBase 表

Keys | CF1/CQ1 | CF1/CQ2 | CF2/CQ1
-----|---------|---------|--------
042  |    C    |         |    E
123  |    I    |    A    |

在 HBase 中存储为

Keys| CF  | CQ  | val
----|-----|-----|---
042 | CF1 | CQ1 | C
042 | CF2 | CQ1 | E
123 | CF1 | CQ1 | I
123 | CF1 | CQ2 | A

当然，实际上的 cell 实现比上面的样子要复杂的多，会附带有 key length / value length / key / value / tags / cf length / cf / cq / timestamp / key type 等等各种属性信息

### Internal Table Operations

#### Compaction

HBase 把接收到的操作数据保存到 memstore 中，当 memstore 满了，就会 flush 到 HFile 中，于是就会在 HDFS 中产生很多的小文件

HBase 会时不时的选择一些小文件进行 Compaction，整合成大文件，在保持数据 locality 的同时提高查询效率，减少 keep track of 过多小文件的压力，同时还可以清除已经被删除的数据

Compaction 分为 Minor 和 major 两类

Minor Compaction 只对 Region 中的部分文件进行，默认为超过两个 HFiles 就会触发 Compaction，HBase 会按策略选取某些文件进行处理；如果选取了的全部 HFiles，那么就提升为 major

Minor Compaction 可能会做一些不完全的 data cleanup，之所以不完全，是因为只对部分文件进行处理，故此信息不够全面

比如执行 cell 的删除操作，比如选中的 HFiles 中有一个 cell 的版本为 3，标记为删除，同时还有相同的 cell 版本为 2，那么就会删除版本为 2 的 cell

之所以是不完全的 cleanup，是因为版本为 1 的 cell 可能没有被选中，故不会被删除；也由此，版本为 3 的 cell 也不能删除，否则就丢掉了删除标识，版本为 1 的 cell 就不会再被删除了

Major Compaction 则是 Region 中 (甚至 Table 中) 的 HFiles 全部被选中处理，既可以被手动触发，也可以被配置为 weekly 执行

上例中的 cell 的全部 1~3 版本都可以在 Major Compaction 中被删除，达到完全 cleanup 的效果

#### Splits (Auto-Sharding)

和 Compaction 正好相反，随着 Compaction 的进行，HFiles 越来越大，也会导致问题，因为越大就越难于解析，难于进一步的 Compaction 等等

HBase 配置了一个 maximum HFile size，0.94 版本之后设置为 10 GB，只要 Region 中的某个 column family 的 HFile 超过这个阈值，就会引发 Splits，效果是把 Region 一分为二

注意，如果 Region 中有多个 column family，而只有一个的 HFile 超过了阈值，那么 Splits 的时候会把所有 column family 都分成两份；新的 Regions 的信息会在 hbase:meta 中更新

还记得 Region 和 hbase:meta 表记录着数据 row key 范围的上下界，故此 Splits 一定不会把同一个 row key 的不同 column 分到不同的 region 中，即 All the columns stay together

#### Balancing

Regions 会被 Split，系统会 Fail，新的 servers 会加到 cluster 中来，故此负载会有可能不再很好的分布在集群的 RegionServers 中

于是 HBase 每 5 分钟会运行 load balancer 来协调负载；0.96 版本后，默认使用 StochasticLoadBalancer 来做 balancing


HBase Sizing and Tuning
=========================

### Hadoop Tuning

YARN allows for specific tuning around the number of CPUs utilized and memory consumption. Three main functions to take into consideration are:

- yarn.nodemanager.resource.cpu-vcores
    Number of CPU cores that can be allocated for containers.
    由于超线程 CPU 的存在， total vcores = physical-cores * 1.5
    这样，配置值为 total vcores - HBase 1 vocre - DataNode 1 vcore - NodeManager 1 vcore - 操作系统 1 vcore - 其他可能的服务如 Impala/Solr 1 vcore

- yarn.nodemanager.resource.memory-mb
    Amount of physical memory, in megabytes, that can be allocated for containers.
    It is important not to over allocate memory for the node: 操作系统 8-16 GB，DataNode 2-4 GB，HBase 12-24 GB，其他分给 YARN framework
    HBase heap space 不要超过 24 GB，否则会导致 garbage collection 过程时间过长(超过 30s)，使得 RegionServer 在 zookeeper 中超时

- yarn.scheduler.minimum-allocation-mb
    The minimum allocation for every container request at the RM, in megabytes，推荐 1-2 GB

### HBase Tuning

#### Write-heavy workload

Two main ways to get data into HBase: either through API (Java, Thrift, REST) or by using bulk load.

最重要的区别在于，API 的方法要使用 WAL (write ahead log) 和 memstore，而 bulk load 是一种 short-circuit 短路型写操作，绕过了 WAL 和 memstore

##### API-driven write model

我们说，HBase 最主要的瓶颈就在于 WAL followed by the memstore，以下使一些优化写操作性能的公式 (假设一个 Region 只有一个 CF)

- To determine region count per node
    availableMemstoreHeap = HBaseHeap * memstoreUpperLimit  (总 HBase 内存堆 乘以 可用于 memstore 的比例)
    recommendedActiveRegionCount = availableMemstoreHeap / memstoreSize  (前提假设了 Region 只有一个 CF，而每个 CF 对应一个 memstore，故此，可供 memstore 使用的总内存 除以 单个 memstore 的容量即可)

- To determine raw space per node
    rawSpaceUsed = recommendedRegionCount * maxfileSize * replicationFactor  (Region 数 * 每个 Region 的文件容量 * 复制系数)

- To determine the number of WALs to keep
    numberOfWALs = availableMemstoreHeap / (WALSize * WALMultiplier)

例如：假设节点 HBase heap = 16 GB， Memstore upper limit = 0.5， Memstore size = 128 MB， Maximum file size = 20 GB， WAL size = 128 MB， WAL rolling multiplier = 0.95， replicationFactor = 3

availableMemstoreHeap = 16,384 MB * 0.5 = 8192 MB

recommendedActiveRegionCount = 8192 MB / 128 MB = 64

rawSpaceUsed = 64 * 20G * 3 = 3.75 TB

numberOfWALs = 8192 MB / (128 MB * 0.95) = 67

##### Bulk load write model

再次模型下，HFile 是在 MapRedue 的 Reduce 阶段被生成，然后使用 completebulkload 工具导入到 HBase 中

#### Read-heavy workload

The primary settings that need to be tweaked are the same as write workloads

- lowering memstore settings
- raising the block cache to allow for more data to be stored in memory

HBase 0.96 引入了 bucket cache 的概念，允许数据同时在内存和低延迟的 disk (SSD/flash cards) 中保存
