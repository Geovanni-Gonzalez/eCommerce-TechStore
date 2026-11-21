"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/components/AuthProvider";
import { adminAccessCheck } from "@/lib/auth-client";

export default function AdminClient() {
  const { user, getToken, logout } = useAuth();
  const router = useRouter();
  const token = getToken();
  const [verified, setVerified] = useState<string | null>(null);
  const [error, setError] = useState("");

  useEffect(() => {
    if (user?.role !== "ADMIN" || !token) return;
    let active = true;
    void adminAccessCheck(token).then(() => {
      if (active) setVerified(token);
    }).catch(() => {
      if (active) {
        setVerified(null);
        setError("No se pudo verificar el rol de administrador con el servidor.");
      }
    });
    return () => { active = false; };
  }, [user, token]);

  if (!user || !token || user.role !== "ADMIN") {
    return (
      <section className="auth-card" aria-labelledby="admin-title">
        <h1 id="admin-title">Acceso restringido</h1>
        <p>Solo una cuenta ADMIN verificada puede acceder a esta sección.</p>
        <Link href="/login">Iniciar sesión</Link>
      </section>
    );
  }
  return (
    <section className="auth-card" aria-labelledby="admin-title">
      <p className="eyebrow">Acceso administrativo · TechStore</p>
      <h1 id="admin-title">Administración</h1>
      {verified !== token ? (
        <p role={error ? "alert" : "status"}>{error || "Verificando permisos en el servidor…"}</p>
      ) : (
        <>
          <p role="status">Permiso ADMIN confirmado por la API.</p>
          <p>La gestión visual de productos e inventario se implementará en
            BKL-023, BKL-024 y BKL-046. Esta pantalla solo comprueba autorización.</p>
        </>
      )}
      <button type="button" className="auth-button" onClick={() => {
        void logout().then(() => router.replace("/login"));
      }}>Cerrar sesión</button>
    </section>
  );
}
