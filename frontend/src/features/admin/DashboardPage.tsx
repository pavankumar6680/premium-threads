import clsx from 'clsx';
import { AlertTriangle, ArrowDownRight, ArrowUpRight, Cpu, ShoppingBag, Sparkles, Target, Users } from 'lucide-react';
import { useState, type ReactNode } from 'react';
import { Link } from 'react-router';
import { Alert } from '../../components/ui/Alert';
import { PageSpinner } from '../../components/ui/Spinner';
import { formatMoney } from '../../lib/format';
import { statusLabels, type OrderStatus } from '../orders/api';
import { useAnalytics, type AnalyticsSummary } from './api';
import { Card, PageHeader } from './components/AdminUi';

const PERIODS = [7, 30, 90] as const;

export function DashboardPage() {
  const [days, setDays] = useState<(typeof PERIODS)[number]>(30);
  const { data, isPending, isError, isFetching } = useAnalytics(days);

  return (
    <>
      <PageHeader
        title="Dashboard"
        description="Sales, stock and how shoppers use goal-based bundles. Revenue excludes cancelled orders."
        action={
          <div role="group" aria-label="Period" className="inline-flex rounded-lg bg-slate-100 p-1">
            {PERIODS.map((p) => (
              <button
                key={p}
                type="button"
                aria-pressed={days === p}
                onClick={() => setDays(p)}
                className={clsx(
                  'rounded-md px-3 py-1.5 text-sm font-medium',
                  days === p ? 'bg-white text-slate-900 shadow-sm' : 'text-slate-600 hover:text-slate-900',
                )}
              >
                {p} days
              </button>
            ))}
          </div>
        }
      />
      {isPending ? (
        <PageSpinner />
      ) : isError ? (
        <Alert tone="error">The dashboard could not be loaded.</Alert>
      ) : (
        <div className={clsx('space-y-6 transition-opacity', isFetching && 'opacity-60')}>
          <Kpis data={data} />
          <div className="grid gap-6 xl:grid-cols-3">
            <Card title="Daily revenue" className="xl:col-span-2">
              <RevenueChart daily={data.daily} />
            </Card>
            <Card title="Orders by status">
              <StatusBreakdown counts={data.ordersByStatus} />
            </Card>
          </div>
          <div className="grid gap-6 lg:grid-cols-2">
            <TopProducts data={data} />
            <LowStock data={data} />
          </div>
          <div className="grid gap-6 lg:grid-cols-2">
            <GoalsCard data={data} />
            <EngineCard data={data} />
          </div>
        </div>
      )}
    </>
  );
}

function Kpis({ data }: { data: AnalyticsSummary }) {
  const { sales, customers } = data;
  const change = sales.previousRevenue > 0 ? (sales.revenue - sales.previousRevenue) / sales.previousRevenue : null;
  return (
    <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
      <Kpi label="Revenue" value={formatMoney(sales.revenue)} icon={ShoppingBag}>
        {change === null ? (
          <span className="text-slate-500">No sales in the previous {data.days} days</span>
        ) : (
          <span className={clsx('inline-flex items-center gap-0.5 font-medium', change >= 0 ? 'text-emerald-700' : 'text-red-600')}>
            {change >= 0 ? <ArrowUpRight className="size-4" aria-hidden="true" /> : <ArrowDownRight className="size-4" aria-hidden="true" />}
            {Math.abs(Math.round(change * 100))}% vs previous {data.days} days
          </span>
        )}
      </Kpi>
      <Kpi label="Orders" value={String(sales.orders)} icon={ShoppingBag}>
        <span className="text-slate-500">{sales.cancelledOrders} cancelled · {sales.unitsSold} units</span>
      </Kpi>
      <Kpi label="Average order" value={formatMoney(sales.averageOrderValue)} icon={ShoppingBag} />
      <Kpi label="Customers" value={String(customers.total)} icon={Users}>
        <span className="text-slate-500">{customers.newInPeriod} new</span>
      </Kpi>
    </div>
  );
}

function Kpi({ label, value, icon: Icon, children }: { label: string; value: string; icon: typeof Users; children?: ReactNode }) {
  return (
    <div className="rounded-2xl border border-slate-200 bg-white p-4 shadow-xs">
      <p className="flex items-center gap-1.5 text-sm text-slate-600">
        <Icon className="size-4 text-slate-400" aria-hidden="true" /> {label}
      </p>
      <p className="mt-1 text-2xl font-bold tracking-tight text-slate-900">{value}</p>
      {children && <p className="mt-1 text-xs">{children}</p>}
    </div>
  );
}

