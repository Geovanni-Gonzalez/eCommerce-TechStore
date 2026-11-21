"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { useAuth } from "@/components/AuthProvider";
import { addCartItem, CartHttpError } from "@/lib/cart-api";

export default function AddToCartButton({
  productId, name, stock,
}: { productId: number; name: string; stock: number }) {
  const { user, getToken, logout } = useAuth();
  const router = useRouter();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  if (stock < 1) {
    return <span className="cart-add-disabled" aria-label={name + ": agotado"}>Agotado</span>;
  }
  if (!user) return <Link className="cart-add-link" href="/login">Ingresar para añadir</Link>;
  if (user.role !== "CLIENT") {
    return <span className="cart-add-disabled">Solo disponible para clientes</span>;
  }
  async function add() {
    if (busy) return;
    const token = getToken();
    if (!token) {
      setError("Inicia sesión de nuevo.");
      return;
    }
    setBusy(true);
    setError("");
    try {
      await addCartItem(token, productId, 1);
      if (getToken() === token) router.push("/carrito");
    } catch (cause: unknown) {
      if (cause instanceof CartHttpError && cause.status === 401 && getToken() === token) {
        await logout();
      }
      if (getToken() === token || !getToken()) {
        setError(cause instanceof Error ? cause.message : "No se pudo añadir el artículo.");
      }
    } finally {
      setBusy(false);
    }
  }
  return (
    <div className="cart-add">
      <button type="button" className="cart-primary" disabled={busy}
        onClick={() => void add()}>
        {busy ? "Agregando…" : "Añadir al carrito"}
      </button>
      {error && <p role="alert" className="cart-error">{error}</p>}
    </div>
  );
}
