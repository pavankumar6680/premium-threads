import { ArrowRight, Boxes, ListChecks, Scale, ShieldCheck, Target, Wallet } from 'lucide-react';
import { Link } from 'react-router';
import { buttonClasses } from '../components/ui/Button';
import { useCategories, useProducts } from '../features/catalog/api';
import { categoryVisual } from '../features/catalog/categoryVisuals';
import { ProductCard, ProductGridSkeleton } from '../features/catalog/components/ProductCard';
import { useCurrentUser } from '../features/auth/hooks';
import { useGoalTemplates } from '../features/goals/api';
import { TemplateCard } from '../features/goals/TemplateCard';

const steps = [
  {
    icon: Target,
    title: 'Describe your goal',
    body: 'Tell us what you want to accomplish, such as a home office, a gaming setup or a home gym, and what you already own.',
  },
  {
    icon: Wallet,
    title: 'Set your budget',
    body: 'We search real catalog products and optimize the combination to get the most value without going over.',
  },
  {
    icon: Boxes,
    title: 'Customize and check out',
    body: 'Swap items, lock favorites and watch compatibility and totals update live. Then buy everything at once.',
  },
];

const promises = [
  { icon: ShieldCheck, title: 'Compatibility checked', body: 'Explicit rules on real specs: sockets, mounts, ports, dimensions.' },
  { icon: Scale, title: 'Three honest options', body: 'Budget, Balanced and Premium, each meaningfully different or clearly explained.' },
  { icon: ListChecks, title: 'Every pick explained', body: 'See why each product was chosen and what the alternatives cost.' },
];

export function HomePage() {
  const { data: user } = useCurrentUser();
  const categories = useCategories();
  const topRated = useProducts('sort=rating&size=8');
  const templates = useGoalTemplates();

  return (
    <>
      <section className="relative overflow-hidden bg-gradient-to-b from-brand-950 via-brand-900 to-brand-800 text-white">
        <div
          className="pointer-events-none absolute -right-32 -top-32 size-[28rem] rounded-full bg-brand-500/30 blur-3xl"
          aria-hidden="true"
        />
        <div className="relative mx-auto max-w-7xl px-4 py-20 sm:py-28">
          <p className="inline-flex rounded-full bg-white/10 px-3 py-1 text-sm font-medium text-brand-100 ring-1 ring-white/20">
            Goal-based shopping
          </p>
          <h1 className="mt-5 max-w-3xl text-4xl font-extrabold tracking-tight sm:text-6xl">
            Tell us your goal. <span className="text-accent-400">We’ll build the bundle.</span>
          </h1>
          <p className="mt-5 max-w-2xl text-lg text-brand-100">
            Stop shopping item by item. BuildMyBundle turns “I want a professional home office for $1,200” into a
            complete, compatible set of products, optimized for your budget.
          </p>
          <div className="mt-8 flex flex-wrap gap-3">
            <Link to="/goals" className={buttonClasses('primary', 'lg', 'bg-white !text-brand-800 hover:bg-brand-50')}>
              Build a bundle
            </Link>
            <Link to="/shop" className={buttonClasses('ghost', 'lg', '!text-white ring-1 ring-white/30 hover:bg-white/10')}>
              Shop all products
            </Link>
            {!user && (
              <Link to="/register" className={buttonClasses('ghost', 'lg', '!text-white hover:bg-white/10')}>
                Create a free account
              </Link>
            )}
          </div>
        </div>
      </section>

      <section className="mx-auto max-w-7xl px-4 pt-16" aria-labelledby="popular-goals">
        <div className="flex items-end justify-between gap-4">
          <h2 id="popular-goals" className="text-2xl font-bold tracking-tight sm:text-3xl">
            Popular goals
          </h2>
          <Link to="/goals" className="flex items-center gap-1 text-sm font-semibold text-brand-700 hover:underline">
            Describe your own <ArrowRight className="size-4" aria-hidden="true" />
          </Link>
        </div>
        <div className="mt-8 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          {(templates.data ?? []).slice(0, 8).map((t) => (
            <TemplateCard key={t.slug} template={t} />
          ))}
        </div>
      </section>

      <section className="mx-auto max-w-7xl px-4 py-16" aria-labelledby="departments">
        <h2 id="departments" className="text-2xl font-bold tracking-tight sm:text-3xl">
          Shop by department
        </h2>
        <div className="mt-8 grid grid-cols-2 gap-4 md:grid-cols-3 lg:grid-cols-6">
          {(categories.data ?? []).map((dept) => {
            const { icon: Icon, tile } = categoryVisual(dept.slug);
            return (
              <Link
                key={dept.slug}
                to={`/shop?category=${dept.slug}`}
                className="group rounded-2xl border border-slate-200 bg-white p-4 shadow-xs transition-shadow hover:shadow-md"
              >
                <div className={`flex aspect-[4/3] items-center justify-center rounded-xl bg-gradient-to-br ${tile}`}>
                  <Icon className="size-10 transition-transform group-hover:scale-110" strokeWidth={1.5} aria-hidden="true" />
                </div>
                <p className="mt-3 text-sm font-semibold leading-tight">{dept.name}</p>
                <p className="text-xs text-slate-500">{dept.productCount} products</p>
              </Link>
            );
          })}
        </div>
      </section>

      <section className="border-y border-slate-200 bg-white" aria-labelledby="top-rated">
        <div className="mx-auto max-w-7xl px-4 py-16">
          <div className="flex items-end justify-between gap-4">
            <h2 id="top-rated" className="text-2xl font-bold tracking-tight sm:text-3xl">
              Top rated
            </h2>
            <Link to="/shop?sort=rating" className="flex items-center gap-1 text-sm font-semibold text-brand-700 hover:underline">
              See all <ArrowRight className="size-4" aria-hidden="true" />
            </Link>
          </div>
          <div className="mt-8">
            {topRated.data ? (
              <div className="grid grid-cols-2 gap-4 md:grid-cols-3 xl:grid-cols-4">
                {topRated.data.content.map((product) => (
                  <ProductCard key={product.id} product={product} />
                ))}
              </div>
            ) : (
              <ProductGridSkeleton count={4} />
            )}
          </div>
        </div>
      </section>

      <section className="mx-auto max-w-7xl px-4 py-16" aria-labelledby="how-it-works">
        <h2 id="how-it-works" className="text-2xl font-bold tracking-tight sm:text-3xl">
          How bundles work
        </h2>
        <ol className="mt-8 grid gap-6 md:grid-cols-3">
          {steps.map(({ icon: Icon, title, body }, i) => (
            <li key={title} className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
              <div className="flex items-center gap-3">
                <span className="flex size-10 items-center justify-center rounded-xl bg-brand-50 text-brand-600">
                  <Icon className="size-5" aria-hidden="true" />
                </span>
                <span className="text-sm font-semibold text-slate-400">Step {i + 1}</span>
              </div>
              <h3 className="mt-4 text-lg font-semibold">{title}</h3>
              <p className="mt-2 text-slate-600">{body}</p>
            </li>
          ))}
        </ol>
        <div className="mt-10 grid gap-8 md:grid-cols-3">
          {promises.map(({ icon: Icon, title, body }) => (
            <div key={title} className="flex gap-4">
              <Icon className="mt-1 size-6 shrink-0 text-brand-600" aria-hidden="true" />
              <div>
                <h3 className="font-semibold">{title}</h3>
                <p className="mt-1 text-slate-600">{body}</p>
              </div>
            </div>
          ))}
        </div>
      </section>
    </>
  );
}
