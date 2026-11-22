/**
 * Build-time public API URL for the TechStore demo.
 * Keep local development pointed at localhost; when built on Vercel, use the
 * Render demo API if no NEXT_PUBLIC_API_URL is configured in the project.
 * This is a PUBLIC endpoint, never a place for tokens or secrets.
 */
const publicApiUrl = process.env.NEXT_PUBLIC_API_URL?.trim() || (
  process.env.VERCEL === "1"
    ? "https://techstore-api-demo-v9nb.onrender.com"
    : "http://localhost:8080"
);

/** @type {import('next').NextConfig} */
const nextConfig = {
  env: { NEXT_PUBLIC_API_URL: publicApiUrl },
};

export default nextConfig;
