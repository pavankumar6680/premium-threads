import clsx from 'clsx';
import { Boxes, ClipboardList, FolderTree, History, House, LayoutDashboard, Package, Palette, ShieldCheck, Tags, Target } from 'lucide-react';
import { NavLink, Outlet } from 'react-router';

const NAV = [
  { to: '/admin', label: 'Dashboard', icon: LayoutDashboard, end: true },
  { to: '/admin/products', label: 'Products', icon: Boxes },
  { to: '/admin/orders', label: 'Orders', icon: Package },
  { to: '/admin/brands', label: 'Brands', icon: Tags },
  { to: '/admin/categories', label: 'Categories', icon: FolderTree },
  { to: '/admin/styles', label: 'Styles & occasions', icon: Palette },
  { to: '/admin/homepage', label: 'Homepage', icon: House },
  { to: '/admin/rules', label: 'Outfit rules', icon: ShieldCheck },
  { to: '/admin/templates', label: 'Outfit templates', icon: Target },
  { to: '/admin/activity', label: 'Activity log', icon: History },
];

/** Admin area shell: a sidebar on large screens, a scrollable tab strip on small ones. */
export function AdminLayout() {
  return (
    <div className="mx-auto max-w-7xl px-4 py-6 lg:flex lg:gap-8 lg:py-10">
      <aside className="lg:w-56 lg:shrink-0">
        <p className="mb-3 hidden items-center gap-2 text-xs font-semibold uppercase tracking-wider text-slate-500 lg:flex">
          <ClipboardList className="size-4" aria-hidden="true" /> Store admin
        </p>
        <nav aria-label="Admin" className="-mx-4 flex gap-1 overflow-x-auto px-4 pb-3 lg:mx-0 lg:flex-col lg:overflow-visible lg:px-0">
          {NAV.map(({ to, label, icon: Icon, end }) => (
            <NavLink
              key={to}
              to={to}
              end={end}
              className={({ isActive }) =>
                clsx(
                  'flex shrink-0 items-center gap-2 rounded-lg px-3 py-2 text-sm font-medium whitespace-nowrap',
                  isActive ? 'bg-brand-50 text-brand-800' : 'text-slate-700 hover:bg-slate-100',
                )
              }
            >
              <Icon className="size-4" aria-hidden="true" />
              {label}
            </NavLink>
          ))}
        </nav>
      </aside>
      <div className="min-w-0 flex-1">
        <Outlet />
      </div>
    </div>
  );
}
