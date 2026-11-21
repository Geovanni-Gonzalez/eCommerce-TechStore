import type { Metadata } from "next";
import LoginForm from "./LoginForm";

export const metadata: Metadata = {
  title: "Ingresar | TechStore",
  description: "Iniciar sesión con la cuenta TechStore. Acceso según rol.",
  robots: { index: false, follow: false },
  referrer: "no-referrer",
};

export default function LoginPage() {
  return <LoginForm />;
}
