import type { Metadata } from "next";

export const metadata: Metadata = { title: "Descargar la app · Finanzas en pareja" };

// Datos del APK publicado. Cámbialos al subir una versión nueva.
const VERSION = "1.0.0";
const SIZE = "21.7 MB";
const SHA256 = "f63ed5b2c756626f258a9167913b686d06b77be7f99caf0fc6db9f114494bf04";

const apkUrl = `${process.env.NEXT_PUBLIC_SUPABASE_URL ?? ""}/storage/v1/object/public/releases/finanzas.apk`;

const STEPS = [
  "Toca el botón y descarga el archivo en tu teléfono Android.",
  "Ábrelo. Si Android pregunta, permite instalar apps desde tu navegador (es normal: no está en Play Store).",
  "Abre Finanzas, inicia sesión con tu cuenta y activa las alertas y el acceso a notificaciones en Cuenta.",
];

export default function Descargar() {
  return (
    <main className="mx-auto flex min-h-screen max-w-xl flex-col justify-center gap-8 px-6 py-16">
      {/* eslint-disable-next-line @next/next/no-img-element */}
      <img src="/icon.svg" alt="Logo de Finanzas en pareja" width={72} height={72} className="rounded-2xl shadow-card" />

      <header>
        <h1 className="text-[clamp(40px,9vw,64px)] font-bold leading-[0.95] tracking-[-0.05em]">
          Finanzas en pareja para Android
        </h1>
        <p className="mt-4 text-lg text-muted">
          Tus tarjetas, cortes y pagos en un solo lugar. Versión {VERSION} · {SIZE}.
        </p>
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
        SHA-256 del archivo: <code>{SHA256}</code>
      </p>
    </main>
  );
}
