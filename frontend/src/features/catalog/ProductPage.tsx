import clsx from 'clsx';
import { RotateCcw, ShieldCheck, ShoppingBag, Sparkles, Truck } from 'lucide-react';
import { useState, type ReactNode } from 'react';
import { Link, useParams } from 'react-router';
import { Alert } from '../../components/ui/Alert';
import { Button, buttonClasses } from '../../components/ui/Button';
import { QuantityStepper } from '../../components/ui/QuantityStepper';
import { PageSpinner } from '../../components/ui/Spinner';
import { ApiError } from '../../lib/api';
import { useAddToCart } from '../cart/api';
import { useProduct } from './api';
import { formatAttribute } from './attributes';
import { GarmentArt, garmentKind } from './components/GarmentArt';
import { Price, ProductBadges } from './components/Merchandising';
import { ProductCard } from './components/ProductCard';
import { Rating } from './components/Rating';
import { StockBadge } from './components/StockBadge';
import { useVariantSelection, VariantPicker } from './components/VariantPicker';
import { WishlistButton } from './components/WishlistButton';
import type { ProductDetail } from './types';

const MAX_PER_ORDER = 99;

export function ProductPage() {
  const { slug = '' } = useParams();
  const { data, isPending, error } = useProduct(slug);

  if (isPending) return <PageSpinner />;
  if (error) {
    const notFound = error instanceof ApiError && error.status === 404;
    return (
      <div className="mx-auto max-w-xl px-4 py-24 text-center">
        <h1 className="font-display text-3xl font-semibold">{notFound ? 'Product not found' : 'Something went wrong'}</h1>
        <p className="mt-2 text-slate-600">{notFound ? 'This product may have been removed.' : error.message}</p>
        <Link to="/shop" className={buttonClasses('primary', 'md', 'mt-6')}>Continue shopping</Link>
      </div>
    );
  }

  // keyed by product so the selection resets when navigating to another product
  return <ProductView key={data.product.id} detail={data} />;
}

