import clsx from 'clsx';
import {
  ChevronDown,
  Heart,
  LayoutDashboard,
  LogOut,
  Menu,
  Package,
  Search,
  ShoppingBag,
  Sparkles,
  UserRound,
  WandSparkles,
  X,
} from 'lucide-react';
import { useEffect, useRef, useState, type FormEvent } from 'react';
import { Link, NavLink, useLocation, useNavigate, useSearchParams } from 'react-router';
import { Logo } from '../../components/Logo';
import { Button, buttonClasses } from '../../components/ui/Button';
import { useCurrentUser, useHasRole, useLogout } from '../../features/auth/hooks';
import { useCart } from '../../features/cart/api';
import { useCategories } from '../../features/catalog/api';
import type { CategoryNode } from '../../features/catalog/types';

const DEPARTMENT_IMAGES: Record<string, string> = {
  men: '/images/editorial/men.jpg',
  women: '/images/editorial/women.jpg',
  accessories: '/images/editorial/accessories.jpg',
};

export function Header() {
  const { data: user, isPending } = useCurrentUser();
  const logout = useLogout();
  const isAdmin = useHasRole('ADMIN');
  const navigate = useNavigate();
  const location = useLocation();
  const { data: departments = [] } = useCategories();
  const [mobileOpen, setMobileOpen] = useState(false);

  useEffect(() => setMobileOpen(false), [location.pathname, location.search]);

  const onLogout = () => {
    logout.mutate(undefined, { onSettled: () => navigate('/', { replace: true }) });
  };

  return (
    <header className="sticky top-0 z-40">
      <div className="bg-slate-900 px-4 py-2 text-center text-xs font-medium text-white">
        Free shipping on orders over $100 · <Link to="/outfit" className="font-semibold text-brand-300 underline-offset-2 hover:underline">Build a complete outfit in under a minute</Link>
      </div>
      <div className="border-b border-slate-200/80 bg-white/90 backdrop-blur-md">
        <div className="mx-auto flex h-16 max-w-7xl items-center gap-3 px-4 lg:gap-6">
          <button
            type="button"
            className="-ml-2 inline-flex size-10 items-center justify-center rounded-full hover:bg-slate-100 lg:hidden"
            aria-label="Open menu"
            aria-expanded={mobileOpen}
            onClick={() => setMobileOpen(true)}
          >
            <Menu className="size-5" aria-hidden="true" />
          </button>
          <Logo />

          <nav aria-label="Main" className="hidden items-center gap-1 lg:flex">
            {departments.map((dept) => <MegaMenu key={dept.slug} department={dept} />)}
            <TopLink to="/brands">Brands</TopLink>
            <TopLink to="/shop?flag=sale" className="text-brand-600">Sale</TopLink>
            <NavLink
              to="/outfit"
              className="ml-1 inline-flex items-center gap-1.5 rounded-full bg-violet-100 px-3.5 py-2 text-sm font-semibold text-violet-800 transition-colors hover:bg-violet-200"
            >
              <WandSparkles className="size-4" aria-hidden="true" /> Build Your Outfit
            </NavLink>
          </nav>

          <SearchBox className="ml-auto hidden max-w-xs flex-1 md:block" />

          <nav aria-label="Account" className="ml-auto flex items-center gap-0.5 md:ml-0">
            {isPending ? (
              <div className="h-9 w-32 animate-pulse rounded-full bg-slate-100" aria-hidden="true" />
            ) : user ? (
              <>
                {isAdmin && <IconLink to="/admin" label="Store admin" icon={LayoutDashboard} alwaysVisible />}
                <IconLink to="/wishlist" label="Wishlist" icon={Heart} />
                <IconLink to="/bundles" label="My outfits" icon={Sparkles} />
                <IconLink to="/orders" label="My orders" icon={Package} />
                <CartLink />
                <Link to="/account" className={buttonClasses('ghost', 'sm', 'rounded-full')} aria-label={`Account: ${user.fullName}`}>
                  <UserRound className="size-5" aria-hidden="true" />
                  <span className="hidden max-w-[7rem] truncate xl:inline">{user.fullName.split(' ')[0]}</span>
                </Link>
                <Button variant="ghost" size="sm" className="rounded-full max-sm:hidden" onClick={onLogout} aria-label="Sign out" isLoading={logout.isPending}>
                  <LogOut className="size-5" aria-hidden="true" />
                </Button>
              </>
            ) : (
              <>
                <Link to="/login" className={buttonClasses('ghost', 'sm', 'rounded-full')}>Sign in</Link>
                <Link to="/register" className={buttonClasses('primary', 'sm', 'rounded-full max-sm:hidden')}>Create account</Link>
              </>
            )}
          </nav>
        </div>
        <div className="border-t border-slate-100 px-4 py-2 md:hidden">
          <SearchBox />
        </div>
      </div>
      {mobileOpen && (
        <MobileMenu departments={departments} onClose={() => setMobileOpen(false)} onLogout={user ? onLogout : undefined} />
      )}
    </header>
  );
}

