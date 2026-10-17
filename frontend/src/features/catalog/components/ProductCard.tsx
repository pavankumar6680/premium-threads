import { ShoppingCart } from 'lucide-react';
import { Link } from 'react-router';
import { Button } from '../../../components/ui/Button';
import { formatMoney } from '../../../lib/format';
import { useAddToCart } from '../../cart/api';
import type { ProductSummary } from '../types';
import { ProductImage } from './ProductImage';
import { Rating } from './Rating';
import { StockBadge } from './StockBadge';
import { WishlistButton } from './WishlistButton';

export function ProductCard({ product }: { product: ProductSummary }) {
  const { addToCart, isPending, pendingProductId } = useAddToCart();
  const purchasable = product.active && product.availableQuantity > 0;

  return (
    <article className="group relative flex flex-col rounded-2xl border border-slate-200 bg-white p-3 shadow-xs transition-shadow hover:shadow-md">
      <div className="relative">
        <Link to={`/products/${product.slug}`} tabIndex={-1} aria-hidden="true">
          <ProductImage categorySlug={product.categorySlug} alt={product.name} />
        </Link>
        <WishlistButton productId={product.id} productName={product.name} className="absolute right-2 top-2" />
      </div>
      <div className="mt-3 flex flex-1 flex-col px-1">
        <p className="text-xs font-medium uppercase tracking-wide text-slate-500">{product.brand}</p>
        <h3 className="mt-0.5 line-clamp-2 text-sm font-semibold leading-snug">
          <Link to={`/products/${product.slug}`} className="hover:text-brand-700">
            {product.name}
          </Link>
        </h3>
        <div className="mt-1.5">
          <Rating value={product.ratingAvg} count={product.ratingCount} />
        </div>
        <div className="mt-auto flex items-end justify-between gap-2 pt-3">
          <div>
            <p className="text-lg font-bold">{formatMoney(product.price)}</p>
            <StockBadge available={product.availableQuantity} active={product.active} />
          </div>
          <Button
            size="sm"
            variant={purchasable ? 'primary' : 'secondary'}
            disabled={!purchasable}
            isLoading={isPending && pendingProductId === product.id}
            onClick={() => addToCart(product.id, 1, product.name)}
            aria-label={`Add ${product.name} to cart`}
          >
            <ShoppingCart className="size-4" aria-hidden="true" />
            <span className="hidden sm:inline">Add</span>
          </Button>
        </div>
      </div>
    </article>
  );
}

export function ProductGridSkeleton({ count = 8 }: { count?: number }) {
  return (
    <div className="grid grid-cols-2 gap-4 md:grid-cols-3 xl:grid-cols-4" aria-hidden="true">
      {Array.from({ length: count }, (_, i) => (
        <div key={i} className="rounded-2xl border border-slate-200 bg-white p-3">
          <div className="aspect-square animate-pulse rounded-xl bg-slate-100" />
          <div className="mt-3 h-3 w-1/3 animate-pulse rounded bg-slate-100" />
          <div className="mt-2 h-4 w-4/5 animate-pulse rounded bg-slate-100" />
          <div className="mt-4 h-5 w-1/4 animate-pulse rounded bg-slate-100" />
        </div>
      ))}
    </div>
  );
}
