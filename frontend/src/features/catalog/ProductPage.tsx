import { ShieldCheck, ShoppingCart, Truck } from 'lucide-react';
import { useState } from 'react';
import { Link, useParams } from 'react-router';
import { Alert } from '../../components/ui/Alert';
import { Button, buttonClasses } from '../../components/ui/Button';
import { QuantityStepper } from '../../components/ui/QuantityStepper';
import { PageSpinner } from '../../components/ui/Spinner';
import { ApiError } from '../../lib/api';
import { formatMoney } from '../../lib/format';
import { useAddToCart } from '../cart/api';
import { useProduct } from './api';
import type { ProductDetail } from './types';
import { formatAttribute } from './attributes';
import { ProductCard } from './components/ProductCard';
import { ProductImage } from './components/ProductImage';
import { Rating } from './components/Rating';
import { StockBadge } from './components/StockBadge';
import { WishlistButton } from './components/WishlistButton';

const MAX_PER_ORDER = 99;
const tierLabels = ['', 'Budget pick', 'Mid-range', 'Premium'];

export function ProductPage() {
  const { slug = '' } = useParams();
  const { data, isPending, error } = useProduct(slug);

  if (isPending) return <PageSpinner />;
  if (error) {
    const notFound = error instanceof ApiError && error.status === 404;
    return (
      <div className="mx-auto max-w-xl px-4 py-24 text-center">
        <h1 className="text-2xl font-bold">{notFound ? 'Product not found' : 'Something went wrong'}</h1>
        <p className="mt-2 text-slate-600">{notFound ? 'This product may have been removed.' : error.message}</p>
        <Link to="/shop" className={buttonClasses('primary', 'md', 'mt-6')}>Continue shopping</Link>
      </div>
    );
  }

  // keyed by product so quantity resets when navigating to a related product
  return <ProductView key={data.product.id} detail={data} />;
}

function ProductView({ detail }: { detail: ProductDetail }) {
  const [quantity, setQuantity] = useState(1);
  const { addToCart, isPending: adding } = useAddToCart();
  const { product, department, attributes, description, images, related } = detail;
  const purchasable = product.active && product.availableQuantity > 0;
  const maxQuantity = Math.max(1, Math.min(product.availableQuantity, MAX_PER_ORDER));

  return (
    <div className="mx-auto max-w-7xl px-4 py-8">
      <nav aria-label="Breadcrumb" className="text-sm text-slate-500">
        <Link to="/shop" className="hover:text-slate-800">Shop</Link>
        {department && (
          <>
            <span className="mx-1.5">/</span>
            <Link to={`/shop?category=${department.slug}`} className="hover:text-slate-800">{department.name}</Link>
          </>
        )}
        <span className="mx-1.5">/</span>
        <Link to={`/shop?category=${product.categorySlug}`} className="hover:text-slate-800">{product.categoryName}</Link>
      </nav>

      <div className="mt-6 grid gap-8 lg:grid-cols-2 lg:gap-12">
        <ProductImage categorySlug={product.categorySlug} imageUrl={images[0]?.url} alt={images[0]?.altText ?? product.name} size="lg" />

        <div>
          <p className="text-sm font-semibold uppercase tracking-wide text-brand-700">{product.brand}</p>
          <h1 className="mt-1 text-3xl font-bold tracking-tight">{product.name}</h1>
          <div className="mt-3 flex flex-wrap items-center gap-3">
            <Rating value={product.ratingAvg} count={product.ratingCount} size="md" />
            <span className="rounded-full bg-slate-100 px-2.5 py-0.5 text-xs font-medium text-slate-700">
              {tierLabels[product.qualityTier]}
            </span>
          </div>
          <p className="mt-5 text-3xl font-bold">{formatMoney(product.price)}</p>
          <StockBadge available={product.availableQuantity} active={product.active} className="mt-1 text-sm" />
          <p className="mt-5 leading-relaxed text-slate-700">{description}</p>

          <div className="mt-6 flex flex-wrap items-center gap-3">
            {purchasable && <QuantityStepper value={quantity} max={maxQuantity} onChange={setQuantity} />}
            <Button size="lg" disabled={!purchasable} isLoading={adding} onClick={() => addToCart(product.id, quantity, product.name)}>
              <ShoppingCart className="size-5" aria-hidden="true" />
              {purchasable ? 'Add to cart' : 'Currently unavailable'}
            </Button>
            <WishlistButton productId={product.id} productName={product.name} withLabel />
          </div>

          <ul className="mt-6 space-y-2 text-sm text-slate-600">
            <li className="flex items-center gap-2"><Truck className="size-4 text-brand-600" aria-hidden="true" /> Free shipping on orders over $100</li>
            <li className="flex items-center gap-2"><ShieldCheck className="size-4 text-brand-600" aria-hidden="true" /> Cancel any time before your order ships</li>
          </ul>
        </div>
      </div>

      {attributes.length > 0 && (
        <section className="mt-12" aria-labelledby="specs">
          <h2 id="specs" className="text-xl font-bold">Specifications</h2>
          <dl className="mt-4 overflow-hidden rounded-2xl border border-slate-200 bg-white">
            {attributes.map((attribute, i) => (
              <div key={attribute.key} className={`grid grid-cols-2 gap-4 px-5 py-3 text-sm sm:grid-cols-3 ${i % 2 ? 'bg-slate-50' : ''}`}>
                <dt className="font-medium text-slate-600">{attribute.label}</dt>
                <dd className="text-slate-900 sm:col-span-2">{formatAttribute(attribute)}</dd>
              </div>
            ))}
            <div className={`grid grid-cols-2 gap-4 px-5 py-3 text-sm sm:grid-cols-3 ${attributes.length % 2 ? 'bg-slate-50' : ''}`}>
              <dt className="font-medium text-slate-600">SKU</dt>
              <dd className="font-mono text-slate-900 sm:col-span-2">{product.sku}</dd>
            </div>
          </dl>
        </section>
      )}

      {related.length > 0 && (
        <section className="mt-12" aria-labelledby="related">
          <h2 id="related" className="text-xl font-bold">More in {product.categoryName}</h2>
          <div className="mt-4 grid grid-cols-2 gap-4 md:grid-cols-4">
            {related.map((p) => (
              <ProductCard key={p.id} product={p} />
            ))}
          </div>
        </section>
      )}

      {!product.active && (
        <div className="mt-8">
          <Alert tone="info">This product has been discontinued.</Alert>
        </div>
      )}
    </div>
  );
}
