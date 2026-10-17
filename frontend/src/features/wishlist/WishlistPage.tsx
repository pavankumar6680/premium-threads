import { Heart } from 'lucide-react';
import { Link } from 'react-router';
import { Alert } from '../../components/ui/Alert';
import { buttonClasses } from '../../components/ui/Button';
import { PageSpinner } from '../../components/ui/Spinner';
import { pluralize } from '../../lib/format';
import { ProductCard } from '../catalog/components/ProductCard';
import { useWishlist } from './api';

export function WishlistPage() {
  const { data, isPending, error } = useWishlist();

  if (isPending) return <PageSpinner />;
  if (error) return <div className="mx-auto max-w-3xl px-4 py-10"><Alert tone="error">{error.message}</Alert></div>;

  return (
    <div className="mx-auto max-w-7xl px-4 py-8">
      <h1 className="text-2xl font-bold tracking-tight sm:text-3xl">Wishlist</h1>
      {data.length === 0 ? (
        <div className="mt-8 rounded-2xl border border-dashed border-slate-300 bg-white px-6 py-16 text-center">
          <Heart className="mx-auto size-10 text-slate-300" aria-hidden="true" />
          <h2 className="mt-3 text-lg font-semibold">Nothing saved yet</h2>
          <p className="mt-1 text-sm text-slate-600">Tap the heart on any product to save it for later.</p>
          <Link to="/shop" className={buttonClasses('primary', 'md', 'mt-5')}>Browse products</Link>
        </div>
      ) : (
        <>
          <p className="mt-1 text-sm text-slate-600">{pluralize(data.length, 'saved product')}</p>
          <div className="mt-6 grid grid-cols-2 gap-4 md:grid-cols-3 xl:grid-cols-4">
            {data.map((product) => (
              <ProductCard key={product.id} product={product} />
            ))}
          </div>
        </>
      )}
    </div>
  );
}