function ProductView({ detail }: { detail: ProductDetail }) {
  const [quantity, setQuantity] = useState(1);
  const { addToCart, isPending: adding } = useAddToCart();
  const { product, department, attributes, description, related, variants, completeTheLook } = detail;
  const selection = useVariantSelection(variants);
  const { singleOption, variant, stock, needsSize } = selection;
  const purchasable = product.active && !!variant && stock > 0;
  const maxQuantity = Math.max(1, Math.min(stock, MAX_PER_ORDER));

  return (
    <div className="mx-auto max-w-7xl px-4 py-8">
      <nav aria-label="Breadcrumb" className="text-sm text-slate-500">
        <Link to="/" className="hover:text-slate-900">Home</Link>
        {department && (
          <>
            <span className="mx-1.5">/</span>
            <Link to={`/shop?category=${department.slug}`} className="hover:text-slate-900">{department.name}</Link>
          </>
        )}
        <span className="mx-1.5">/</span>
        <Link to={`/shop?category=${product.categorySlug}`} className="hover:text-slate-900">{product.categoryName}</Link>
      </nav>

      <div className="mt-6 grid gap-8 lg:grid-cols-[1.1fr_1fr] lg:gap-14">
        <Gallery detail={detail} color={selection.color} colorHex={selection.colorHex} onPickColor={selection.setColor} />

        <div className="lg:py-2">
          <Link to={`/brands/${detail.brandInfo.slug}`} className="text-xs font-bold uppercase tracking-[0.16em] text-violet-700 hover:underline">
            {product.brand}
          </Link>
          <h1 className="mt-2 font-display text-3xl font-semibold leading-tight sm:text-4xl">{product.name}</h1>
          <div className="mt-3 flex flex-wrap items-center gap-3">
            <Rating value={product.ratingAvg} count={product.ratingCount} size="md" />
            <ProductBadges product={product} className="flex-row" />
          </div>
          <div className="mt-5"><Price price={product.price} compareAtPrice={product.compareAtPrice} size="lg" /></div>

          <div className="mt-6">
            <VariantPicker selection={selection} variants={variants} sizeScale={detail.sizeScale} />
          </div>
          <StockBadge available={variant ? stock : product.availableQuantity} active={product.active} className="mt-4 text-sm" />

          <div className="mt-5 flex flex-wrap items-center gap-3">
            {purchasable && <QuantityStepper value={quantity} max={maxQuantity} onChange={setQuantity} />}
            <Button
              size="lg"
              className="min-w-48 flex-1"
              disabled={!purchasable}
              isLoading={adding}
              onClick={() => addToCart(product.id, quantity, product.name, variant?.id)}
            >
              <ShoppingBag className="size-5" aria-hidden="true" />
              {!product.active ? 'Currently unavailable' : needsSize ? 'Choose a size' : stock > 0 ? 'Add to cart' : singleOption ? 'Sold out' : 'Sold out in this size'}
            </Button>
            <WishlistButton productId={product.id} productName={product.name} withLabel />
          </div>

          <ul className="mt-6 grid gap-2 rounded-2xl bg-white p-4 text-sm text-slate-700 shadow-card sm:grid-cols-3">
            <li className="flex items-center gap-2"><Truck className="size-4 text-brand-600" aria-hidden="true" /> Free shipping over $100</li>
            <li className="flex items-center gap-2"><RotateCcw className="size-4 text-brand-600" aria-hidden="true" /> Cancel before it ships</li>
            <li className="flex items-center gap-2"><ShieldCheck className="size-4 text-brand-600" aria-hidden="true" /> Secure checkout</li>
          </ul>

          <div className="mt-6 divide-y divide-slate-200 border-y border-slate-200">
            <Details title="Description" open>
              <p className="leading-relaxed text-slate-700">{description}</p>
              {detail.styles.length + detail.occasions.length > 0 && (
                <div className="mt-3 flex flex-wrap gap-2">
                  {detail.styles.map((s) => (
                    <Link key={s.slug} to={`/shop?style=${s.slug}`} className="rounded-full bg-violet-100 px-3 py-1 text-xs font-semibold text-violet-800 hover:bg-violet-200">{s.name}</Link>
                  ))}
                  {detail.occasions.map((o) => (
                    <Link key={o.slug} to={`/shop?occasion=${o.slug}`} className="rounded-full bg-slate-100 px-3 py-1 text-xs font-semibold text-slate-700 hover:bg-slate-200">{o.name}</Link>
                  ))}
                </div>
              )}
            </Details>
            {(detail.material || detail.care) && (
              <Details title="Material and care">
                {detail.material && <p className="text-slate-700"><span className="font-semibold">Material:</span> {detail.material}</p>}
                {detail.care && <p className="mt-1 text-slate-700"><span className="font-semibold">Care:</span> {detail.care}</p>}
              </Details>
            )}
            {attributes.length > 0 && (
              <Details title="Fit and details">
                <dl className="grid grid-cols-2 gap-x-4 gap-y-1.5 text-sm">
                  {attributes.filter((a) => a.key !== 'formality').map((a) => (
                    <div key={a.key} className="contents">
                      <dt className="text-slate-500">{a.label}</dt>
                      <dd className="font-medium text-slate-900">{formatAttribute(a)}</dd>
                    </div>
                  ))}
                  <dt className="text-slate-500">SKU</dt>
                  <dd className="font-mono text-slate-900">{variant?.sku ?? product.sku}</dd>
                </dl>
              </Details>
            )}
          </div>
        </div>
      </div>

      {completeTheLook.length > 0 && (
        <section className="mt-16" aria-labelledby="look">
          <div className="flex flex-wrap items-end justify-between gap-4">
            <div>
              <p className="text-xs font-bold uppercase tracking-[0.16em] text-brand-600">Complete the look</p>
              <h2 id="look" className="mt-1 font-display text-2xl font-semibold sm:text-3xl">Pieces that pair with it</h2>
            </div>
            <Link to="/outfit" className={buttonClasses('secondary', 'md')}>
              <Sparkles className="size-4" aria-hidden="true" /> Build a whole outfit
            </Link>
          </div>
          <div className="mt-6 grid grid-cols-2 gap-x-4 gap-y-8 md:grid-cols-4">
            {completeTheLook.map((p) => <ProductCard key={p.id} product={p} />)}
          </div>
        </section>
      )}

      {related.length > 0 && (
        <section className="mt-16" aria-labelledby="related">
          <h2 id="related" className="font-display text-2xl font-semibold sm:text-3xl">More {product.categoryName.toLowerCase()}</h2>
          <div className="mt-6 grid grid-cols-2 gap-x-4 gap-y-8 md:grid-cols-4">
            {related.map((p) => <ProductCard key={p.id} product={p} />)}
          </div>
        </section>
      )}

      {!product.active && (
        <div className="mt-8"><Alert tone="info">This product has been discontinued.</Alert></div>
      )}
    </div>
  );
}

