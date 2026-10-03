# Load Generator (Go)

Herramienta de testing que reproduce contención real sobre la API de bookings
mediante HTTP: lanza N clientes simultáneos a reservar el mismo recurso en el
mismo intervalo y verifica el invariante central del proyecto.

Es una herramienta de prueba, **no** parte del runtime de la aplicación Java
(ver `docs/SPEC.md` → Load Testing). Usa **solo la stdlib de Go** (`net/http`,
`sync`, `encoding/json`): cero dependencias externas.

## Cómo se ejecuta

Requisitos previos (el generador no arranca nada por su cuenta):

```bash
just db-up          # PostgreSQL en :5433
just run            # la app en otra terminal, health UP en :8080
just load-test http://localhost:8080 20   # URL y N opcionales (defaults :8080 y 50)
```

Flags:

| Flag | Default | Significado |
|---|---|---|
| `-url` | `http://localhost:8080` | URL base de la API |
| `-n` | `50` | cantidad de clientes concurrentes |
| `-start` | mañana 10:00 UTC | inicio del intervalo (RFC3339) |
| `-end` | `-start` + 1h | fin del intervalo (RFC3339) |

Ejemplo de salida:

```
user=2 resource=1 interval=[2026-10-03T18:00:00Z, 2026-10-03T19:00:00Z) racers=20
201: 1  409: 19  other: 0
latency ms: p50=77.2 p95=78.8 max=78.8
INVARIANT OK: exactly one booking won the interval
```

**Exit code:** `0` si exactamente 1 request obtuvo 201; `1` en cualquier otro
caso (invariante violado, errores de red, fallo al crear user/resource).
Pensado para que CI (Phase 8) haga fail del pipeline.

## Qué hace, paso a paso

1. **Setup por la API** (`createID`): crea un `user` con email único por
   ejecución (`loadgen-<unixmilli>@example.com`) y un `resource`
   (`Load Room <unixmilli>`). Los identificadores únicos evitan el 409 de
   email duplicado y garantizan un recurso fresco sin reservas previas:
   el único conflicto posible es el que el propio generador provoca.
2. **Carrera** (`race`): suelta los N clientes *al mismo tiempo* contra
   `POST /api/bookings` con el mismo `userId`, `resourceId` e intervalo.
3. **Conteo y métricas:** clasifica cada respuesta (201 / 409 / otro),
   mide la latencia de cada request, imprime p50/p95/max.
4. **Veredicto:** imprime `INVARIANT OK` o `INVARIANT VIOLATED` y fija el
   exit code. Verifica también el estado final en BD sería redundante:
   el invariante observable por HTTP (1 × 201) es equivalente, y la BD
   queda verificable a mano con `just psql`.

## Anatomía del código

### Flags e intervalo

La stdlib de Go trae parser de flags (`flag.String`, `flag.Int`) — el
equivalente de los args/properties de Spring. `interval()` valida con
`start.Before(end)` y sale con error si no; en Go los errores se **retornan**
y se manejan con `if err != nil`, no se lanzan (no existe try/catch).

```go
start = time.Now().UTC().Add(24 * time.Hour).Truncate(time.Hour)
```

`Truncate` redondea a la hora para un default determinista y legible.

### El HTTP client afinado

```go
Transport: &http.Transport{
    MaxIdleConns:        *racers,
    MaxIdleConnsPerHost: *racers,
}
```

Go reutiliza conexiones TCP (keep-alive), pero el default del `Transport`
solo conserva **2** conexiones inactivas por host. Con N clientes
simultáneos, N-2 tendrían que reabrir conexión (handshake) por request:
medirías el handshake, no el servidor. Elevar el pool a N hace que todas
las goroutines reusen conexiones. En Java lo hace el connection pool
(Hikari) transparentemente; aquí es explícito. `Timeout: 30s` evita que un
servidor colgado bloquee al generador para siempre.

### `createID` — setup por HTTP

El generador solo conoce la API, nunca la BD. `json.Marshal` serializa el
body; la respuesta se decodifica en el struct mínimo:

