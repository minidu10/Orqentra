export function money(value: number): string {
  return new Intl.NumberFormat("en-LK", {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(value);
}

export function relativeTime(iso: string | null | undefined): string {
  // The POST /api/orders response has no createdAt: the column is filled by a database
  // default and is not read back on insert. Only the list endpoint carries it, so a
  // missing value renders as nothing rather than as 1970.
  if (!iso) return "just now";

  const then = new Date(iso).getTime();
  if (Number.isNaN(then) || then === 0) return "";

  const seconds = Math.round((Date.now() - then) / 1000);
  if (seconds < 60) return `${seconds}s ago`;
  if (seconds < 3600) return `${Math.floor(seconds / 60)}m ago`;
  if (seconds < 86400) return `${Math.floor(seconds / 3600)}h ago`;
  return `${Math.floor(seconds / 86400)}d ago`;
}
