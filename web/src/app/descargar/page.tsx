import type { Metadata } from "next";

export const metadata: Metadata = { title: "Descargar la app · Finanzas en pareja" };

const releasesBase = `${process.env.NEXT_PUBLIC_SUPABASE_URL ?? ""}/storage/v1/object/public/releases`;
const apkUrl = `${releasesBase}/finanzas.apk`;

interface Release {
  versionName: string;
  sha256: string;
  sizeBytes: number | null;
  notes: string | null;
}

// Respaldo por si el manifiesto no responde; el dato vigente sale de latest.json (lo publica el script).
const FALLBACK: Release = {
  versionName: "1.0.2",
  sha256: "cd772ca6b40b8478dc905ed11a32eb3f3b35ac0760ab3864b3d76fd76996f7b7",
  sizeBytes: null,
  notes: null,
};

async function loadRelease(): Promise<Release> {
  try {
    const res = await fetch(`${releasesBase}/latest.json`, { next: { revalidate: 60 } });
    if (!res.ok) return FALLBACK;
    const data = await res.json();
    return {
      versionName: String(data.versionName),
      sha256: String(data.sha256),
      sizeBytes: typeof data.sizeBytes === "number" ? data.sizeBytes : null,
      notes: data.notes ? String(data.notes) : null,
    };
  } catch {
    return FALLBACK;
  }
}

const STEPS = [
  "Toca el botón y descarga el archivo en tu teléfono Android.",
  "Ábrelo. Si Android pregunta, permite instalar apps desde tu navegador (es normal: no está en Play Store).",
  "Abre Finanzas, inicia sesión con tu cuenta y activa las alertas y el acceso a notificaciones en Cuenta.",
];

export default async function Descargar() {
  const release = await loadRelease();
  const size = release.sizeBytes ? `${(release.sizeBytes / 1024 / 1024).toFixed(1)} MB` : null;

  return (
    <main className="mx-auto flex min-h-screen max-w-xl flex-col justify-center gap-8 px-6 py-16">
      {/* eslint-disable-next-line @next/next/no-img-element */}
      <img src="/icon.svg" alt="Logo de Finanzas en pareja" width={72} height={72} className="rounded-2xl shadow-card" />

      <header>
        <h1 className="text-[clamp(40px,9vw,64px)] font-bold leading-[0.95] tracking-[-0.05em]">
          Finanzas en pareja para Android
        </h1>
        <p className="mt-4 text-lg text-muted">
          Tus tarjetas, cortes y pagos en un solo lugar. Versión {release.versionName}
          {size ? ` · ${size}` : ""}.
        </p>
        {release.notes ? <p className="mt-2 text-sm text-muted">{release.notes}</p> : null}
      </header>

      <a
        href={apkUrl}
        className="inline-flex w-fit items-center rounded-full bg-ink px-6 py-4 text-base font-semibold text-canvas shadow-btn"
      >
        Descargar APK
      </a>

      <ol className="flex flex-col gap-4">
        {STEPS.map((step, index) => (
          <li key={step} className="flex gap-4 rounded-xl bg-card p-5 shadow-card">
            <span className="flex size-8 shrink-0 items-center justify-center rounded-full bg-mist text-sm font-bold">
              {index + 1}
            </span>
            <span>{step}</span>
          </li>
        ))}
      </ol>

      <p className="break-all text-xs text-smoke">
        SHA-256 del archivo: <code>{release.sha256}</code>
      </p>
    </main>
  );
}
