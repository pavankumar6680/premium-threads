import clsx from 'clsx';
import { GarmentArt, garmentKind } from './GarmentArt';

/**
 * A product picture: the uploaded photo when there is one, otherwise the garment illustration in the
 * selected color. Portrait 5:6, like fashion photography.
 */
export function ProductImage({ categorySlug, imageUrl, colorHex, alt, className }: {
  categorySlug: string;
  imageUrl?: string | null;
  colorHex?: string | null;
  alt: string;
  /** kept for older callers; pictures scale with their container */
  size?: 'sm' | 'md' | 'lg';
  className?: string;
}) {
  if (imageUrl) {
    return <img src={imageUrl} alt={alt} loading="lazy" className={clsx('aspect-[5/6] w-full rounded-xl object-cover', className)} />;
  }
  return (
    <div className={clsx('aspect-[5/6] w-full overflow-hidden rounded-xl', className)}>
      <GarmentArt kind={garmentKind(categorySlug)} color={colorHex} label={alt} />
    </div>
  );
}
