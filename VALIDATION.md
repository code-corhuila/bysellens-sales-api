# Validación de SALES

Origen: `BACKEND/sales-service` de la copia local disponible.
Las seis clases de soporte proceden de `BACKEND/common`; no se incluye código
de Auth, Customer, Product, Inventory ni del frontend.

Se conserva el código funcional de SALES y sus pruebas. Los cambios de Java
son de formato. El POM utiliza directamente el padre Spring Boot original e
incluye las dependencias necesarias para sustituir el módulo local `common`.
El Dockerfile utiliza el JAR generado en `target/` del proyecto independiente.

## Pruebas

- Maven: `mvn -B test` con el POM independiente.
- Verificación final: `mvn -B clean verify`, con compilación, pruebas y JAR
  ejecutable Spring Boot; `BUILD SUCCESS`.
- `SalesControllerTest`: 4 pruebas correctas.
- `SalesServiceTest`: 2 pruebas correctas.
- Total: 6 ejecutadas, 0 fallos, 0 errores, 0 omitidas; `BUILD SUCCESS`.

Las pruebas verifican idempotencia de ventas confirmadas, rollback ante un
resultado incierto de Inventory, cabecera de idempotencia, validación del
contrato HTTP y autorización. Utilizan dobles de los servicios externos;
no acreditan una integración real con PostgreSQL u otros microservicios.
