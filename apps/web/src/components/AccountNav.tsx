"use client";

import Link from "next/link";
import { useAuth } from "./AuthProvider";
import { roleDestination } from "@/lib/auth";

export default function AccountNav() {
  const { user } = useAuth();
  return (
    <li>
      <Link href={user ? roleDestination(user.role) : "/login"}>
        {user ? "Mi cuenta" : "Ingresar"}
      </Link>
    </li>
  );
}
