import clsx from 'clsx';
import { useState } from 'react';
import { Alert } from '../../components/ui/Alert';
import { Pagination } from '../../components/ui/Pagination';
import { Spinner } from '../../components/ui/Spinner';
import { formatDateTime } from '../../lib/format';
import { useAuditLog } from './api';
import { PageHeader, SelectField, TableWrap, td, th } from './components/AdminUi';

const ENTITY_TYPES: Record<string, string> = {
  PRODUCT: 'Products',
  CATEGORY: 'Categories',
  ORDER: 'Orders',
  COMPATIBILITY_RULE: 'Compatibility rules',
  GOAL_TEMPLATE: 'Goal templates',
};

/** "PRODUCT_UPDATED" → "Product updated" */
const humanize = (action: string) => action.charAt(0) + action.slice(1).toLowerCase().replaceAll('_', ' ');

/** Renders the JSON details compactly: {"price":{"from":10,"to":12}} → price: 10 → 12 */
function describe(details: string): string {
  try {
    const parsed = JSON.parse(details) as Record<string, unknown>;
    return Object.entries(parsed)
      .map(([key, value]) => {
        if (value && typeof value === 'object' && 'from' in value && 'to' in value) {
          const change = value as { from: unknown; to: unknown };
          return `${key}: ${String(change.from)} → ${String(change.to)}`;
        }
        return `${key}: ${String(value)}`;
      })
      .join(' · ');
  } catch {
    return details;
  }
}

export function ActivityPage() {
  const [entityType, setEntityType] = useState('');
  const [page, setPage] = useState(0);
  const { data, isPending, isError, isFetching } = useAuditLog(entityType, page);

  return (
    <>
      <PageHeader title="Activity log" description="Every change made in the admin area, who made it and when." />
      <SelectField
        label="Show"
        className="mb-4 max-w-xs"
        value={entityType}
        onChange={(e) => {
          setEntityType(e.target.value);
          setPage(0);
        }}
      >
        <option value="">All changes</option>
        {Object.entries(ENTITY_TYPES).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
      </SelectField>
      {isPending ? (
        <div className="flex justify-center py-16"><Spinner /></div>
      ) : isError ? (
        <Alert tone="error">The activity log could not be loaded.</Alert>
      ) : data.content.length === 0 ? (
        <p className="rounded-2xl border border-dashed border-slate-300 p-10 text-center text-sm text-slate-500">No changes recorded yet.</p>
      ) : (
        <div className={clsx(isFetching && 'opacity-60')}>
          <TableWrap>
            <thead className="bg-slate-50">
              <tr>
                <th className={th}>When</th>
                <th className={th}>Who</th>
                <th className={th}>What</th>
                <th className={th}>Details</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {data.content.map((e) => (
                <tr key={e.id}>
                  <td className={clsx(td, 'whitespace-nowrap')}>{formatDateTime(e.createdAt)}</td>
                  <td className={td}>{e.actorEmail}</td>
                  <td className={clsx(td, 'whitespace-nowrap')}>
                    {humanize(e.action)} <span className="text-xs text-slate-500">#{e.entityId}</span>
                  </td>
                  <td className={clsx(td, 'text-xs text-slate-600')}>{describe(e.details)}</td>
                </tr>
              ))}
            </tbody>
          </TableWrap>
          <Pagination page={data.page} totalPages={data.totalPages} onChange={setPage} />
        </div>
      )}
    </>
  );
}
