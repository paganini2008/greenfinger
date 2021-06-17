# Greenfinger Project
**Greenfinger** is a high-performance **distributed web crawler framework** based on springboot framework. With a little configuration, you can easily build a distributed web crawler microservice cluster. In addition, the greenfinger framework provides rich interfaces to customize your system

### Compatibility
1. jdk8 (or later)
2. SpringBoot Framework 2.2.x (or later)
3. Redis 3.x (or later)
4. MySQL 5.x (or later)
5. ElasticSearch 6.x (or later)

### Install
``` xml
<dependency>
	<groupId>indi.atlantis.framework</groupId>
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
