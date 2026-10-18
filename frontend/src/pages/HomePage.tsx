import clsx from 'clsx';
import { ArrowRight, ChevronLeft, ChevronRight, Palette, Ruler, ShoppingBag, Sparkles, Tag, Truck, WandSparkles } from 'lucide-react';
import { useRef, type ReactNode } from 'react';
import { Link } from 'react-router';
import { buttonClasses } from '../components/ui/Button';
import { useBrands, useHomeSections, useProducts, type HomeSection } from '../features/catalog/api';
import { ProductCard, ProductGridSkeleton } from '../features/catalog/components/ProductCard';
import type { ProductCard as Card } from '../features/catalog/types';

const DEPARTMENTS = [
  { slug: 'men', title: 'Shop Men', blurb: 'Oxford shirts, denim, knits and sharp layers', image: '/images/editorial/men.jpg' },
  { slug: 'women', title: 'Shop Women', blurb: 'Dresses, tailoring and weekend favourites', image: '/images/editorial/women.jpg' },
  { slug: 'accessories', title: 'Shop Accessories', blurb: 'Watches, bags, shades and jewelry', image: '/images/editorial/accessories.jpg' },
];

const STYLES = [
  { slug: 'casual', name: 'Casual', image: '/images/editorial/style-casual.jpg' },
  { slug: 'streetwear', name: 'Streetwear', image: '/images/editorial/style-streetwear.jpg' },
  { slug: 'formal', name: 'Formal', image: '/images/editorial/style-formal.jpg' },
  { slug: 'minimalist', name: 'Minimalist', image: '/images/editorial/style-minimalist.jpg' },
  { slug: 'party-wear', name: 'Party Wear', image: '/images/editorial/style-party.jpg' },
];

/** Built-in rails and their defaults (used until the admin-managed sections load, or if they fail to). */
const BUILT_IN: Record<string, Omit<HomeSection, 'id' | 'position'>> = {
  trending: { slug: 'trending', eyebrow: 'Most wanted', title: 'Trending now', query: 'flag=trending' },
  'new-arrivals': { slug: 'new-arrivals', eyebrow: 'Just landed', title: 'New arrivals', query: 'flag=new&sort=newest' },
  offers: { slug: 'offers', eyebrow: 'Special offers', title: 'On sale', query: 'flag=sale&sort=discount' },
  recommended: { slug: 'recommended', eyebrow: 'Picked for you', title: 'Recommended products', query: 'flag=featured&sort=rating' },
};

