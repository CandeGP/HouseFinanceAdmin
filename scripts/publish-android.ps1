<#
  Publica el APK de release ya compilado en el bucket público `releases` de Supabase y actualiza
  `latest.json`, que es lo que consultan la app (ventana de actualización) y la página /descargar.

  Uso (desde la raíz del repo, con `npx supabase login` hecho y el proyecto vinculado):
    ./gradlew assembleRelease            # en android/
    ./scripts/publish-android.ps1 -Notes "Qué cambió en esta versión"
#>
param(
    [string]$Notes = "",
    [string]$Apk = "android/app/build/outputs/apk/release/app-release.apk"
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

if (-not (Test-Path $Apk)) { throw "No existe $Apk. Compila antes con: cd android; ./gradlew assembleRelease" }

# Versión real del APK (no la que "creemos" haber compilado)
$aapt = Get-ChildItem "$env:LOCALAPPDATA\Android\Sdk\build-tools\*\aapt.exe" | Sort-Object FullName | Select-Object -Last 1
$badging = & $aapt.FullName dump badging $Apk | Select-String "^package:" | Select-Object -First 1
if ($badging.Line -notmatch "name='mx\.finanzas\.pareja'" ) { throw "El APK no es mx.finanzas.pareja (¿build demo?)." }
$versionCode = [int]([regex]::Match($badging.Line, "versionCode='(\d+)'").Groups[1].Value)
$versionName = [regex]::Match($badging.Line, "versionName='([^']+)'").Groups[1].Value

$sha = (Get-FileHash $Apk -Algorithm SHA256).Hash.ToLower()
$size = (Get-Item $Apk).Length

# Origen público del bucket (URL del proyecto vinculado)
$ref = (Get-Content supabase/.temp/project-ref -ErrorAction Stop).Trim()
$base = "https://$ref.supabase.co/storage/v1/object/public/releases"

$manifest = [ordered]@{
    versionCode = $versionCode
    versionName = $versionName
    url         = "$base/finanzas.apk"
    sha256      = $sha
    sizeBytes   = $size
    notes       = $Notes
} | ConvertTo-Json

New-Item -ItemType Directory -Force samples | Out-Null
Copy-Item $Apk samples/finanzas.apk -Force
[IO.File]::WriteAllText((Join-Path $root "samples/latest.json"), $manifest, (New-Object Text.UTF8Encoding($false)))

$apkType = "application/vnd.android.package-archive"
function Publish($local, $name, $type, $cache) {
    # El CLI escribe avisos en stderr aunque salga bien: se juzga por el resultado, no por stderr.
    $previous = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        # Reemplaza si existe (el CLI responde 409 si el objeto ya está).
        npx supabase storage rm "ss:///releases/$name" --linked --experimental --yes 2>&1 | Out-Null
        $r = (npx supabase storage cp $local "ss:///releases/$name" --linked --experimental `
                --content-type $type --cache-control $cache 2>&1 | Out-String)
    } finally {
        $ErrorActionPreference = $previous
    }
    if ($r -notmatch '"uploaded"') { throw "Falló la subida de ${name}: $r" }
    "  subido: $name"
}

"Publicando $versionName (código $versionCode, $([math]::Round($size/1MB,1)) MB)..."
Publish "samples/finanzas.apk" "finanzas-$versionName.apk" $apkType "max-age=300"
Publish "samples/finanzas.apk" "finanzas.apk" $apkType "max-age=300"
# El manifiesto va al final, cuando el APK ya está disponible; caché corta para que el aviso llegue pronto.
Publish "samples/latest.json" "latest.json" "application/json" "max-age=60"

# Verificación pública: lo descargado debe ser idéntico a lo compilado
$tmp = New-TemporaryFile
Invoke-WebRequest -Uri "$base/finanzas.apk" -OutFile $tmp -UseBasicParsing
$remote = (Get-FileHash $tmp -Algorithm SHA256).Hash.ToLower()
Remove-Item $tmp -Force
if ($remote -ne $sha) { throw "El APK publicado NO coincide con el compilado." }
"OK: $versionName publicado y verificado (sha256 $sha)"
