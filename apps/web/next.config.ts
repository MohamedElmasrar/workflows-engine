import type { NextConfig } from "next";

// The whole UI is rendered in the browser behind Keycloak, so Cache Components /
// Partial Prefetching are intentionally left disabled (see README, "Rendering model").
const nextConfig: NextConfig = {
  output: "standalone",
  reactStrictMode: true,
  poweredByHeader: false,
  turbopack: {
    rules: {
      "*.css": {
        loaders: ["@tailwindcss/turbopack"],
        as: "*.css",
      },
    },
  },
};

export default nextConfig;
