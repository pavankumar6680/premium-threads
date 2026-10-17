import { Mail, ShieldCheck, UserRound } from 'lucide-react';
import { useCurrentUser } from '../auth/hooks';
import { MyGoalsSection } from '../goals/MyGoalsSection';
import { AddressBookSection } from './AddressBookSection';

export function AccountPage() {
  const { data: user } = useCurrentUser();
  if (!user) return null; // guarded by RequireAuth

  return (
    <div className="mx-auto max-w-3xl px-4 py-10">
      <h1 className="text-2xl font-bold tracking-tight">My account</h1>
      <p className="mt-1 text-slate-600">Your profile, goals and saved addresses.</p>

      <section className="mt-6 rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
        <div className="flex items-center gap-4">
          <div className="flex size-14 items-center justify-center rounded-full bg-brand-100 text-xl font-bold text-brand-700">
            {user.fullName.charAt(0).toUpperCase()}
          </div>
          <div>
            <p className="text-lg font-semibold">{user.fullName}</p>
            <p className="text-sm text-slate-600">{user.email}</p>
          </div>
        </div>

        <dl className="mt-6 grid gap-4 border-t border-slate-100 pt-6 sm:grid-cols-3">
          <div>
            <dt className="flex items-center gap-1.5 text-xs font-medium uppercase tracking-wide text-slate-500">
              <UserRound className="size-3.5" aria-hidden="true" /> Name
            </dt>
            <dd className="mt-1 text-sm">{user.fullName}</dd>
          </div>
          <div>
            <dt className="flex items-center gap-1.5 text-xs font-medium uppercase tracking-wide text-slate-500">
              <Mail className="size-3.5" aria-hidden="true" /> Email
            </dt>
            <dd className="mt-1 break-all text-sm">{user.email}</dd>
          </div>
          <div>
            <dt className="flex items-center gap-1.5 text-xs font-medium uppercase tracking-wide text-slate-500">
              <ShieldCheck className="size-3.5" aria-hidden="true" /> Roles
            </dt>
            <dd className="mt-1 flex flex-wrap gap-1.5">
              {user.roles.map((role) => (
                <span key={role} className="rounded-full bg-slate-100 px-2.5 py-0.5 text-xs font-medium text-slate-700">
                  {role.toLowerCase()}
                </span>
              ))}
            </dd>
          </div>
        </dl>
      </section>

      <MyGoalsSection />
      <AddressBookSection />
    </div>
  );
}
