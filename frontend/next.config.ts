import type { NextConfig } from "next";

const gateway = process.env.GATEWAY_URL ?? "http://localhost:8080";

const nextConfig: NextConfig = {
  async rewrites() {
    return [
      { source: "/api/:path*", destination: `${gateway}/api/:path*` },
      { source: "/gw/:path*", destination: `${gateway}/gw/:path*` },
      { source: "/actuator/:path*", destination: `${gateway}/actuator/:path*` },
    ];
  },
};

export default nextConfig;
