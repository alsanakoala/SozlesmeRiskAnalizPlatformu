# Contract Risk Analysis Platform (Starter)

Spring Boot + PostgreSQL + Apache Tika + Rule Engine (Regex) iskeleti.

## Hızlı Başlangıç
1) **PostgreSQL**: `docker-compose up -d`
2) **Build**: `./mvnw clean package` (veya `mvn clean package`)
3) **Çalıştır**: `java -jar target/contract-risk-platform-0.1.0.jar`
4) **Sağlık**: `GET http://localhost:8080/api/v1/health`
5) **Yükle**: `POST /api/v1/documents/upload` (multipart form-data: file=PDF)
6) **Analiz**: `POST /api/v1/documents/{id}/analyze`

> OpenNLP model dosyaları opsiyoneldir. `src/main/resources/nlp/` altına (`en-sent.bin`, `tr-sent.bin`) eklerseniz segmentasyonda kullanılabilir.

## Yapı
- `RulePack` YAML: `src/main/resources/risk/rulepack.yml`
- Flyway şema: `src/main/resources/db/migration/V1__init.sql`

## Notlar
- Bu sürüm, **kural tabanlı** basit bir risk tespit ve bileşim puanı içerir.
- Faz B'de DL4J/Python entegrasyonu eklenir.