/** Photos for the chosen color when uploaded, otherwise the illustration with the other colorways as thumbnails. */
function Gallery({ detail, color, colorHex, onPickColor }: {
  detail: ProductDetail;
  color: string | undefined;
  colorHex: string | null;
  onPickColor: (color: string) => void;
}) {
  const photos = detail.images.filter((i) => !i.color || i.color === color);
  const [index, setIndex] = useState(0);
  const kind = garmentKind(detail.product.categorySlug);
  const colors = [...new Map(detail.variants.map((v) => [v.color, v])).values()].filter((c) => c.color !== 'Standard');

  if (photos.length > 0) {
    const current = photos[Math.min(index, photos.length - 1)];
    return (
      <div className="lg:sticky lg:top-24 lg:self-start">
        <img src={current.url} alt={current.altText ?? detail.product.name} className="aspect-[5/6] w-full rounded-3xl object-cover shadow-card" />
        {photos.length > 1 && (
          <div className="mt-3 flex gap-3 overflow-x-auto no-scrollbar">
            {photos.map((p, i) => (
              <button key={p.url} type="button" onClick={() => setIndex(i)} aria-label={`Photo ${i + 1}`} aria-pressed={i === index}
                className={clsx('w-20 shrink-0 overflow-hidden rounded-xl ring-2', i === index ? 'ring-slate-900' : 'ring-transparent')}>
                <img src={p.url} alt="" className="aspect-[5/6] w-full object-cover" />
              </button>
            ))}
          </div>
        )}
      </div>
    );
  }
  return (
    <div className="lg:sticky lg:top-24 lg:self-start">
      <div className="overflow-hidden rounded-3xl shadow-card">
        <GarmentArt kind={kind} color={colorHex} label={`${detail.product.name}${color && color !== 'Standard' ? ` in ${color}` : ''}`} className="aspect-[5/6]" />
      </div>
      {colors.length > 1 && (
        <div className="mt-3 flex gap-3 overflow-x-auto no-scrollbar" aria-label="Colorways">
          {colors.map((c) => (
            <button key={c.color} type="button" onClick={() => onPickColor(c.color)} aria-label={`Show ${c.color}`} aria-pressed={c.color === color}
              className={clsx('w-20 shrink-0 overflow-hidden rounded-xl ring-2 transition', c.color === color ? 'ring-slate-900' : 'ring-transparent hover:ring-slate-300')}>
              <GarmentArt kind={kind} color={c.colorHex} className="aspect-[5/6]" />
            </button>
          ))}
        </div>
      )}
      <p className="mt-3 text-xs text-slate-500">Illustration shown. Demo store: products are fictional sample data.</p>
    </div>
  );
}

function Details({ title, open = false, children }: { title: string; open?: boolean; children: ReactNode }) {
  return (
    <details open={open} className="group py-4">
      <summary className="flex cursor-pointer list-none items-center justify-between text-sm font-bold uppercase tracking-wider">
        {title}
        <span className="text-lg text-slate-400 transition-transform group-open:rotate-45" aria-hidden="true">+</span>
      </summary>
      <div className="mt-3 text-sm">{children}</div>
    </details>
  );
}