function TopLink({ to, className, children }: { to: string; className?: string; children: string }) {
  return (
    <NavLink
      to={to}
      className={({ isActive }) =>
        clsx('rounded-full px-3 py-2 text-sm font-semibold transition-colors hover:bg-slate-100', isActive && 'bg-slate-100', className)
      }
    >
      {children}
    </NavLink>
  );
}

/** Department menu: opens on hover or click, lists the department's categories with an editorial tile. */
function MegaMenu({ department }: { department: CategoryNode }) {
  const [open, setOpen] = useState(false);
  const closeTimer = useRef<number | undefined>(undefined);
  const location = useLocation();
  useEffect(() => setOpen(false), [location.pathname, location.search]);
  const show = () => {
    window.clearTimeout(closeTimer.current);
    setOpen(true);
  };
  const hide = () => {
    closeTimer.current = window.setTimeout(() => setOpen(false), 120);
  };
  const panelId = `menu-${department.slug}`;
  return (
    <div className="relative" onMouseEnter={show} onMouseLeave={hide} onKeyDown={(e) => e.key === 'Escape' && setOpen(false)}>
      <button
        type="button"
        className={clsx('inline-flex items-center gap-1 rounded-full px-3 py-2 text-sm font-semibold transition-colors hover:bg-slate-100', open && 'bg-slate-100')}
        aria-expanded={open}
        aria-controls={panelId}
        onClick={() => setOpen((o) => !o)}
      >
        {department.name}
        <ChevronDown className={clsx('size-4 transition-transform', open && 'rotate-180')} aria-hidden="true" />
      </button>
      {open && (
        <div id={panelId} className="absolute left-0 top-full z-50 pt-3">
          <div className="grid w-[34rem] grid-cols-[1fr_12rem] gap-6 rounded-3xl bg-white p-6 shadow-lift ring-1 ring-slate-200 animate-fade-up">
            <div>
              <p className="text-xs font-bold uppercase tracking-[0.16em] text-slate-500">{department.name}</p>
              <ul className="mt-3 grid grid-cols-2 gap-x-6 gap-y-1">
                {department.children.map((c) => (
                  <li key={c.slug}>
                    <Link to={`/shop?category=${c.slug}`} className="block rounded-lg px-2 py-1.5 text-sm font-medium text-slate-700 hover:bg-violet-50 hover:text-violet-800">
                      {c.name}
                    </Link>
                  </li>
                ))}
              </ul>
              <div className="mt-4 flex gap-4 border-t border-slate-100 pt-3 text-sm font-semibold">
                <Link to={`/shop?category=${department.slug}`} className="text-slate-900 hover:underline">Shop all {department.name.toLowerCase()}</Link>
                <Link to={`/shop?category=${department.slug}&flag=new`} className="text-brand-600 hover:underline">New arrivals</Link>
              </div>
            </div>
            <Link to={`/shop?category=${department.slug}`} className="group relative overflow-hidden rounded-2xl" tabIndex={-1}>
              {DEPARTMENT_IMAGES[department.slug] && (
                <img src={DEPARTMENT_IMAGES[department.slug]} alt="" className="h-full w-full object-cover transition-transform duration-500 group-hover:scale-105" />
              )}
              <span className="absolute inset-x-0 bottom-0 bg-gradient-to-t from-black/70 to-transparent p-3 text-sm font-semibold text-white">
                {department.productCount} pieces
              </span>
            </Link>
          </div>
        </div>
      )}
    </div>
  );
}

