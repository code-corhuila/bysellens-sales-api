# bysellens-sales-api

## SALES: ejecución independiente

Proyecto Java 21 y Spring Boot 3.5.16 con `pom.xml` en la raíz.
No requiere compilar el proyecto padre ni instalar el artefacto `common`.
Las clases de soporte utilizadas por SALES se incluyen en
`src/main/java/com/bysellens/common`, conservando sus paquetes y comportamiento.
No se incluyen implementaciones de otros microservicios ni frontend.

### Compilar y probar

```sh
mvn clean verify
java -jar target/sales-service-1.0.0.jar
```

Tests use Mockito and MockMvc. RabbitMQ integration tests use Testcontainers and
are skipped when Docker is unavailable. Runtime requires PostgreSQL, RabbitMQ,
and access to the Customer, Product, and Inventory APIs.

### Configuración de ejecución

Proporcionar las siguientes variables mediante el entorno del proceso:

| Variable | Uso |
| --- | --- |
| `DB_URL` | URL JDBC PostgreSQL; predeterminado `jdbc:postgresql://localhost:5432/bysellens_db` |
| `DB_USER` | Usuario PostgreSQL; predeterminado `sales_app` |
| `DB_PASSWORD` | Contraseña PostgreSQL, obligatoria, sin valor predeterminado |
| `JWT_SECRET` | Clave HMAC compartida de al menos 32 bytes, obligatoria |
| `CUSTOMER_URL` | API Customer; predeterminado `http://customer-service:8080` |
| `PRODUCT_URL` | API Product; predeterminado `http://product-service:8080` |
| `INVENTORY_URL` | API Inventory; predeterminado `http://inventory-service:8080` |
| `RABBITMQ_HOST` | RabbitMQ host; default `localhost` |
| `RABBITMQ_USER` | RabbitMQ username; default `bysellens` |
| `RABBITMQ_PASSWORD` | RabbitMQ password; required, with no default |

La configuración original añade `?currentSchema=sales` a `DB_URL`; suministrar
la URL base sin parámetros. Flyway aplica la migración del esquema `sales` y
Hibernate valida las entidades. La aplicación escucha en el puerto 8080.

### API y contenedor

- `GET /api/ventas`: ventas confirmadas.
- `GET /api/ventas/{id}`: consulta individual.
- `POST /api/ventas`: creación con cabecera `Idempotency-Key` obligatoria.
- `/actuator/health`: estado de salud.

Sales operations require a JWT with the `ADMIN` role. Inventory reservations
remain idempotent and are confirmed asynchronously through RabbitMQ and the
transactional outbox; scheduled reconciliation remains enabled.

```sh
docker build -t bysellens-sales-api .
```

El contenedor requiere las mismas variables y conectividad. No incorporar
credenciales, archivos `.env`, claves privadas ni `target/` al repositorio.

> sales bounded context: service API

Part of the **LMS Library** distributed system — team `lms-library`, Grupo 2.
Governance and documentation live in [`library-docs`](https://github.com/code-corhuila/library-docs).

## Branching

Three permanent branches. **None of them accepts a direct commit** — you enter through a child
branch and leave through a Pull Request.

```
develop  <--PR--  feat/... fix/... chore/...
qa       <--PR--  qa/...
main     <--PR--  release/...  hotfix/...
```

Promotion happens **by re-application** (`git cherry-pick -x`), never by merging one permanent
branch into another: `merge develop -> qa` and `merge qa -> main` do not exist in this model.

`main` requires **1 approval from `ariel5253`**. On `develop` and `qa` the team sets its own review
rule.

Full policy: `00-governance/branching-policy.md` in `library-docs`.
