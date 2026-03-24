# Análisis del proyecto banco02 (Spring Boot)

Fecha del análisis: 2026-03-24

## 1) Resumen ejecutivo (para recordar rápido)

Tu proyecto tiene una base muy buena para un **ATM/cajero web**:
- Arquitectura por capas clara (controller/service/repository).
- Entidades y repositorios bien encaminados para un CRUD transaccional.
- Flujo funcional completo para login, consultas, retiro, consignación, transferencia y cambio de PIN.

Sin embargo, hoy hay varios puntos que conviene corregir para llevarlo a un estado más sólido de producción: **seguridad, consistencia transaccional, robustez de vistas y pruebas**.

## 2) Qué está bien logrado

1. **Separación de responsabilidades**
   - Controladores para web MVC, servicios para reglas, repositorios JPA para persistencia.
2. **Cobertura funcional de negocio**
   - Incluye operaciones principales de un cajero: retiro, transferencia, consignación, consulta y cambio de PIN.
3. **Modelado inicial correcto**
   - Relaciones `Cliente -> Cuenta -> Movimiento` con JPA.
4. **Uso de Lombok y Spring Data**
   - Reduce ruido de código y acelera desarrollo.

## 3) Hallazgos clave (priorizados)

### Prioridad ALTA

1. **Seguridad de PIN en texto plano**
   - El PIN se compara directamente con `equals`, sin hashing.
   - Recomendación: `BCryptPasswordEncoder` + migración de PINs almacenados.

2. **Validaciones de pertenencia de cuenta comentadas**
   - En consultas de movimientos y transferencias hay validaciones críticas comentadas.
   - Riesgo: un usuario podría operar o consultar cuentas de otros si conoce el número.

3. **Credenciales de base de datos hardcodeadas**
   - `application.properties` contiene `root` y contraseña fija.
   - Recomendación: variables de entorno + perfiles (`dev`, `prod`).

4. **Plantillas faltantes para rutas admin**
   - El controlador admin retorna vistas `admin/crear-cliente`, `admin/crear-cuenta` y `admin/desbloquear`, pero actualmente no existen esos HTML en el repo.
   - Resultado esperado: error en tiempo de ejecución al navegar a esas rutas.

### Prioridad MEDIA

5. **Inconsistencias de rutas redirect**
   - Hay redirects con typos o sin slash inicial (`/ajero/...`, `redirect:cajero/...`).
   - Puede romper navegación y manejo de errores.

6. **Uso de `double` para dinero**
   - En banca puede producir errores de precisión.
   - Recomendación: migrar a `BigDecimal` y redondeo explícito.

7. **Operaciones sin transacción explícita en servicios críticos**
   - Transferencias actualizan dos cuentas y registran movimientos; conviene `@Transactional` para atomicidad.

8. **Lógica de retiro duplicada**
   - Hay implementación en `MovimientoService` y otra en `RetiroService`.
   - Recomendación: centralizar una sola ruta de negocio.

9. **Vista de movimientos no alineada al enum real**
   - HTML evalúa tipo `DEBITO`, pero el enum usa `RETIRO`, `TRANSFERENCIA`, `CONSIGNACION`, `CONSULTA`.
   - Impacto: estilos/signos de débito/crédito pueden mostrarse mal.

### Prioridad BAJA

10. **Calidad de frontend mejorable**
   - `login.html` tiene doble `<!DOCTYPE html>`.
   - `transferir.html` tiene `<!DOCTYPE html>` extra al final.
   - `admin/index.html` referencia `admin.css` inexistente en `static/styles`.

11. **Pruebas automáticas muy básicas**
   - Sólo existe `contextLoads()`; no valida reglas de negocio ni controladores.

12. **`mvnw` sin permiso de ejecución**
   - En este estado no corre directo con `./mvnw` en algunos entornos.

## 4) Mapa funcional actual (resumen corto)

- **Admin**: crear cliente, crear cuenta, desbloquear cliente.
- **Cajero**: login por número de cuenta + PIN, menú, consultas de saldo/movimientos, retiro, consignación, transferencia, cambio de clave, logout.
- **Persistencia**: MySQL con JPA/Hibernate, `ddl-auto=update`.

## 5) Recomendaciones prácticas (plan 30/60/90)

### Primer bloque (rápido, 1-2 días)
- Corregir redirects mal escritos.
- Restaurar y reforzar validaciones de pertenencia de cuenta.
- Eliminar hardcode de credenciales y pasar a variables de entorno.
- Agregar plantillas faltantes del módulo admin.

### Segundo bloque (1 semana)
- Migrar PIN a hash seguro (`BCrypt`).
- Unificar lógica de retiro y marcar servicios críticos con `@Transactional`.
- Corregir lógica visual de movimientos según enum real.

### Tercer bloque (1-2 semanas)
- Migrar montos de `double` a `BigDecimal`.
- Añadir pruebas unitarias/integración de casos críticos:
  - bloqueo por intentos,
  - retiro con saldo insuficiente,
  - transferencia entre cuentas,
  - validación de pertenencia.

## 6) “Summary para recordar” (ultra corto)

> Proyecto bien estructurado y funcional para un cajero web, con buena base en Spring Boot/JPA.
> Lo más importante a reforzar es: **seguridad (PIN hash + autorizaciones), consistencia transaccional, y completar vistas/pruebas**.
> Con esas mejoras, pasa de prototipo académico fuerte a solución mucho más robusta y presentable.
