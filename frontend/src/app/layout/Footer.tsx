import { Mail, MessageCircle, Phone, RotateCcw, ShieldCheck, Truck } from 'lucide-react';
import { useState, type FormEvent } from 'react';
import { Link } from 'react-router';
import { Logo } from '../../components/Logo';

const SHOP = [
  { to: '/shop?category=men', label: 'Men' },
  { to: '/shop?category=women', label: 'Women' },
  { to: '/shop?category=accessories', label: 'Accessories' },
  { to: '/brands', label: 'All brands' },
  { to: '/shop?flag=new', label: 'New arrivals' },
  { to: '/shop?flag=sale', label: 'Sale' },
];

const HELP = [
  { to: '/orders', label: 'Track an order' },
  { to: '/shop?category=men-jeans', label: 'Size guide' },
  { to: '/account', label: 'Your account' },
  { to: '/outfit', label: 'Build Your Outfit' },
];

const SOCIAL = ['Instagram', 'TikTok', 'Pinterest', 'YouTube'];

export function Footer() {
  const [subscribed, setSubscribed] = useState(false);
  const subscribe = (e: FormEvent) => {
    e.preventDefault();
    setSubscribed(true);
  };
  return (
    <footer className="mt-20 bg-slate-900 text-slate-300">
      <div className="border-b border-white/10">
        <ul className="mx-auto grid max-w-7xl gap-4 px-4 py-6 text-sm sm:grid-cols-3">
          <li className="flex items-center gap-3"><Truck className="size-5 text-brand-400" aria-hidden="true" /> Free shipping on orders over $100</li>
          <li className="flex items-center gap-3"><RotateCcw className="size-5 text-brand-400" aria-hidden="true" /> Cancel any order before it ships</li>
          <li className="flex items-center gap-3"><ShieldCheck className="size-5 text-brand-400" aria-hidden="true" /> Secure checkout, simulated payments</li>
        </ul>
      </div>
      <div className="mx-auto grid max-w-7xl gap-10 px-4 py-12 md:grid-cols-[1.3fr_1fr_1fr_1.3fr]">
        <div>
          <Logo light />
          <p className="mt-4 max-w-xs text-sm leading-relaxed text-slate-400">
            A multi-brand fashion marketplace where you can shop pieces or let us build a complete, coordinated outfit for your occasion and budget.
          </p>
          <p className="mt-5 text-xs font-bold uppercase tracking-[0.16em] text-slate-500">Follow us</p>
          <ul className="mt-2 flex flex-wrap gap-2" aria-label="Social channels (coming soon)">
            {SOCIAL.map((s) => (
              <li key={s} className="rounded-full border border-white/15 px-3 py-1 text-xs" title={`${s}: coming soon`}>{s}</li>
            ))}
          </ul>
        </div>
        <FooterLinks title="Shop" links={SHOP} />
        <FooterLinks title="Help" links={HELP} />
        <div>
          <h2 className="text-xs font-bold uppercase tracking-[0.16em] text-white">Customer support</h2>
          <ul className="mt-4 space-y-2.5 text-sm">
            <li className="flex items-center gap-2"><Mail className="size-4" aria-hidden="true" /> support@buildmybundle.example</li>
            <li className="flex items-center gap-2"><Phone className="size-4" aria-hidden="true" /> +1 (555) 010-2030 · Mon–Fri 9–6</li>
            <li className="flex items-center gap-2"><MessageCircle className="size-4" aria-hidden="true" /> Live chat in your account</li>
          </ul>
          <form onSubmit={subscribe} className="mt-6">
            <label htmlFor="newsletter" className="text-xs font-bold uppercase tracking-[0.16em] text-white">The style letter</label>
            {subscribed ? (
              <p className="mt-2 text-sm text-brand-300" role="status">Thanks! (Demo store: no emails are sent.)</p>
            ) : (
              <div className="mt-2 flex gap-2">
                <input id="newsletter" type="email" required placeholder="you@example.com" className="h-10 min-w-0 flex-1 rounded-full bg-white/10 px-4 text-sm text-white placeholder:text-slate-500 focus:outline-none focus:ring-2 focus:ring-brand-400" />
                <button type="submit" className="h-10 rounded-full bg-brand-600 px-4 text-sm font-semibold text-white hover:bg-brand-500">Join</button>
              </div>
            )}
          </form>
        </div>
      </div>
      <div className="border-t border-white/10">
        <div className="mx-auto flex max-w-7xl flex-col gap-2 px-4 py-5 text-xs text-slate-500 sm:flex-row sm:items-center sm:justify-between">
          <p>© {new Date().getFullYear()} BuildMyBundle. A portfolio project; payments are simulated.</p>
          <p>Demo store: products, prices and stock are fictional sample data, not affiliated with or endorsed by the brands named.</p>
        </div>
      </div>
    </footer>
  );
}

function FooterLinks({ title, links }: { title: string; links: { to: string; label: string }[] }) {
  return (
    <div>
      <h2 className="text-xs font-bold uppercase tracking-[0.16em] text-white">{title}</h2>
      <ul className="mt-4 space-y-2.5 text-sm">
        {links.map((l) => (
          <li key={l.label}><Link to={l.to} className="hover:text-white">{l.label}</Link></li>
        ))}
      </ul>
    </div>
  );
}