/** Bar chart in plain SVG (no chart library); each bar carries a native tooltip and the data is in a table for screen readers. */
function RevenueChart({ daily }: { daily: AnalyticsSummary['daily'] }) {
  const max = Math.max(...daily.map((d) => d.revenue), 1);
  const width = 600;
  const height = 180;
  const gap = daily.length > 40 ? 1 : 3;
  const barWidth = (width - gap * (daily.length - 1)) / daily.length;
  const total = daily.reduce((sum, d) => sum + d.revenue, 0);
  const day = (iso: string) => new Date(`${iso}T00:00:00`).toLocaleDateString(undefined, { month: 'short', day: 'numeric' });

  if (total === 0) {
    return <p className="py-12 text-center text-sm text-slate-500">No sales in this period yet.</p>;
  }
  return (
    <figure>
      <svg viewBox={`0 0 ${width} ${height + 20}`} className="h-52 w-full" role="img" aria-label={`Daily revenue, ${formatMoney(total)} in total`}>
        {[0.25, 0.5, 0.75, 1].map((f) => (
          <line key={f} x1={0} x2={width} y1={height - f * height} y2={height - f * height} className="stroke-slate-100" />
        ))}
        {daily.map((d, i) => {
          const h = Math.max((d.revenue / max) * height, d.revenue > 0 ? 2 : 0);
          return (
            <rect key={d.date} x={i * (barWidth + gap)} y={height - h} width={barWidth} height={h} rx={2} className="fill-brand-500 hover:fill-brand-700">
              <title>{`${day(d.date)}: ${formatMoney(d.revenue)} · ${d.orders} orders`}</title>
            </rect>
          );
        })}
        <text x={0} y={height + 16} className="fill-slate-500 text-[11px]">{day(daily[0].date)}</text>
        <text x={width} y={height + 16} textAnchor="end" className="fill-slate-500 text-[11px]">{day(daily[daily.length - 1].date)}</text>
      </svg>
      <figcaption className="mt-1 text-xs text-slate-500">Highest day: {formatMoney(max)}</figcaption>
      <table className="sr-only">
        <caption>Daily revenue</caption>
        <tbody>
          {daily.map((d) => (
            <tr key={d.date}><th>{d.date}</th><td>{formatMoney(d.revenue)}</td><td>{d.orders} orders</td></tr>
          ))}
        </tbody>
      </table>
    </figure>
  );
}

function StatusBreakdown({ counts }: { counts: Record<OrderStatus, number> }) {
  const total = Object.values(counts).reduce((a, b) => a + b, 0);
  return (
    <ul className="space-y-3">
      {(Object.keys(statusLabels) as OrderStatus[]).map((status) => {
        const n = counts[status] ?? 0;
        return (
          <li key={status}>
            <div className="mb-1 flex justify-between text-sm">
              <span className="text-slate-700">{statusLabels[status].label}</span>
              <span className="font-medium text-slate-900">{n}</span>
            </div>
            <div className="h-2 rounded-full bg-slate-100">
              <div className="h-2 rounded-full bg-brand-500" style={{ width: total ? `${(n / total) * 100}%` : 0 }} />
            </div>
          </li>
        );
      })}
    </ul>
  );
}

function TopProducts({ data }: { data: AnalyticsSummary }) {
  return (
    <Card title="Best sellers">
      {data.topProducts.length === 0 ? (
        <p className="text-sm text-slate-500">No sales in this period yet.</p>
      ) : (
        <ol className="divide-y divide-slate-100">
          {data.topProducts.map((p, i) => (
            <li key={p.productId} className="flex items-center gap-3 py-2.5 text-sm">
              <span className="w-5 text-slate-400">{i + 1}</span>
              <Link to={`/admin/products/${p.productId}`} className="min-w-0 flex-1 truncate font-medium text-slate-800 hover:underline">
                {p.name}
              </Link>
              <span className="text-slate-500">{p.units} sold</span>
              <span className="w-24 text-right font-medium text-slate-900">{formatMoney(p.revenue)}</span>
            </li>
          ))}
        </ol>
      )}
    </Card>
  );
}

