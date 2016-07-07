---
title: 7周7并发模型 P7 - The Lambda Architecture
date: 2016-06-29 02:27:25
tags: [并发,Lambda Architecture,7日7并发模型,笔记]
categories: 并发
---

Notes on 7 Concurrent Models in 7 Weeks - Part 7. The Lambda Architecture

<!-- more -->

### The Batch Layer -- MapReduce

### The Speed Layer -- Counting Wiki Contributions with Storm

Simulate Logs  -->  Parse Logs  -->  Record Contributions

##### Simulating the Contribution Logs

``` java
public class RandomContributorSpout extends BaseRichSpout {
    private static final Random rand = new Random();
    private static final DateTimeFormatter isoFormat = ISODateTimeFormat.dateTimeNoMillis();
    private SpoutOutputCollector collector;
    private int contributionId = 10000;

    /// open() is used during initialization
    public void open(Map conf, TopologyContext context, SpoutOutputCollector collector) {
        this.collector = collector;
    }

    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        declarer.declare(new Fields("line"));    /// the tuples have a single field called line
    }

    public void nextTuple() {
        Utils.sleep(rand.nextInt(100));
        ++contributionId;
        String line = isoFormat.print(DateTime.now()) + " " + contributionId + " " + rand.nextInt(10000) + " " + "dummyusername";
        collector.emit(new Values(line));
    }
}
```

##### Parsing Log Entries

``` java
/// parses log, and outputs tuples with four fields, one for each component of the log line
class ContributionParser extends BaseBasicBolt {
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        declarer.declare(new Fields("timestamp", "id", "contributorId", "username"));
    }

    public void execute(Tuple tuple, BasicOutputCollector collector) {
        Contribution contribution = new Contribution(tuple.getString(0));    /// Contribution class will convert string into four fields, skip.
        collector.emit(new Values(contribution.timestamp, contribution.id, contribution.contributorId, contribution.username));
    }
}
```

##### Recording Contributions

``` java
class ContributionRecord extends BaseBasicBolt {
    /// why set?? adding an item to a set is idempotent. -- At least once !
    private static final HashMap<Integer, HashSet<Long>> timestamps = new HashMap<Integer, HashSet<Long>>();

    public void declareOutputFields(OutputFieldsDeclarer declarer) {
    }

    public void execute(Tuple tuple, BasicOutputCollector collector) {
        addTimestamp(tuple.getInteger(2), tuple.getLong(0));    /// contributorId & timestamp
    }

    private void addTimestamp(int contributorId, long timestamp) {
        HashSet<Long> contributorTimestamps = timestamps.get(contributorId);
        if (contributorTimestamps == null) {
            contributorTimestamps = new HashSet<Long>();
            timestamps.put(contributorId, contributorTimestamps);
        }
        contributorTimestamps.add(timestamp);
    }
}
```

##### Building the Topology

``` java
public class WikiContributorsTopology {
    public static void main(String[] args) throws Exception {
        TopologyBuilder builder = new TopologyBuilder();
        builder.setSpout("contribution_spout", new RandomContributorSpout(), 4);    /// 4 is a hint instructing Storm to create 4 workers for our spout
        /// simply sends tuples to a random worker
        builder.setBolt("contribution_parser", new ContributionParser(), 4).shuffleGrouping("contribution_spout");
        /// all tuples with the same values for a set of fields (in our case, the contributorId field)
        builder.setBolt("contribution_recorder", new ContributionRecord(), 4).fieldsGrouping("contribution_parser", new Fields("contributorId"));

        LocalCluster cluster = new LocalCluster();
        Config conf = new Config();
        cluster.submitTopology("wiki-contributors", conf, builder.createTopology());
        Thread.sleep(10000);

        cluster.shutdown();
    }
}
```




