import type { Metadata } from "next";
import AdminClient from "./AdminClient";
import "./admin.css";

export const metadata: Metadata = {
  title: "Gestión de catálogo | TechStore",
  robots: { index: false, follow: false },
  referrer: "no-referrer",
};

export default function AdminPage() {
  return <AdminClient />;
}