function LowStock({ data }: { data: AnalyticsSummary }) {
  const { inventory } = data;
  return (
    <Card
      title="Low stock"
      action={
        <Link to="/admin/products?lowStock=1" className="text-sm font-medium text-brand-700 hover:underline">
          View all
        </Link>
      }
    >
      <p className="mb-3 flex items-center gap-1.5 text-sm text-slate-600">
        <AlertTriangle className="size-4 text-amber-500" aria-hidden="true" />
        {inventory.lowStock} of {inventory.activeProducts} active products have 5 or fewer units; {inventory.outOfStock} {inventory.outOfStock === 1 ? 'is' : 'are'} sold out.
      </p>
      <ul className="divide-y divide-slate-100">
        {inventory.lowest.map((p) => (
          <li key={p.productId} className="flex items-center justify-between gap-3 py-2 text-sm">
            <Link to={`/admin/products/${p.productId}`} className="min-w-0 truncate text-slate-800 hover:underline">{p.name}</Link>
            <span className={clsx('shrink-0 font-semibold', p.stock === 0 ? 'text-red-600' : 'text-amber-700')}>
              {p.stock === 0 ? 'Sold out' : `${p.stock} left`}
            </span>
          </li>
        ))}
      </ul>
    </Card>
  );
}

function GoalsCard({ data }: { data: AnalyticsSummary }) {
  const { goals } = data;
  const max = Math.max(...goals.byTemplate.map((t) => t.goals), 1);
  return (
    <Card title="Shopping goals">
      <div className="mb-4 grid grid-cols-3 gap-3 text-center">
        <Mini label="Goals" value={goals.created} icon={Target} />
        <Mini label="In own words" value={goals.describedInOwnWords} icon={Sparkles} />
        <Mini label="Avg. budget" value={formatMoney(goals.averageBudget)} />
      </div>
      <ul className="space-y-2">
        {goals.byTemplate.map((t) => (
          <li key={t.title} className="text-sm">
            <div className="mb-1 flex justify-between"><span className="text-slate-700">{t.title}</span><span className="font-medium">{t.goals}</span></div>
            <div className="h-1.5 rounded-full bg-slate-100"><div className="h-1.5 rounded-full bg-violet-500" style={{ width: `${(t.goals / max) * 100}%` }} /></div>
          </li>
        ))}
      </ul>
    </Card>
  );
}

function EngineCard({ data }: { data: AnalyticsSummary }) {
  const r = data.recommendations;
  return (
    <Card title="Recommendation engine">
      <dl className="grid grid-cols-2 gap-x-6 gap-y-3 text-sm">
        <Stat label="Runs" value={r.runs} />
        <Stat label="No bundle possible" value={`${r.infeasibleRuns} (${Math.round(r.infeasibleShare * 100)}%)`} />
        <Stat label="Average time" value={`${r.averageDurationMs} ms`} />
        <Stat label="95th percentile" value={`${r.p95DurationMs} ms`} />
        <Stat label="Combinations explored (avg.)" value={r.averageNodes.toLocaleString()} />
        <Stat label="AI overviews" value={r.aiOverviews} />
        <Stat label="Bundles saved" value={r.savedBundles} />
        <Stat label="Bundles customised" value={r.customizedBundles} />
      </dl>
      <p className="mt-4 flex items-start gap-1.5 text-xs text-slate-500">
        <Cpu className="mt-0.5 size-3.5 shrink-0" aria-hidden="true" />
        A high "no bundle possible" share usually means budgets are below what the catalog can offer for popular goals.
      </p>
    </Card>
  );
}

function Mini({ label, value, icon: Icon }: { label: string; value: string | number; icon?: typeof Users }) {
  return (
    <div className="rounded-xl bg-slate-50 p-3">
      <p className="flex items-center justify-center gap-1 text-xs text-slate-500">{Icon && <Icon className="size-3.5" aria-hidden="true" />}{label}</p>
      <p className="mt-0.5 text-lg font-bold text-slate-900">{value}</p>
    </div>
  );
}

function Stat({ label, value }: { label: string; value: string | number }) {
  return (
    <div>
      <dt className="text-slate-500">{label}</dt>
      <dd className="font-semibold text-slate-900">{value}</dd>
    </div>
  );
}