```go
type idOnly struct {
    ID int64 `json:"id"`
}
```

Solo declara el campo que le interesa (misma filosofía que Jackson: lo no
declarado se ignora). Si el status no es 201, devuelve el body dentro del
error para diagnóstico.

### `race` — el patrón barrera

```go
start := make(chan struct{})   // canal de arranque
var wg sync.WaitGroup
wg.Add(racers)                 // contador = N
for i := 0; i < racers; i++ {
    go func(slot int) {        // goroutine = hilo ligero
        defer wg.Done()        // contador-- al terminar
        <-start                // bloquea hasta que el canal se cierre
        ... client.Post ...    // la carrera
    }(i)
}
close(start)                   // libera a TODAS de golpe
wg.Wait()                      // espera a que todas terminen
```

- **Barrera:** cerrar un canal desbloquea a todos los lectores pendientes a
  la vez — idiomático Go, cumple el rol de `CyclicBarrier`.
- **`WaitGroup`:** el rol de `CountDownLatch` (`Add`/`Done`/`Wait`).
- **Sin atomics:** cada goroutine escribe **solo su slot** del array
  `results[slot] = ...`. No hay dos escritores sobre la misma posición, así
  que no hay data race ni hace falta `AtomicInteger`/locks: la ausencia de
  compartición es la ausencia de carrera.
- Cada goroutine mide su propia latencia con `time.Since(begin)`.

Cada goroutine hace `defer resp.Body.Close()` y drena el body con
`io.Copy(io.Discard, ...)`: en Go un body no leído/drenado impide que la
conexión vuelva al pool de keep-alive (y filtraría conexiones).

### Conteo, percentiles y veredicto

Las latencias se acumulan en un slice, `sort.Float64s` y:

```go
func percentile(sorted []float64, p int) float64 {
    index := (p * len(sorted)) / 100  // clamp al último
    return sorted[index]
}
```

Un index-pick sobre el slice ordenado: sin librería de métricas porque
p50/p95 de N muestras no necesitan más. La clasificación es un `switch`:

```go
case r.code == http.StatusCreated:  successes++
case r.code == conflictHTTP:        conflicts++
default:                            errors++
```

## El invariante

`éxitos == 1` es la versión HTTP del assert del test de concurrencia Java
(`BookingConcurrencyTest`): con la constraint de exclusión en PostgreSQL
(`docs/decisions/003`), la BD serializa los inserts en carrera — la
perdedora espera el commit de la ganadora y recibe la violación, que la app
traduce a 409. Si el generador observa otro resultado, o la app cambió su
comportamiento o el invariante está roto: exit 1.

Niveles de verificación del mismo invariante:

| Nivel | Herramienta | Qué prueba |
|---|---|---|
| SQL | constraint `V2` | el árbitro existe |
| Service | `BookingConcurrencyTest` (8 hilos) | transacciones reales, un ganador |
| HTTP | este generador (N clientes) | contención de clientes reales + latencias |

## Equivalencias Java ↔ Go

| Concepto | Java (test de concurrencia) | Go (generador) |
|---|---|---|
| Unidades de ejecución | `ExecutorService` + threads | goroutines (`go func()`) |
| Barrera de salida | `CyclicBarrier` | canal cerrado (`close(start)`) |
| Espera de finalización | `CountDownLatch` / `Future.get` | `sync.WaitGroup` |
| Contadores compartidos | `AtomicInteger` | slots dedicados por goroutine |
| JSON | Jackson + DTOs | `encoding/json` + struct tags |
| Errores | try/catch + excepciones | `if err != nil` |
| HTTP client | `RestClient`/`HttpClient` | `net/http` + `http.Transport` |

## Qué NO hace (por diseño)

- No autentica (la API está abierta hasta Phase 6; si auth llega, se añade
  token al client).
- No arranca Docker, la BD ni la app.
- No importa/exporta métricas (Prometheus/Grafana): stdout + exit code.
- No genero contención multi-intervalo ni multi-recurso: un intervalo por
  ejecución, foco total en el invariante.
