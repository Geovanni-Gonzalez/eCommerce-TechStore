import type { Metadata } from "next";
import RegisterForm from "./RegisterForm";

export const metadata: Metadata = {
  title: "Crear cuenta | TechStore",
  description: "Registro de clientes TechStore con validación segura en la API.",
  robots: { index: false, follow: false },
  referrer: "no-referrer",
};

export default function RegisterPage() {
  return <RegisterForm />;
}