function MobileMenu({ departments, onClose, onLogout }: { departments: CategoryNode[]; onClose: () => void; onLogout?: () => void }) {
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && onClose();
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, [onClose]);
  return (
    <div className="fixed inset-0 z-50 lg:hidden" role="dialog" aria-modal="true" aria-label="Menu">
      <div className="absolute inset-0 bg-slate-900/50" onClick={onClose} aria-hidden="true" />
      <div className="relative h-full w-[86%] max-w-sm overflow-y-auto bg-white p-5 shadow-lift animate-fade-up">
        <div className="flex items-center justify-between">
          <Logo />
          <button type="button" onClick={onClose} aria-label="Close menu" className="inline-flex size-10 items-center justify-center rounded-full hover:bg-slate-100">
            <X className="size-5" aria-hidden="true" />
          </button>
        </div>
        <Link to="/outfit" className="mt-5 flex items-center gap-2 rounded-2xl bg-violet-100 px-4 py-3 font-semibold text-violet-800">
          <WandSparkles className="size-5" aria-hidden="true" /> Build Your Outfit
        </Link>
        {departments.map((dept) => (
          <details key={dept.slug} className="group mt-2 border-b border-slate-100 py-2">
            <summary className="flex cursor-pointer list-none items-center justify-between py-2 text-lg font-semibold">
              {dept.name}
              <ChevronDown className="size-5 transition-transform group-open:rotate-180" aria-hidden="true" />
            </summary>
            <ul className="space-y-1 pb-2">
              <li><Link to={`/shop?category=${dept.slug}`} className="block py-1.5 text-sm font-semibold">Shop all</Link></li>
              {dept.children.map((c) => (
                <li key={c.slug}><Link to={`/shop?category=${c.slug}`} className="block py-1.5 text-sm text-slate-700">{c.name}</Link></li>
              ))}
            </ul>
          </details>
        ))}
        <Link to="/brands" className="block border-b border-slate-100 py-4 text-lg font-semibold">Brands</Link>
        <Link to="/shop?flag=sale" className="block border-b border-slate-100 py-4 text-lg font-semibold text-brand-600">Sale</Link>
        <div className="mt-4 grid grid-cols-2 gap-2 text-sm font-semibold">
          <Link to="/wishlist" className="rounded-xl bg-slate-100 px-3 py-2.5">Wishlist</Link>
          <Link to="/bundles" className="rounded-xl bg-slate-100 px-3 py-2.5">My outfits</Link>
          <Link to="/orders" className="rounded-xl bg-slate-100 px-3 py-2.5">My orders</Link>
          <Link to="/account" className="rounded-xl bg-slate-100 px-3 py-2.5">Account</Link>
        </div>
        {onLogout && (
          <button type="button" onClick={onLogout} className="mt-4 text-sm font-semibold text-slate-600 hover:underline">Sign out</button>
        )}
      </div>
    </div>
  );
}

function SearchBox({ className }: { className?: string }) {
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const [query, setQuery] = useState(params.get('q') ?? '');

  const submit = (e: FormEvent) => {
    e.preventDefault();
    const q = query.trim();
    navigate(q ? `/shop?q=${encodeURIComponent(q)}` : '/shop');
  };

  return (
    <form role="search" onSubmit={submit} className={clsx('relative', className)}>
      <Search className="pointer-events-none absolute left-3.5 top-1/2 size-4 -translate-y-1/2 text-slate-400" aria-hidden="true" />
      <input
        type="search"
        aria-label="Search products"
        placeholder="Search jeans, dresses, watches…"
        value={query}
        onChange={(e) => setQuery(e.target.value)}
        className="h-10 w-full rounded-full border border-transparent bg-slate-100 pl-10 pr-4 text-sm transition-colors placeholder:text-slate-500 focus:border-violet-400 focus:bg-white focus:outline-none focus:ring-4 focus:ring-violet-100"
      />
    </form>
  );
}

/** Secondary shortcuts are hidden on phones (max-sm:hidden, since buttonClasses already sets a display value). */
function IconLink({ to, label, icon: Icon, alwaysVisible = false }: { to: string; label: string; icon: typeof Heart; alwaysVisible?: boolean }) {
  return (
    <Link to={to} className={buttonClasses('ghost', 'sm', alwaysVisible ? 'rounded-full px-2' : 'rounded-full px-2 max-sm:hidden')} aria-label={label} title={label}>
      <Icon className="size-5" aria-hidden="true" />
    </Link>
  );
}

function CartLink() {
  const { data: cart } = useCart();
  const count = cart?.itemCount ?? 0;
  return (
    <Link to="/cart" className={buttonClasses('ghost', 'sm', 'relative rounded-full px-2')} aria-label={`Cart, ${count} items`}>
      <ShoppingBag className="size-5" aria-hidden="true" />
      {count > 0 && (
        <span className="absolute -right-0.5 -top-0.5 flex min-w-5 items-center justify-center rounded-full bg-brand-500 px-1 text-[11px] font-bold text-white">
          {count > 99 ? '99+' : count}
        </span>
      )}
    </Link>
  );
}
