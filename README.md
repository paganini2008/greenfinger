# Greenfinger Framework
A high-performance distributed web crawling framework based on <code>SpringBoot</code> framework. It provides rich APIs to customize business and easily embedded your system. 

## Compatibility

* Jdk8 (or later)
* Netty 4.x (or later)
* <code>SpringBoot</code> Framework 2.2.x (or later)
* Redis 3.x (or later)
* MySQL 5.x (or later)
* ElasticSearch 6.x (or later)

## Install

``` xml

<dependency>
    <groupId>com.github.paganini2008.atlantis</groupId>
    <artifactId>greenfinger-spring-boot-starter</artifactId>
    <version>1.0-RC1</version>
</dependency>

```

## Features

* Perfect compatibility with <code>SpringBoot</code> framework
* Support universal and vertical Crawlers
* Adopt depth-first crawling strategy
* Support multiprocessing crawling, which is easy to scale horizontally
* Support full index and incremental index
* Support update indexes by task scheduling
* Support mainstream web page parsing technologies (like htmlunit, selenium)
* Support removing duplicate URLs
* Support multiple conditional interrupt strategies or customizing conditional interrupt strategies
* Support index query with multiple version 



## Core API

<code>CrawlerLauncher</code>

<code>PathFilterFactory</code>

<code>CrawlerHandler</code>

<code>ResourceManager</code>

<code>PageExtractor</code>

<code>Condition</code>

<code>PathAcceptor</code>




## Quick Start

Step 1:
You need to get the latest code of GreenFinger project.  
git clone https://github.com/paganini2008/greenfinger.git

Step 2:
cd greenfinger/greenfinger-console/run

You can see:

```
run
├── config
│   ├── application-dev.properties
│   └── application.properties
├── db
│   └── crawler.sql
├── images
├── lib
├── greenfinger-console-1.0.1.jar
└── logs

```


Step 3:
open application-dev.properties then to modify the Jdbc Configuration, Redis Configuration and Elasticsearch Configuration
Other configuration has better keep default settings when you start the application on first time.

Step4:
execute 'java -jar greenfinger-console-1.0.1.jar --server-port=21212' and start the Greenfinger Console application.
Default http port is 21212. You can also  start multiple applications of Greenfinger-Console to form a cluster and then work together.



Step 5:

Open your browser:

http://localhost:21212/atlantis/greenfinger/catalog/
