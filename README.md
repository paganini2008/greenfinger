
# GreenFinger

[![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
[![Spring Cloud](https://img.shields.io/badge/Spring%20Cloud-Compatible-brightgreen.svg)](https://spring.io/projects/spring-cloud)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-Compatible-blue.svg)](https://www.postgresql.org/)
[![Elasticsearch](https://img.shields.io/badge/Elasticsearch-Compatible-green.svg)](https://www.elastic.co/)
[![Netty](https://img.shields.io/badge/Netty-Based-brightgreen.svg)](https://netty.io/)

GreenFinger is a **powerful and scalable distributed web crawler framework** designed to handle massive-scale web crawling with precision and flexibility. With its **Spring Cloud** foundation and integration with **PostgreSQL** and **Elasticsearch**, GreenFinger is the ultimate choice for enterprises and developers seeking a customizable, high-performance crawling solution. Its advanced features, intuitive Web UI, and event-driven architecture make it stand out as an exceptional tool for data...

---

## Key Features

### 1. Intuitive Web UI for Task Management
GreenFinger provides a user-friendly **Web UI** for managing crawler tasks:
- Create and configure websites, URLs, and crawling parameters.
- Dynamically create tasks using **Amber Job** (see [Amber Job README](https://github.com/paganini2008/amber-job)) or trigger tasks manually with one click.
- Monitor task execution in real time and view detailed logs.

---

### 2. Distributed Architecture with Dynamic Node Scaling
- GreenFinger supports **horizontal scaling**, allowing nodes to be dynamically added or removed as needed.
- The distributed architecture ensures efficient resource utilization and fault tolerance, enabling seamless handling of large-scale crawling tasks.

---

### 3. Massive-Scale Duplicate URL Elimination
- Supports **trillions of URLs** with advanced deduplication strategies.
- Integrates **Bloom Filters** for memory-efficient duplicate detection.
- Ensures clean and efficient crawling by eliminating redundant URLs from the pipeline.

---

### 4. Selenium Integration for Dynamic Page Crawling
- GreenFinger integrates with **Selenium**, enabling it to handle dynamic web pages, JavaScript-rendered content, and other modern web technologies.
- Ideal for crawling rich, interactive websites.

---

### 5. Intelligent URL Depth and Format Validation
- Automatically validates URL formats and depth levels to prevent over-crawling.
- Prevents URL "leakage" by ensuring the crawler remains focused on the intended domain or site scope.
- Uses advanced algorithms to detect and block domain transitions.

---

### 6. Real-Time Monitoring and Interrupt Conditions
- Provides comprehensive **real-time monitoring** of task progress, resource utilization, and key metrics.
- Supports **conditional interruption**, allowing tasks to terminate automatically based on predefined rules, such as data thresholds, error rates, or completion percentages.

---

### 7. Vertical Crawling with High Customizability
- Designed for **vertical crawling** with domain-specific configurations.
- Fully customizable crawling logic, allowing developers to tailor it to their exact needs, including data extraction rules, scheduling, and storage formats.

---

### 8. PostgreSQL and Elasticsearch Integration
- Uses **PostgreSQL** for robust, relational data storage and task management.
- Automatically creates **Elasticsearch indices** for fast and scalable search capabilities.

---

### 9. Event-Driven, High-Performance Architecture
- Built on **Netty NIO**, leveraging non-blocking IO for high throughput and low latency.
- The **event-driven model** ensures efficient task execution, making GreenFinger suitable for high-concurrency environments.

---

### 10. Future-Ready Features
- **Advanced Workflow Support**: Plan to integrate DAG-based task orchestration for complex crawling workflows.
- **Custom Plugin System**: Future support for pluggable extensions to enhance functionality, such as new deduplication algorithms or domain-specific parsers.

---

## Getting Started

### Prerequisites
- **Java 17+**
- **PostgreSQL** for task and data storage
- **Elasticsearch** for search indexing
- **Selenium** (optional) for dynamic page crawling

### Installation
1. Clone the repository:
   ```bash
   git clone https://github.com/paganini2008/greenfinger.git
   cd greenfinger
   ```

2. Build and run the application:
   ```bash
   mvn clean install
   java -jar target/greenfinger.jar
   ```

3. Access the Web UI:
   Navigate to `http://localhost:8080` to configure tasks and monitor progress.

---

## Documentation
For detailed setup instructions, API references, and advanced configuration, visit the [Official Documentation](https://github.com/paganini2008/greenfinger/wiki).

---

## Contributing
Contributions are welcome! Refer to the [Contributing Guide](CONTRIBUTING.md) for more information.

---

## License
GreenFinger is licensed under the MIT License. See the [LICENSE](LICENSE) file for more details.

---

GreenFinger combines the power of distributed systems, high-performance crawling, and real-time monitoring into a single, cohesive framework. Whether for enterprise-scale web scraping or domain-specific data extraction, GreenFinger is the ultimate tool for modern web crawling challenges.
