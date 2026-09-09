import type { OrderStatus } from "../types";

/** Colour carries the status meaning and nothing else. */
const STYLES: Record<OrderStatus, string> = {
  PENDING: "bg-stone-100 text-stone-700 ring-stone-300",
  AWAITING_PAYMENT: "bg-amber-50 text-amber-800 ring-amber-300",
  CONFIRMED: "bg-emerald-50 text-emerald-800 ring-emerald-300",
  CANCELLED: "bg-rose-50 text-rose-800 ring-rose-300",
};

const LABELS: Record<OrderStatus, string> = {
  PENDING: "Pending",
  AWAITING_PAYMENT: "Awaiting payment",
  CONFIRMED: "Confirmed",
  CANCELLED: "Cancelled",
};

export function StatusBadge({ status }: { status: OrderStatus }) {
  return (
    <span
      data-testid="status-badge"
      data-status={status}
      className={`inline-flex items-center rounded px-2 py-0.5 text-xs font-medium ring-1 ring-inset ${STYLES[status]}`}
    >
      {LABELS[status]}
    </span>
  );
}
