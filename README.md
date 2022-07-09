# Greenfinger Framework
A high-performance distributed web crawler framework based on <code>SpringBoot</code> framework. It  provides rich APIs to customize business and easily embedded your system. 

## Compatibility

* Jdk8 (or later)
* <code>SpringBoot</code> Framework 2.2.x (or later)
* Redis 3.x (or later)
* MySQL 5.x (or later)
* ElasticSearch 6.x (or later)

## Install

``` xml

<dependency>
    <groupId>com.github.paganini2008.atlantis</groupId>
    <artifactId>greenfinger-spring-boot-starter</artifactId
    <version>1.0-RC1</version>
</dependency>

```

### Features
1. Perfectly compatible with springboot 2.2.0 (or later)
2. Support dynamic horizontal expansion of crawler micro service
3. Provide a variety of load balancing algorithms or custom load balancing algorithms
4. Support to create, update and delete crawler index
5. Support a variety of mainstream HTTP client technology
6. Support bloom filter to remove duplicate URL
7. Provide multiple conditional interrupt strategies or custom conditional interrupt strategies
8. Support statistics of processed URLs

### Run
``` java
@EnableGreenFingerServer
@SpringBootApplication
public class GreenFingerServerConsoleMain {

	public static void main(String[] args) {
		SpringApplication.run(GreenFingerServerConsoleMain.class, args);
	}
}
```
