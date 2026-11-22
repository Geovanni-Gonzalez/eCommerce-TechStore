"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/components/AuthProvider";
import { adminAccessCheck } from "@/lib/auth-client";
import AdminCatalogManager from "./AdminCatalogManager";
import AdminOrdersPanel from "./AdminOrdersPanel";
import AdminInventoryPanel from "./AdminInventoryPanel";

export default function AdminClient() {
  const { user, getToken, logout } = useAuth();
  const router = useRouter();
  const token = getToken();
  const [verified, setVerified] = useState<string | null>(null);
  const [error, setError] = useState("");
  const [section, setSection] = useState<"orders" | "inventory" | "catalog">("orders");

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
    <section className="admin-page" aria-labelledby="admin-title">
      <p className="eyebrow">Acceso administrativo · TechStore</p>
      <h1 id="admin-title">Administración</h1>
      {verified !== token ? (
        <p role={error ? "alert" : "status"}>{error || "Verificando permisos en el servidor…"}</p>
      ) : (
        <>
          <p role="status">Permiso ADMIN confirmado por la API.</p>
          <nav className="admin-sections" aria-label="Secciones administrativas">
            <button type="button" aria-pressed={section === "orders"}
              className="admin-section-button"
              onClick={() => setSection("orders")}>Pedidos</button>
            <button type="button" aria-pressed={section === "inventory"}
              className="admin-section-button"
              onClick={() => setSection("inventory")}>Inventario</button>
            <button type="button" aria-pressed={section === "catalog"}
              className="admin-section-button"
              onClick={() => setSection("catalog")}>Catálogo</button>
          </nav>
          {section === "orders" && <AdminOrdersPanel key={token} token={token} />}
          {section === "inventory" && <AdminInventoryPanel key={token} token={token} />}
          {section === "catalog" && <AdminCatalogManager key={token} token={token} />}
        </>
      )}
      <button type="button" className="auth-button" onClick={() => {
        void logout().then(() => router.replace("/login"));
      }}>Cerrar sesión</button>
    </section>
  );
}
