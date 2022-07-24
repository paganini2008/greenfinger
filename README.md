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


## Run
``` java
@EnableGreenFingerServer
@SpringBootApplication
public class GreenFingerServerConsoleMain {

	public static void main(String[] args) {
		SpringApplication.run(GreenFingerServerConsoleMain.class, args);
	}
}
```
