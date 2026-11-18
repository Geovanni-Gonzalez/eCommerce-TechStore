import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "TechStore | Prototipo",
  description: "Prototipo académico de e-commerce de accesorios tecnológicos",
};

export default function RootLayout({
  children,
}: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="es">
      <body>{children}</body>
    </html>
  );
}
