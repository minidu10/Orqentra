import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";

export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    // 5173 is the origin the gateway's CORS config already allows.
    port: 5173,
    strictPort: true,
  },
});
