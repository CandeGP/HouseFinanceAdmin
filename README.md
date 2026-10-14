# HouseFinanceAdmin

Registro de gastos de tarjetas de crédito compartido entre dos personas (MXN).

- **Android** (`android/`): Kotlin + Jetpack Compose + supabase-kt.
- **Backend** (`supabase/`): Postgres con RLS, funciones de ciclo de tarjeta (corte/pago, MSI) y pruebas pgTAP.

## Reglas de negocio

- El día de corte pertenece al ciclo que cierra; lo del día siguiente entra al corte siguiente.
- Cada persona edita solo sus propios datos; la vista compartida del hogar es de solo lectura.
- MSI: se registra el número de mensualidades y **en cuál va**; cada ciclo posterior carga una mensualidad.
- % de uso = (total del ciclo + saldo MSI pendiente) / límite de crédito.

## Desarrollo local

Requisitos: Docker Desktop, Node, JDK 17, Android SDK 34.

```bash
# Backend
npx supabase start
npx supabase test db        # pruebas pgTAP

# Android: crear android/local.properties (no versionado)
#   sdk.dir=...
#   supabase.url=http://10.0.2.2:54321      # emulador (con teléfono por USB: http://127.0.0.1:54321 + adb reverse tcp:54321 tcp:54321)
#   supabase.key=<publishable key de `npx supabase status`>
cd android
./gradlew testDebugUnitTest
./gradlew installDebug
```

## Estado

Hecho: cuentas de pareja con invitación, tarjetas, gastos (contado/MSI, notas), resumen por tarjeta,
gastos por mes/categoría/tarjeta.
Pendiente: captura automática (notificaciones/correo), estados de cuenta, pagos en efectivo con evidencia,
panel web, alertas y presupuestos.
