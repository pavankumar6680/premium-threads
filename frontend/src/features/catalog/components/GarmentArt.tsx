import clsx from 'clsx';
import type { ReactNode } from 'react';

/**
 * Illustrated product pictures. The demo catalog has no product photography (and must not use brand imagery),
 * so every garment type has a simple flat illustration drawn in the selected variant's color. Uploaded
 * photos, when an admin adds them, always take precedence (see ProductImage).
 */
export type GarmentKind =
  | 'tshirt'
  | 'shirt'
  | 'jeans'
  | 'trousers'
  | 'jacket'
  | 'hoodie'
  | 'sweater'
  | 'kurta'
  | 'dress'
  | 'top'
  | 'skirt'
  | 'watch'
  | 'sunglasses'
  | 'handbag'
  | 'backpack'
  | 'wallet'
  | 'belt'
  | 'jewelry'
  | 'cap'
  | 'scarf'
  | 'tag';

const KINDS: Record<string, GarmentKind> = {
  tshirts: 'tshirt',
  shirts: 'shirt',
  jeans: 'jeans',
  trousers: 'trousers',
  jackets: 'jacket',
  hoodies: 'hoodie',
  sweaters: 'sweater',
  'ethnic-wear': 'kurta',
  dresses: 'dress',
  tops: 'top',
  skirts: 'skirt',
  watches: 'watch',
  sunglasses: 'sunglasses',
  handbags: 'handbag',
  backpacks: 'backpack',
  wallets: 'wallet',
  belts: 'belt',
  jewelry: 'jewelry',
  caps: 'cap',
  scarves: 'scarf',
};

/** "men-tshirts" → tshirt, "watches" → watch; unknown categories get a neutral tag. */
export function garmentKind(categorySlug: string): GarmentKind {
  return KINDS[categorySlug.replace(/^(men|women)-/, '')] ?? 'tag';
}

const DEFAULT_COLOR = '#9A9DA1';

function channels(hex: string): [number, number, number] {
  const value = /^#[0-9a-f]{6}$/i.test(hex) ? hex : DEFAULT_COLOR;
  return [1, 3, 5].map((i) => parseInt(value.slice(i, i + 2), 16)) as [number, number, number];
}

function toHex(rgb: number[]): string {
  return `#${rgb.map((c) => Math.round(Math.max(0, Math.min(255, c))).toString(16).padStart(2, '0')).join('')}`;
}

/** Mixes a color towards another (t = 0 keeps a, t = 1 gives b). */
export function mix(a: string, b: string, t: number): string {
  const x = channels(a);
  const y = channels(b);
  return toHex(x.map((c, i) => c + (y[i] - c) * t));
}

function luminance(hex: string): number {
  const [r, g, b] = channels(hex);
  return (0.299 * r + 0.587 * g + 0.114 * b) / 255;
}

export function GarmentArt({ kind, color, label, className }: {
  kind: GarmentKind;
  color?: string | null;
  /** accessible name; omit when the picture is decorative */
  label?: string;
  className?: string;
}) {
  const fill = color && /^#[0-9a-f]{6}$/i.test(color) ? color : DEFAULT_COLOR;
  const light = luminance(fill) > 0.8;
  const dark = luminance(fill) < 0.22;
  // dark garments get a lighter outline and shading, otherwise their seams disappear
  const outline = dark ? mix(fill, '#ffffff', 0.32) : mix(fill, '#181818', light ? 0.35 : 0.45);
  const shade = dark ? mix(fill, '#ffffff', 0.12) : mix(fill, '#181818', 0.22);
  const detail = luminance(fill) > 0.45 ? mix(fill, '#181818', 0.55) : mix(fill, '#ffffff', 0.55);
  const background = mix(light ? '#d9cfc3' : fill, '#faf8f5', light ? 0.6 : 0.86);
  return (
    <svg
      viewBox="0 0 200 240"
      role={label ? 'img' : undefined}
      aria-label={label}
      aria-hidden={label ? undefined : true}
      className={clsx('block h-full w-full', className)}
    >
      <rect width="200" height="240" fill={background} />
      <ellipse cx="100" cy="222" rx="62" ry="7" fill="#181818" opacity="0.07" />
      <g stroke={outline} strokeWidth="2.5" strokeLinejoin="round" strokeLinecap="round">
        {SHAPES[kind]({ fill, shade, detail, outline })}
      </g>
    </svg>
  );
}

