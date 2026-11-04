import type { MetadataRoute } from "next";

/** Hace la web instalable ("Agregar a pantalla de inicio" en iPhone y Android) y la abre como app. */
export default function manifest(): MetadataRoute.Manifest {
  return {
    name: "Finanzas en pareja",
    short_name: "Finanzas",
    description: "Las finanzas de la casa, claras para los dos.",
    start_url: "/",
    display: "standalone",
    orientation: "portrait",
    background_color: "#0b1b3a",
    theme_color: "#0b1b3a",
    lang: "es",
    icons: [
      { src: "/icon.svg", sizes: "any", type: "image/svg+xml", purpose: "any" },
      { src: "/apple-icon", sizes: "180x180", type: "image/png", purpose: "any" },
    ],
  };
}