export function HomePage() {
  const sections = useHomeSections();
  // a built-in rail hidden by an admin is absent from the list; before it loads, show the defaults
  const rail = (slug: string) => (sections.data ? sections.data.find((s) => s.slug === slug) : BUILT_IN[slug]);
  const trendingRail = rail('trending');
  const newRail = rail('new-arrivals');
  const offersRail = rail('offers');
  const recommendedRail = rail('recommended');
  const extras = sections.data?.filter((s) => !(s.slug in BUILT_IN)) ?? [];
  const trending = useProducts(`${trendingRail?.query ?? BUILT_IN.trending.query}&size=12`);
  const newArrivals = useProducts(`${newRail?.query ?? BUILT_IN['new-arrivals'].query}&size=8`);
  const sale = useProducts(`${offersRail?.query ?? BUILT_IN.offers.query}&size=12`);
  const recommended = useProducts(`${recommendedRail?.query ?? BUILT_IN.recommended.query}&size=8`);
  const brands = useBrands();
  const maxDiscount = Math.max(0, ...(sale.data?.content ?? []).map(discount));

  return (
    <>
      {/* 1. Hero */}
      <section className="relative isolate overflow-hidden bg-slate-900">
        <img src="/images/editorial/hero.jpg" alt="" className="absolute inset-0 -z-10 h-full w-full object-cover object-[70%_30%]" />
        <div className="absolute inset-0 -z-10 bg-gradient-to-r from-slate-950/90 via-slate-950/55 to-slate-950/5" aria-hidden="true" />
        <div className="mx-auto max-w-7xl px-4 py-24 text-white sm:py-32 lg:py-40">
          <p className="inline-flex items-center gap-2 rounded-full bg-white/10 px-3.5 py-1.5 text-xs font-bold uppercase tracking-[0.18em] text-brand-200 ring-1 ring-white/20 backdrop-blur animate-fade-up">
            <Sparkles className="size-3.5" aria-hidden="true" /> 15 brands · one outfit
          </p>
          <h1 className="mt-6 max-w-2xl font-display text-5xl font-semibold leading-[1.05] tracking-tight sm:text-7xl animate-fade-up">
            Dress for the <em className="font-medium italic text-brand-400">moment</em>.
          </h1>
          <p className="mt-5 max-w-xl text-lg text-white/85 animate-fade-up">
            Shop the season’s pieces from the labels you love, or tell us the occasion and budget and we’ll build a
            complete, coordinated look in seconds.
          </p>
          <div className="mt-9 flex flex-wrap gap-3 animate-fade-up">
            <Link to="/shop" className={buttonClasses('primary', 'lg', 'rounded-full px-8')}>
              Shop now <ArrowRight className="size-5" aria-hidden="true" />
            </Link>
            <Link to="/outfit" className={buttonClasses('secondary', 'lg', 'rounded-full bg-white/10 px-7 !text-white ring-white/40 backdrop-blur hover:bg-white/20')}>
              <WandSparkles className="size-5" aria-hidden="true" /> Build Your Outfit
            </Link>
          </div>
        </div>
      </section>

      <div className="mx-auto max-w-7xl px-4">
        {/* 2. Departments */}
        <section className="mt-14 grid gap-5 md:grid-cols-3" aria-label="Shop by department">
          {DEPARTMENTS.map((d) => (
            <Link key={d.slug} to={`/shop?category=${d.slug}`} className="group relative aspect-[4/5] overflow-hidden rounded-3xl shadow-card md:aspect-[3/4]">
              <img src={d.image} alt="" className="absolute inset-0 h-full w-full object-cover transition-transform duration-700 group-hover:scale-105" />
              <div className="absolute inset-0 bg-gradient-to-t from-slate-950/80 via-slate-950/10 to-transparent" aria-hidden="true" />
              <div className="absolute inset-x-0 bottom-0 p-6 text-white">
                <h2 className="font-display text-3xl font-semibold">{d.title}</h2>
                <p className="mt-1 text-sm text-white/80">{d.blurb}</p>
                <span className="mt-4 inline-flex items-center gap-1.5 text-sm font-semibold text-brand-300 transition-transform group-hover:translate-x-1">
                  Explore <ArrowRight className="size-4" aria-hidden="true" />
                </span>
              </div>
            </Link>
          ))}
        </section>

        {/* 3. Featured brands */}
        <Section eyebrow="The labels" title="Featured brands" action={<SeeAll to="/brands">All brands</SeeAll>}>
          {brands.isPending ? (
            <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-5">
              {Array.from({ length: 10 }, (_, i) => <div key={i} className="h-24 animate-pulse rounded-2xl bg-slate-200/70" />)}
            </div>
          ) : (
            <ul className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-5">
              {(brands.data ?? []).map((b, i) => (
                <li key={b.slug}>
                  <Link
                    to={`/brands/${b.slug}`}
                    className={clsx(
                      'group flex h-24 flex-col items-center justify-center rounded-2xl px-3 text-center shadow-card ring-1 ring-slate-200/70 transition hover:-translate-y-0.5 hover:shadow-lift',
                      i % 3 === 1 ? 'bg-violet-100' : i % 3 === 2 ? 'bg-brand-50' : 'bg-white',
                    )}
                  >
                    <span className="font-display text-lg font-semibold uppercase tracking-[0.14em] group-hover:text-violet-700">{b.name}</span>
                    <span className="mt-1 text-xs text-slate-500">{b.productCount} pieces</span>
                  </Link>
                </li>
              ))}
            </ul>
          )}
          <p className="mt-3 text-xs text-slate-500">Brand names are shown as text only. Demo store: not affiliated with or endorsed by these brands.</p>
        </Section>

        {/* 4. Trending now */}
        {trendingRail && (
          <Section eyebrow={trendingRail.eyebrow} title={trendingRail.title} action={<SeeAll to={`/shop?${trendingRail.query}`}>Shop all</SeeAll>}>
            <Carousel products={trending.data?.content} loading={trending.isPending} label={trendingRail.title} />
          </Section>
        )}

        {/* 5. New arrivals */}
        {newRail && (
          <Section eyebrow={newRail.eyebrow} title={newRail.title} action={<SeeAll to={`/shop?${newRail.query}`}>See what’s new</SeeAll>}>
            {newArrivals.isPending ? <ProductGridSkeleton count={8} /> : (
              <div className="grid grid-cols-2 gap-x-4 gap-y-8 md:grid-cols-4">
                {newArrivals.data?.content.map((p) => <ProductCard key={p.id} product={p} />)}
              </div>
            )}
          </Section>
        )}

        {/* 6. Shop by style */}
        <Section eyebrow="Find your vibe" title="Shop by style">
          <div className="flex snap-x gap-4 overflow-x-auto pb-2 no-scrollbar lg:grid lg:grid-cols-5 lg:overflow-visible">
            {STYLES.map((s) => (
              <Link key={s.slug} to={`/shop?style=${s.slug}`} className="group relative aspect-[3/4] w-56 shrink-0 snap-start overflow-hidden rounded-3xl shadow-card lg:w-auto">
                <img src={s.image} alt="" className="absolute inset-0 h-full w-full object-cover transition-transform duration-700 group-hover:scale-105" />
                <div className="absolute inset-0 bg-gradient-to-t from-slate-950/75 to-transparent" aria-hidden="true" />
                <span className="absolute bottom-4 left-4 font-display text-2xl font-semibold text-white">{s.name}</span>
              </Link>
            ))}
          </div>
        </Section>

        {/* 7. Special offers */}
        {offersRail && (
        <section className="mt-20 overflow-hidden rounded-3xl bg-slate-900 text-white shadow-lift" aria-labelledby="offers">
          <div className="grid lg:grid-cols-[1fr_1.4fr]">
            <div className="relative min-h-64">
              <img src="/images/editorial/offers.jpg" alt="" className="absolute inset-0 h-full w-full object-cover opacity-80" />
              <div className="absolute inset-0 bg-gradient-to-t from-slate-900 via-slate-900/40 to-transparent lg:bg-gradient-to-r lg:from-transparent lg:to-slate-900" aria-hidden="true" />
              <div className="relative flex h-full flex-col justify-end p-8">
                <p className="text-xs font-bold uppercase tracking-[0.2em] text-brand-300">{offersRail.eyebrow || 'Special offers'}</p>
                <h2 id="offers" className="mt-2 font-display text-4xl font-semibold">{offersRail.title}</h2>
                <p className="mt-2 text-white/80">{maxDiscount > 0 ? `Up to ${maxDiscount}% off selected pieces while stock lasts.` : 'Selected pieces at reduced prices.'}</p>
                <Link to={`/shop?${offersRail.query}`} className={buttonClasses('primary', 'md', 'mt-5 w-fit rounded-full')}>
                  Shop the sale <ArrowRight className="size-4" aria-hidden="true" />
                </Link>
              </div>
            </div>
            <div className="p-6 lg:p-8">
              <div className="grid gap-3 sm:grid-cols-3">
                <Offer icon={Tag} title={maxDiscount > 0 ? `Up to ${maxDiscount}% off` : 'Sale'} body="Real reductions on denim, dresses and more." to="/shop?flag=sale" />
                <Offer icon={Sparkles} title="New season" body="Fresh arrivals from 15 brands." to="/shop?flag=new" />
                <Offer icon={Truck} title="Free shipping" body="On every order over $100." to="/shop" />
              </div>
              <div className="mt-6 [&_article_a]:text-white [&_article_p]:text-white/90">
                <Carousel products={sale.data?.content?.slice(0, 8)} loading={sale.isPending} label={offersRail.title} dark />
              </div>
            </div>
          </div>
        </section>
        )}

        {/* 8. Build your outfit */}
        <section className="mt-20 grid items-center gap-8 overflow-hidden rounded-3xl bg-violet-100 lg:grid-cols-2" aria-labelledby="build-outfit">
          <div className="p-8 sm:p-12">
            <p className="text-xs font-bold uppercase tracking-[0.2em] text-violet-700">Build Your Outfit</p>
            <h2 id="build-outfit" className="mt-2 font-display text-4xl font-semibold leading-tight sm:text-5xl">One occasion. One budget. One complete look.</h2>
            <p className="mt-4 max-w-lg text-slate-700">
              “A stylish casual outfit for a weekend party under $200.” We pick pieces from different brands that work
              together in your size, colors and style, then you swap, add accessories and check out in one go.
            </p>
            <ul className="mt-6 grid gap-3 sm:grid-cols-3">
              {[
                { icon: Palette, text: 'Colors that go together' },
                { icon: Ruler, text: 'In stock in your size' },
                { icon: ShoppingBag, text: 'Whole outfit to cart' },
              ].map(({ icon: Icon, text }) => (
                <li key={text} className="flex items-center gap-2 rounded-2xl bg-white/70 px-3 py-2.5 text-sm font-semibold">
                  <Icon className="size-4 text-violet-700" aria-hidden="true" /> {text}
                </li>
              ))}
            </ul>
            <Link to="/outfit" className={buttonClasses('primary', 'lg', 'mt-8 rounded-full bg-violet-600 hover:bg-violet-700')}>
              <WandSparkles className="size-5" aria-hidden="true" /> Start building
            </Link>
          </div>
          <img src="/images/editorial/build-outfit.jpg" alt="" className="h-full max-h-[32rem] w-full object-cover" />
        </section>

        {/* 9. Recommended */}
        {recommendedRail && (
          <Section eyebrow={recommendedRail.eyebrow} title={recommendedRail.title} action={<SeeAll to={`/shop?${recommendedRail.query}`}>More picks</SeeAll>}>
            {recommended.isPending ? <ProductGridSkeleton count={8} /> : (
              <div className="grid grid-cols-2 gap-x-4 gap-y-8 md:grid-cols-4">
                {recommended.data?.content.map((p) => <ProductCard key={p.id} product={p} />)}
              </div>
            )}
          </Section>
        )}

        {/* Extra collections added by admins */}
        {extras.map((s) => <CollectionRail key={s.id} section={s} />)}
      </div>
    </>
  );
}

