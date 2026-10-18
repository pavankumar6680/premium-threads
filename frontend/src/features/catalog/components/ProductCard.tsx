import clsx from 'clsx';
import { Eye, ShoppingBag } from 'lucide-react';
import { useState } from 'react';
import { Link } from 'react-router';
import { useAddToCart } from '../../cart/api';
import type { ProductCard as Card } from '../types';
import { GarmentArt, garmentKind } from './GarmentArt';
import { ColorDots, isSingleOption, Price, ProductBadges, sizeRange } from './Merchandising';
import { QuickView } from './QuickView';
import { WishlistButton } from './WishlistButton';

/**
 * Listing card: picture with a second colorway on hover, badges, brand, name, price (with the original
 * price on sale), colors and sizes, wishlist, Quick View and Add to cart. Products with sizes or colors open
 * Quick View to choose before adding.
 */
export function ProductCard({ product, className }: { product: Card; className?: string }) {
  const [quickView, setQuickView] = useState(false);
  const { addToCart, isPending, pendingProductId } = useAddToCart();
  const purchasable = product.active && product.availableQuantity > 0;
  const single = isSingleOption(product);
  const primary = product.colors[0]?.hex;
  const alternate = product.colors[1]?.hex ?? primary;
  const kind = garmentKind(product.categorySlug);

  const add = () => (single ? addToCart(product.id, 1, product.name, undefined, product.slug) : setQuickView(true));

  return (
    <article className={clsx('group relative flex flex-col', className)} aria-label={product.name}>
      <div className="relative overflow-hidden rounded-2xl bg-slate-100 shadow-card">
        <Link to={`/products/${product.slug}`} tabIndex={-1} aria-hidden="true" className="block aspect-[5/6]">
          {product.imageUrl ? (
            <img src={product.imageUrl} alt="" loading="lazy" className="h-full w-full object-cover transition-transform duration-500 group-hover:scale-105" />
          ) : (
            <>
              <GarmentArt kind={kind} color={primary} className="transition duration-500 group-hover:scale-105 group-hover:opacity-0" />
              <GarmentArt kind={kind} color={alternate} className="absolute inset-0 scale-105 opacity-0 transition duration-500 group-hover:scale-100 group-hover:opacity-100" />
            </>
          )}
        </Link>
        <ProductBadges product={product} className="absolute left-3 top-3" />
        <WishlistButton productId={product.id} productName={product.name} className="absolute right-3 top-3" />
        <div className="absolute inset-x-3 bottom-3 flex gap-2 transition-all duration-300 sm:translate-y-3 sm:opacity-0 sm:group-hover:translate-y-0 sm:group-hover:opacity-100 sm:group-focus-within:translate-y-0 sm:group-focus-within:opacity-100">
          <button
            type="button"
            onClick={() => setQuickView(true)}
            className="inline-flex h-10 flex-1 items-center justify-center gap-1.5 rounded-full bg-white/95 text-sm font-semibold text-slate-900 shadow-md backdrop-blur hover:bg-white"
            aria-label={`Quick view: ${product.name}`}
          >
            <Eye className="size-4" aria-hidden="true" /> <span className="hidden sm:inline">Quick view</span>
          </button>
          <button
            type="button"
            onClick={add}
            disabled={!purchasable || (isPending && pendingProductId === product.id)}
            className="inline-flex h-10 flex-1 items-center justify-center gap-1.5 rounded-full bg-slate-900 text-sm font-semibold text-white shadow-md hover:bg-brand-600 disabled:bg-slate-400"
            aria-label={purchasable ? `Add ${product.name} to cart` : `${product.name} is sold out`}
          >
            <ShoppingBag className="size-4" aria-hidden="true" />
            <span className="hidden sm:inline">{purchasable ? 'Add to cart' : 'Sold out'}</span>
          </button>
        </div>
      </div>
      <div className="mt-3 flex flex-1 flex-col px-0.5">
        <Link to={`/brands/${product.brandSlug}`} className="text-xs font-bold uppercase tracking-[0.12em] text-slate-500 hover:text-violet-700">
          {product.brand}
        </Link>
        <h3 className="mt-1 line-clamp-2 text-sm font-semibold leading-snug">
          <Link to={`/products/${product.slug}`} className="hover:text-brand-600">{product.name}</Link>
        </h3>
        <div className="mt-1.5">
          <Price price={product.price} compareAtPrice={product.compareAtPrice} />
        </div>
        <div className="mt-2 flex items-center justify-between gap-2">
          <ColorDots colors={product.colors} />
          <span className="text-xs text-slate-500">{sizeRange(product.sizes)}</span>
        </div>
      </div>
      <QuickView slug={product.slug} open={quickView} onClose={() => setQuickView(false)} />
    </article>
  );
}

export function ProductGridSkeleton({ count = 8, className }: { count?: number; className?: string }) {
  return (
    <div className={clsx('grid grid-cols-2 gap-x-4 gap-y-8 md:grid-cols-3 xl:grid-cols-4', className)} aria-hidden="true">
      {Array.from({ length: count }, (_, i) => (
        <div key={i}>
          <div className="aspect-[5/6] animate-pulse rounded-2xl bg-slate-200/70" />
          <div className="mt-3 h-3 w-1/3 animate-pulse rounded bg-slate-200/70" />
          <div className="mt-2 h-4 w-4/5 animate-pulse rounded bg-slate-200/70" />
          <div className="mt-2 h-4 w-1/4 animate-pulse rounded bg-slate-200/70" />
        </div>
      ))}
    </div>
  );
}
