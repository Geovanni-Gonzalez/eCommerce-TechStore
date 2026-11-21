"use client";

import Image from "next/image";
import { useState } from "react";

type Props = { name: string; imageUrl: string | null; priority?: boolean };

/**
 * La API V1 aún no devuelve imageUrl en los fixtures: no inventamos fotografías.
 * Si la API publica una HTTPS válida, se presenta; ante error, fallback explícito.
 */
export default function ProductPhoto({ name, imageUrl, priority = false }: Props) {
  const [failed, setFailed] = useState(false);
  if (imageUrl && !failed) {
    return (
      <div className="catalog-photo">
        <Image
          src={imageUrl}
          alt={`Fotografía de ${name}`}
          width={640}
          height={440}
          priority={priority}
          unoptimized
          className="catalog-photo__image"
          onError={() => setFailed(true)}
        />
      </div>
    );
  }

  return (
    <div className="catalog-photo catalog-photo--empty" role="img"
      aria-label={`Fotografía no disponible para ${name}`}>
      <svg viewBox="0 0 80 80" width="70" height="70" fill="none"
        aria-hidden="true" focusable="false">
        <rect x="12" y="18" width="56" height="44" rx="8"
          stroke="currentColor" strokeWidth="3" />
        <circle cx="30" cy="33" r="5" stroke="currentColor" strokeWidth="3" />
        <path d="m15 53 14-12 10 8 10-11 16 16"
          stroke="currentColor" strokeWidth="3" strokeLinejoin="round" />
      </svg>
      <span>Imagen no disponible</span>
    </div>
  );
}
