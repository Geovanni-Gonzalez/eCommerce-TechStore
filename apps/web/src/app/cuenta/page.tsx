import type { Metadata } from "next";
import AccountClient from "./AccountClient";

export const metadata: Metadata = {
  title: "Mi cuenta | TechStore",
  robots: { index: false, follow: false },
  referrer: "no-referrer",
};

export default function AccountPage() {
  return <AccountClient />;
}