type Paint = { fill: string; shade: string; detail: string; outline: string };

const SHAPES: Record<GarmentKind, (p: Paint) => ReactNode> = {
  tshirt: ({ fill, shade }) => (
    <>
      <path d="M72 44 L56 50 L28 82 L48 100 L62 88 L62 206 L138 206 L138 88 L152 100 L172 82 L144 50 L128 44 Q100 64 72 44 Z" fill={fill} />
      <path d="M72 44 Q100 64 128 44" fill="none" />
      <path d="M62 196 L138 196" fill="none" stroke={shade} />
    </>
  ),
  shirt: ({ fill, detail }) => (
    <>
      <path d="M74 40 L52 48 L30 112 L30 196 L48 198 L52 126 L60 104 L60 210 L140 210 L140 104 L148 126 L152 198 L170 196 L170 112 L148 48 L126 40 L100 56 Z" fill={fill} />
      <path d="M74 40 L100 56 L88 74 Z M126 40 L100 56 L112 74 Z" fill={fill} />
      <path d="M100 56 L100 210" fill="none" />
      {[84, 112, 140, 168, 196].map((y) => <circle key={y} cx="106" cy={y} r="2.6" fill={detail} stroke="none" />)}
    </>
  ),
  hoodie: ({ fill, shade, detail }) => (
    <>
      <path d="M70 52 L50 60 L28 120 L28 200 L48 202 L52 130 L60 112 L60 208 L140 208 L140 112 L148 130 L152 202 L172 200 L172 120 L150 60 L130 52 Z" fill={fill} />
      <path d="M70 52 Q66 22 100 18 Q134 22 130 52 Q116 70 100 70 Q84 70 70 52 Z" fill={shade} />
      <path d="M78 54 Q100 74 122 54" fill="none" />
      <path d="M94 70 L92 100 M106 70 L108 100" fill="none" stroke={detail} />
      <path d="M74 150 L126 150 L132 186 L68 186 Z" fill={shade} />
    </>
  ),
  sweater: ({ fill, shade }) => (
    <>
      <path d="M74 42 L52 50 L28 118 L28 198 L48 200 L52 128 L60 108 L60 208 L140 208 L140 108 L148 128 L152 200 L172 198 L172 118 L148 50 L126 42 Q100 60 74 42 Z" fill={fill} />
      <path d="M74 42 Q100 60 126 42" fill="none" />
      <path d="M60 196 L140 196 M28 188 L48 190 M152 190 L172 188" fill="none" stroke={shade} strokeWidth="4" />
      <path d="M80 90 L80 180 M100 90 L100 180 M120 90 L120 180" fill="none" stroke={shade} strokeDasharray="4 6" />
    </>
  ),
  jacket: ({ fill, shade, detail }) => (
    <>
      <path d="M72 40 L50 48 L26 116 L26 202 L48 204 L52 128 L60 108 L60 212 L96 212 L96 78 L104 78 L104 212 L140 212 L140 108 L148 128 L152 204 L174 202 L174 116 L150 48 L128 40 L100 70 Z" fill={fill} />
      <path d="M72 40 L96 78 L80 96 L74 66 Z M128 40 L104 78 L120 96 L126 66 Z" fill={shade} />
      <path d="M96 78 L100 70 L104 78" fill={mix(fill, '#ffffff', 0.7)} />
      <path d="M68 150 L88 150 M112 150 L132 150" fill="none" stroke={detail} />
    </>
  ),
  kurta: ({ fill, detail }) => (
    <>
      <path d="M78 38 L56 46 L32 104 L32 170 L50 172 L54 116 L62 100 L60 222 L140 222 L138 100 L146 116 L150 172 L168 170 L168 104 L144 46 L122 38 Q100 50 78 38 Z" fill={fill} />
      <path d="M84 36 L84 30 L116 30 L116 36" fill={fill} />
      <path d="M100 42 L100 110" fill="none" stroke={detail} />
      {[56, 74, 92].map((y) => <circle key={y} cx="100" cy={y} r="2.6" fill={detail} stroke="none" />)}
      <path d="M62 206 L138 206" fill="none" stroke={detail} strokeDasharray="3 5" />
    </>
  ),
  dress: ({ fill, shade }) => (
    <>
      <path d="M82 26 L86 26 L92 52 Q100 58 108 52 L114 26 L118 26 L124 60 Q130 84 120 104 L160 214 Q100 230 40 214 L80 104 Q70 84 76 60 Z" fill={fill} />
      <path d="M80 104 Q100 112 120 104" fill="none" stroke={shade} strokeWidth="5" />
      <path d="M90 120 L74 210 M110 120 L126 210 M100 120 L100 218" fill="none" stroke={shade} />
    </>
  ),
  top: ({ fill, shade }) => (
    <>
      <path d="M68 52 L50 60 L36 98 L54 106 L64 86 L62 176 Q100 186 138 176 L136 86 L146 106 L164 98 L150 60 L132 52 Q100 84 68 52 Z" fill={fill} />
      <path d="M68 52 Q100 84 132 52" fill="none" />
      <path d="M62 164 Q100 174 138 164" fill="none" stroke={shade} />
    </>
  ),
  jeans: ({ fill, shade, detail }) => (
    <>
      <path d="M62 30 L138 30 L146 214 L108 214 L100 96 L92 214 L54 214 Z" fill={fill} />
      <path d="M62 30 L138 30 L139 44 L61 44 Z" fill={shade} />
      <path d="M100 44 L100 96 M70 44 Q76 64 96 62 M130 44 Q124 64 104 62" fill="none" stroke={detail} />
      <path d="M58 200 L92 200 M108 200 L142 200" fill="none" stroke={detail} strokeDasharray="3 4" />
    </>
  ),
  trousers: ({ fill, shade }) => (
    <>
      <path d="M64 30 L136 30 L144 214 L108 214 L100 92 L92 214 L56 214 Z" fill={fill} />
      <path d="M64 30 L136 30 L137 42 L63 42 Z" fill={shade} />
      <path d="M80 42 L76 212 M120 42 L124 212" fill="none" stroke={shade} />
    </>
  ),
  skirt: ({ fill, shade }) => (
    <>
      <path d="M70 60 L130 60 L156 188 Q100 202 44 188 Z" fill={fill} />
      <path d="M70 60 L130 60 L131 74 L69 74 Z" fill={shade} />
      <path d="M86 74 L74 192 M100 74 L100 196 M114 74 L126 192" fill="none" stroke={shade} />
    </>
  ),
  watch: ({ fill, detail }) => (
    <>
      <path d="M82 20 L118 20 L114 70 L86 70 Z M86 170 L114 170 L118 220 L82 220 Z" fill={fill} />
      <circle cx="100" cy="120" r="50" fill={fill} />
      <circle cx="100" cy="120" r="40" fill="#fbfaf7" />
      <path d="M100 120 L100 92 M100 120 L120 132" fill="none" stroke="#181818" strokeWidth="3" />
      {[0, 90, 180, 270].map((a) => (
        <circle key={a} cx={100 + 32 * Math.cos((a * Math.PI) / 180)} cy={120 + 32 * Math.sin((a * Math.PI) / 180)} r="2.5" fill={detail} stroke="none" />
      ))}
      <rect x="148" y="114" width="8" height="12" rx="2" fill={fill} />
    </>
  ),
  sunglasses: ({ fill, outline }) => (
    <>
      <path d="M20 100 L180 100" fill="none" stroke={fill} strokeWidth="6" />
      <path d="M28 100 Q28 150 62 150 Q92 150 92 108 L92 100 Z" fill="#2b2b2e" stroke={fill} strokeWidth="6" />
      <path d="M172 100 Q172 150 138 150 Q108 150 108 108 L108 100 Z" fill="#2b2b2e" stroke={fill} strokeWidth="6" />
      <path d="M92 104 Q100 92 108 104" fill="none" stroke={fill} strokeWidth="6" />
      <path d="M44 112 Q52 106 62 110" fill="none" stroke="#ffffff" strokeOpacity="0.5" />
      <path d="M124 112 Q132 106 142 110" fill="none" stroke="#ffffff" strokeOpacity="0.5" />
      <path d="M20 100 L20 92 M180 100 L180 92" fill="none" stroke={outline} />
    </>
  ),
  handbag: ({ fill, shade, detail }) => (
    <>
      <path d="M70 92 Q70 44 100 44 Q130 44 130 92" fill="none" stroke={shade} strokeWidth="7" />
      <path d="M44 92 L156 92 L168 204 L32 204 Z" fill={fill} />
      <path d="M44 92 L156 92 L150 132 Q100 146 50 132 Z" fill={shade} />
      <rect x="92" y="126" width="16" height="14" rx="3" fill={detail} />
    </>
  ),
  backpack: ({ fill, shade, detail }) => (
    <>
      <path d="M80 44 Q80 22 100 22 Q120 22 120 44" fill="none" stroke={shade} strokeWidth="6" />
      <rect x="48" y="40" width="104" height="168" rx="28" fill={fill} />
      <rect x="64" y="124" width="72" height="66" rx="12" fill={shade} />
      <path d="M64 140 L136 140" fill="none" stroke={detail} />
      <path d="M58 70 Q100 52 142 70" fill="none" stroke={detail} strokeDasharray="3 4" />
    </>
  ),
  wallet: ({ fill, shade, detail }) => (
    <>
      <rect x="36" y="74" width="128" height="96" rx="12" fill={fill} />
      <path d="M36 98 L164 98" fill="none" stroke={shade} />
      <path d="M120 112 L164 112 L164 148 L120 148 Q110 130 120 112 Z" fill={shade} />
      <circle cx="134" cy="130" r="4" fill={detail} stroke="none" />
      <path d="M48 160 L108 160" fill="none" stroke={detail} strokeDasharray="3 4" />
    </>
  ),
  belt: ({ fill, shade }) => (
    <>
      <path d="M16 112 L148 112 L148 132 L16 132 Z" fill={fill} />
      <rect x="148" y="100" width="36" height="44" rx="6" fill="none" stroke="#b8a76a" strokeWidth="6" />
      <path d="M164 122 L196 122" fill="none" stroke="#b8a76a" strokeWidth="5" />
      {[36, 60, 84].map((x) => <circle key={x} cx={x} cy="122" r="3" fill={shade} stroke="none" />)}
    </>
  ),
  jewelry: ({ fill, detail }) => (
    <>
      <path d="M44 40 Q100 160 156 40" fill="none" stroke={fill} strokeWidth="5" />
      <path d="M100 116 L118 140 L100 172 L82 140 Z" fill={fill} />
      <path d="M100 124 L110 140 L100 160 L90 140 Z" fill={detail} opacity="0.5" />
      <circle cx="100" cy="112" r="5" fill={fill} />
    </>
  ),
  cap: ({ fill, shade, detail }) => (
    <>
      <path d="M44 140 Q44 66 100 66 Q156 66 156 140 Z" fill={fill} />
      <path d="M44 140 Q100 132 156 140 Q190 146 196 162 Q150 168 44 150 Z" fill={shade} />
      <path d="M100 66 L100 138 M72 74 Q78 104 74 138 M128 74 Q122 104 126 138" fill="none" stroke={detail} />
      <circle cx="100" cy="66" r="5" fill={fill} />
    </>
  ),
  scarf: ({ fill, shade, detail }) => (
    <>
      <path d="M50 46 Q100 72 150 46 L156 70 Q100 104 44 70 Z" fill={fill} />
      <path d="M106 84 L132 84 L146 196 L114 196 Z" fill={fill} />
      <path d="M78 84 L104 84 L96 182 L66 178 Z" fill={shade} />
      {[118, 126, 134, 142].map((x) => <path key={x} d={`M${x} 196 L${x} 210`} fill="none" stroke={detail} />)}
      {[70, 78, 86, 94].map((x) => <path key={x} d={`M${x} 180 L${x - 2} 194`} fill="none" stroke={detail} />)}
    </>
  ),
  tag: ({ fill, detail }) => (
    <>
      <path d="M60 60 L130 60 L170 120 L130 180 L60 180 Z" fill={fill} />
      <circle cx="134" cy="120" r="8" fill="#faf8f5" />
      <path d="M78 104 L114 104 M78 136 L104 136" fill="none" stroke={detail} />
    </>
  ),
};