function CollectionRail({ section }: { section: HomeSection }) {
  const products = useProducts(`${section.query}&size=12`);
  if (!products.isPending && !products.data?.content.length) return null;
  return (
    <Section eyebrow={section.eyebrow} title={section.title} action={<SeeAll to={`/shop?${section.query}`}>Shop the collection</SeeAll>}>
      <Carousel products={products.data?.content} loading={products.isPending} label={section.title} />
    </Section>
  );
}

function discount(p: Card): number {
  return p.compareAtPrice && p.compareAtPrice > p.price ? Math.round(((p.compareAtPrice - p.price) / p.compareAtPrice) * 100) : 0;
}

function Section({ eyebrow, title, action, children }: { eyebrow: string; title: string; action?: ReactNode; children: ReactNode }) {
  return (
    <section className="mt-20" aria-label={title}>
      <div className="mb-6 flex flex-wrap items-end justify-between gap-3">
        <div>
          <p className="text-xs font-bold uppercase tracking-[0.2em] text-brand-600">{eyebrow}</p>
          <h2 className="mt-1 font-display text-3xl font-semibold tracking-tight sm:text-4xl">{title}</h2>
        </div>
        {action}
      </div>
      {children}
    </section>
  );
}

function SeeAll({ to, children }: { to: string; children: string }) {
  return (
    <Link to={to} className="inline-flex items-center gap-1 text-sm font-semibold text-slate-900 underline-offset-4 hover:underline">
      {children} <ArrowRight className="size-4" aria-hidden="true" />
    </Link>
  );
}

