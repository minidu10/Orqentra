/**
 * Mirrors of the actual API responses, taken from the JSON the services return rather
 * than from assumption. Money arrives as a JSON number.
 */

export type OrderStatus =
  | "PENDING"
  | "AWAITING_PAYMENT"
  | "CONFIRMED"
  | "CANCELLED";

export interface OrderItem {
  sku: string;
  quantity: number;
  unitPrice: number;
}

export interface Order {
  reference: string;
  restaurantId: string;
  status: OrderStatus;
  total: number;
  cancellationReason: string | null;
  /** Null on the POST response: database-generated and not read back on insert. */
  createdAt: string | null;
  items: OrderItem[];
}

/** Spring Data page, trimmed to the fields the UI actually reads. */
export interface Page<T> {
  content: T[];
  number: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
  empty: boolean;
}

export interface Product {
  id: number;
  sku: string;
  name: string;
  price: number;
  createdAt: string;
}

export interface Stock {
  sku: string;
  available: number;
}

export interface Payment {
  orderReference: string;
  amount: number;
  status: "SUCCEEDED" | "FAILED";
  failureReason: string | null;
}

export interface DlqTopic {
  topic: string;
  messageCount: number;
}

export interface DlqMessage {
  partition: number;
  offset: number;
  key: string | null;
  payload: string | null;
  originalTopic: string | null;
  exceptionType: string | null;
  failureReason: string | null;
}

export interface ReplayResult {
  topic: string;
  replayedTo: string;
  replayed: number;
}

export interface StuckOrder {
  reference: string;
  status: OrderStatus;
  createdAt: string;
  ageSeconds: number;
}

export interface LoginResponse {
  token: string;
  expiresAt: string;
}

export interface RegisterResponse {
  email: string;
  restaurantRef: string;
}

/** What the notification service pushes. Names are order.status and stock.released. */
export interface OrderEventMessage {
  orderReference: string;
  status: OrderStatus | null;
  message: string;
  reason: string | null;
  timestamp: string;
}

export interface ProblemDetail {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  instance?: string;
}

export type ConnectionState = "connecting" | "connected" | "reconnecting" | "disconnected";

export interface CartLine {
  sku: string;
  name: string;
  unitPrice: number;
  quantity: number;
}
