import type { Metadata, Viewport } from "next";
import localFont from "next/font/local";
import "./globals.css";
import { Shell } from "@/components/Shell";

const jakarta = localFont({
  variable: "--font-jakarta",
  display: "swap",
  src: [
    { path: "./fonts/plus_jakarta_sans_regular.ttf", weight: "400" },
    { path: "./fonts/plus_jakarta_sans_medium.ttf", weight: "500" },
    { path: "./fonts/plus_jakarta_sans_semibold.ttf", weight: "600" },
    { path: "./fonts/plus_jakarta_sans_bold.ttf", weight: "700" },
    { path: "./fonts/plus_jakarta_sans_extrabold.ttf", weight: "800" },
  ],
});

export const metadata: Metadata = {
  title: "Finanzas en pareja",
  applicationName: "Finanzas en pareja",
  // iOS: al agregarla a la pantalla de inicio se abre a pantalla completa, sin la barra de Safari.
  appleWebApp: { capable: true, title: "Finanzas", statusBarStyle: "black-translucent" },
};

export const viewport: Viewport = {
  width: "device-width",
  initialScale: 1,
  viewportFit: "cover", // usa toda la pantalla; el contenido respeta las zonas seguras con env(safe-area-inset-*)
  themeColor: [
    { media: "(prefers-color-scheme: dark)", color: "#0b1b3a" },
    { media: "(prefers-color-scheme: light)", color: "#e9e4ff" },
  ],
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="es" className={jakarta.variable}>
      <body className="min-h-screen text-ink antialiased">
        <Shell>{children}</Shell>
      </body>
    </html>
  );
}