function Offer({ icon: Icon, title, body, to }: { icon: typeof Tag; title: string; body: string; to: string }) {
  return (
    <Link to={to} className="rounded-2xl bg-white/10 p-4 ring-1 ring-white/10 transition-colors hover:bg-white/15">
      <Icon className="size-5 text-brand-300" aria-hidden="true" />
      <p className="mt-2 font-semibold text-white">{title}</p>
      <p className="mt-0.5 text-xs text-white/70">{body}</p>
    </Link>
  );
}

/** Horizontally scrolling product row with arrow buttons (swipe on touch devices). */
function Carousel({ products, loading, label, dark = false }: { products?: Card[]; loading: boolean; label: string; dark?: boolean }) {
  const track = useRef<HTMLDivElement>(null);
  const scroll = (direction: 1 | -1) => track.current?.scrollBy({ left: direction * track.current.clientWidth * 0.8, behavior: 'smooth' });
  if (loading) return <ProductGridSkeleton count={4} className="md:grid-cols-4" />;
  if (!products?.length) return <p className={clsx('text-sm', dark ? 'text-white/70' : 'text-slate-500')}>Nothing here right now.</p>;
  return (
    <div className="relative" role="region" aria-roledescription="carousel" aria-label={label}>
      <div ref={track} className="-mx-1 flex snap-x snap-mandatory gap-4 overflow-x-auto scroll-smooth px-1 pb-2 no-scrollbar">
        {products.map((p) => (
          <ProductCard key={p.id} product={p} className="w-[46%] shrink-0 snap-start sm:w-[31%] lg:w-[23%]" />
        ))}
      </div>
      {products.length > 4 && (
        <div className="mt-2 hidden justify-end gap-2 md:flex">
          {([-1, 1] as const).map((d) => (
            <button key={d} type="button" onClick={() => scroll(d)} aria-label={d < 0 ? `Previous: ${label}` : `Next: ${label}`}
              className={clsx('inline-flex size-10 items-center justify-center rounded-full ring-1 transition-colors', dark ? 'text-white ring-white/30 hover:bg-white/10' : 'bg-white ring-slate-300 hover:bg-slate-100')}>
              {d < 0 ? <ChevronLeft className="size-5" aria-hidden="true" /> : <ChevronRight className="size-5" aria-hidden="true" />}
            </button>
          ))}
        </div>
      )}
    </div>
  );
}
