import { apiFetch } from "./client";
import type {
  DlqMessage,
  DlqTopic,
  LoginResponse,
  Order,
  Page,
  Payment,
  Product,
  RegisterResponse,
  ReplayResult,
  Stock,
  StuckOrder,
} from "../types";

export const api = {
  login: (email: string, password: string) =>
    apiFetch<LoginResponse>(
      "/api/auth/login",
      { method: "POST", body: JSON.stringify({ email, password }) },
      // A 401 here means the credentials were wrong, not that a session lapsed.
      { credentialsEndpoint: true },
    ),

  register: (email: string, password: string, restaurantName: string) =>
    apiFetch<RegisterResponse>(
      "/api/auth/register",
      { method: "POST", body: JSON.stringify({ email, password, restaurantName }) },
      { credentialsEndpoint: true },
    ),

  products: () => apiFetch<Product[]>("/api/products"),

  stock: (sku: string) => apiFetch<Stock>(`/api/inventory/${sku}`),

  orders: (page = 0, size = 20) =>
    apiFetch<Page<Order>>(`/api/orders?page=${page}&size=${size}`),

  order: (reference: string) => apiFetch<Order>(`/api/orders/${reference}`),

  /**
   * The body carries items only. There is no restaurant field to send: the server takes
   * it from the token, so a client cannot order on another restaurant's behalf.
   */
  placeOrder: (items: { sku: string; quantity: number }[]) =>
    apiFetch<Order>("/api/orders", {
      method: "POST",
      body: JSON.stringify({ items }),
    }),

  payment: (reference: string) =>
    apiFetch<Payment>(`/api/payments/${reference}`),

  dlqTopics: () => apiFetch<DlqTopic[]>("/api/admin/dlq/topics"),

  dlqMessages: (topic: string) =>
    apiFetch<DlqMessage[]>(`/api/admin/dlq/${topic}`),

  replayDlq: (topic: string) =>
    apiFetch<ReplayResult>(`/api/admin/dlq/${topic}/replay`, { method: "POST" }),

  stuckOrders: () => apiFetch<StuckOrder[]>("/api/admin/stuck-orders"),
};
