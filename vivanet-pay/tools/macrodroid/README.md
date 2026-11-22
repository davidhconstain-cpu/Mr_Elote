# Plantillas MacroDroid para Vivanet Pay

| Archivo | Uso |
|---|---|
| `payload-template.json` | Cuerpo JSON que la acción **Solicitud HTTP** de MacroDroid debe enviar por cada notificación de Nequi. |
| `payload-template.form.txt` | Mismo mensaje como `application/x-www-form-urlencoded` (una línea): úselo si las notificaciones traen comillas y rompen el JSON. |
| `payload-prueba.json` | Igual, con `"test": true`: comprueba la conexión **sin crear pagos**. |
| `probar-receptor.ps1` | Envía una prueba desde un PC con Windows (pide el secreto de forma oculta). |

Guía paso a paso: [`docs/MACRODROID.md`](../../docs/MACRODROID.md).

## Contrato HTTP (resumen técnico)

`POST /v1/events` — `Content-Type: application/json` **o** `application/x-www-form-urlencoded` (mismos campos, UTF-8, sin campos repetidos/desconocidos; en HMAC se firma el cuerpo crudo igual) — cuerpo ≤ 16 KB (configurable).

Autenticación según `connector.android.auth`:

- `token`: `Authorization: Bearer <secreto>` (MacroDroid).
- `hmac` (por defecto): `X-Vivanet-Timestamp` (epoch s, ±300 s), `X-Vivanet-Nonce` (16–128 `[A-Za-z0-9_-]`, un solo uso),
  `X-Vivanet-Signature: v1=<hex HMAC-SHA256(secreto, "v1\n"+timestamp+"\n"+nonce+"\n"+cuerpo)>`.

Campos: `schemaVersion`=1 (obl.), `eventId` 1–128 (obl., único y estable: los reintentos deben repetir el mismo),
`title` 0–256 (obl.), `text` 1–4096 (obl.), `capturedAt` ISO-8601 con zona o epoch s/ms (obl.),
`source`=`ANDROID_NEQUI`, `app` ≤128, `device` ≤64, `test` booleano. Campos desconocidos ⇒ 400.

Respuestas: `202 accepted` (nuevo) · `200 duplicate` (mismo `eventId`, no crea otro movimiento) · `200 test_ok` ·
`202 processing` (el núcleo tarda; reintentar es seguro) · `400` esquema/JSON · `401` autenticación · `404` · `405` método ·
`409` nonce repetido · `413` tamaño · `415` tipo · `429` tasa (`Retry-After`) · `503` en pausa/ocupado · `500` reintentar.

`GET /health` ⇒ `{"status":"ok","schemaVersion":1}` (o `"paused"`).
