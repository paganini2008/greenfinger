# History

The 1.x project, kept for reference.

`greenfinger-spring-boot-starter` and `greenfinger-ui` are the original Spring Boot 2.7 / doodler
implementation, together with the Maven aggregator that built them. Nothing here takes part in the
current build; the working project is in `backend/`.

It is kept because the 2.x rewrite is a port rather than a fresh start: when a behaviour is in
question, this is the authority on what the original did.

| Directory | What it was |
|---|---|
| `greenfinger-spring-boot-starter/` | The crawler engine, its six pluggable components, the Elasticsearch indexer and the REST API |
| `greenfinger-ui/` | The Spring Boot application that hosted the Angular interface, plus `db/crawler.sql` |
| `pom.xml` | The aggregator that built both |
