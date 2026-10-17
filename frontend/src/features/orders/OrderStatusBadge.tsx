import clsx from 'clsx';
import { statusLabels, type OrderStatus } from './api';

export function OrderStatusBadge({ status }: { status: OrderStatus }) {
  const { label, className } = statusLabels[status];
  return <span className={clsx('rounded-full px-2.5 py-0.5 text-xs font-semibold', className)}>{label}</span>;
}
