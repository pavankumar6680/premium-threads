import clsx from 'clsx';
import { Boxes, Heart, LayoutDashboard, LogOut, Package, Search, ShoppingCart, UserRound } from 'lucide-react';
import { useState, type FormEvent } from 'react';
import { Link, NavLink, useNavigate, useSearchParams } from 'react-router';
import { Logo } from '../../components/Logo';
import { Button, buttonClasses } from '../../components/ui/Button';
import { useCurrentUser, useHasRole, useLogout } from '../../features/auth/hooks';
import { useCart } from '../../features/cart/api';

export function Header() {
  const { data: user, isPending } = useCurrentUser();
  const logout = useLogout();
  const isAdmin = useHasRole('ADMIN');
  const navigate = useNavigate();

  const onLogout = () => {
    logout.mutate(undefined, { onSettled: () => navigate('/', { replace: true }) });
  };

  return (
    <header className="sticky top-0 z-40 border-b border-slate-200 bg-white/90 backdrop-blur">
      <div className="mx-auto flex h-16 max-w-7xl items-center gap-4 px-4">
        <Logo />
        <NavLink
          to="/shop"
          className={({ isActive }) =>
            clsx('hidden rounded-lg px-3 py-2 text-sm font-semibold sm:block', isActive ? 'text-brand-700' : 'text-slate-700 hover:bg-slate-100')
          }
        >
          Shop
        </NavLink>
        <NavLink
          to="/goals"
          className={({ isActive }) =>
            clsx('hidden rounded-lg px-3 py-2 text-sm font-semibold sm:block', isActive ? 'text-brand-700' : 'text-slate-700 hover:bg-slate-100')
          }
        >
          Build a bundle
        </NavLink>
        <SearchBox className="hidden flex-1 md:block" />

        <nav aria-label="Account" className="ml-auto flex items-center gap-1">
          {isPending ? (
            <div className="h-9 w-40 animate-pulse rounded-lg bg-slate-100" aria-hidden="true" />
          ) : user ? (
            <>
              {isAdmin && <IconLink to="/admin" label="Store admin" icon={LayoutDashboard} alwaysVisible />}
              <IconLink to="/wishlist" label="Wishlist" icon={Heart} />
              <IconLink to="/bundles" label="My bundles" icon={Boxes} />
              <IconLink to="/orders" label="My orders" icon={Package} />
              <CartLink />
              <Link to="/account" className={buttonClasses('ghost', 'sm')} aria-label={`Account: ${user.fullName}`}>
                <UserRound className="size-4" aria-hidden="true" />
                <span className="hidden max-w-[8rem] truncate lg:inline">{user.fullName}</span>
              </Link>
              <Button variant="secondary" size="sm" onClick={onLogout} aria-label="Sign out" isLoading={logout.isPending}>
                <LogOut className="size-4" aria-hidden="true" />
                <span className="hidden sm:inline">Sign out</span>
              </Button>
            </>
          ) : (
            <>
              <Link to="/login" className={buttonClasses('ghost', 'sm')}>
                Sign in
              </Link>
              <Link to="/register" className={buttonClasses('primary', 'sm', 'max-sm:hidden')}>
                Create account
              </Link>
            </>
          )}
        </nav>
      </div>
      <div className="border-t border-slate-100 px-4 py-2 md:hidden">
        <SearchBox />
      </div>
    </header>
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
    <form role="search" onSubmit={submit} className={clsx('relative max-w-xl', className)}>
      <Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-slate-400" aria-hidden="true" />
      <input
        type="search"
        aria-label="Search products"
        placeholder="Search desks, monitors, microphones…"
        value={query}
        onChange={(e) => setQuery(e.target.value)}
        className="h-10 w-full rounded-full border border-slate-300 bg-slate-50 pl-9 pr-4 text-sm focus:border-brand-500 focus:bg-white focus:outline-none focus:ring-2 focus:ring-brand-100"
      />
    </form>
  );
}

/** Secondary shortcuts are hidden on phones (max-sm:hidden, since buttonClasses already sets a display value). */
function IconLink({ to, label, icon: Icon, alwaysVisible = false }: { to: string; label: string; icon: typeof Heart; alwaysVisible?: boolean }) {
  return (
    <Link to={to} className={buttonClasses('ghost', 'sm', alwaysVisible ? 'px-2' : 'px-2 max-sm:hidden')} aria-label={label} title={label}>
      <Icon className="size-5" aria-hidden="true" />
    </Link>
  );
}

function CartLink() {
  const { data: cart } = useCart();
  const count = cart?.itemCount ?? 0;
  return (
    <Link to="/cart" className={buttonClasses('ghost', 'sm', 'relative px-2')} aria-label={`Cart, ${count} items`}>
      <ShoppingCart className="size-5" aria-hidden="true" />
      {count > 0 && (
        <span className="absolute -right-0.5 -top-0.5 flex min-w-5 items-center justify-center rounded-full bg-brand-600 px-1 text-[11px] font-bold text-white">
          {count > 99 ? '99+' : count}
        </span>
      )}
    </Link>
  );
}
