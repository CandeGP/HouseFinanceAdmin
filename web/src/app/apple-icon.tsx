import { ImageResponse } from "next/og";

export const size = { width: 180, height: 180 };
export const contentType = "image/png";

/** Ícono de iOS (apple-touch-icon): casa sobre el degradado de la app. iOS no acepta SVG, por eso es PNG. */
export default function AppleIcon() {
  return new ImageResponse(
    (
      <div
        style={{
          width: "100%",
          height: "100%",
          display: "flex",
          alignItems: "center",
          justifyContent: "center",
          background: "linear-gradient(145deg, #7c6cff 0%, #ff6fa8 100%)",
        }}
      >
        <svg width="104" height="104" viewBox="0 0 108 108">
          <path fill="#ffffff" d="M54,16 L92,50 L82,50 L82,88 Q82,92 78,92 L30,92 Q26,92 26,88 L26,50 L16,50 Z" />
          <circle cx="54" cy="66" r="15" fill="#ffe45c" />
          <path d="M54,56 L54,76" stroke="#20294c" strokeWidth="4" strokeLinecap="round" />
        </svg>
      </div>
    ),
    size,
  );
}
