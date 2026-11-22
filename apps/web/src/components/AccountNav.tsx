"use client";

import Link from "next/link";
import { useAuth } from "./AuthProvider";
import { roleDestination } from "@/lib/auth";

export default function AccountNav({onNavigate}: {onNavigate?: () => void}) {
  const { user } = useAuth();
  return (
    <li>
      <Link onClick={onNavigate} href={user ? roleDestination(user.role) : "/login"}>
        {user ? "Mi cuenta" : "Ingresar"}
      </Link>
    </li>
  );
}
