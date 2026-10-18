"""Generates the fashion demo catalog migration (deterministic, fixed random seed).

    py -3 tools/gen_fashion_seed.py backend/src/main/resources/db/seed/V17__fashion_catalog.sql

Product data is mock demo data. Once a migration has been applied somewhere, do not regenerate it in place:
write a new migration instead (Flyway checksums applied migrations).
"""
import random, re, sys

random.seed(20261018)
OUT = sys.argv[1]

def q(s):
    return "NULL" if s is None else "'" + str(s).replace("'", "''") + "'"

def slugify(s):
    return re.sub(r"(^-|-$)", "", re.sub(r"[^a-z0-9]+", "-", s.lower()))

# ------------------------------------------------------------------ vocabularies
DEPARTMENTS = [
    ("men", "Men", "Shirts, tees, denim, outerwear and ethnic wear for men.", 1),
    ("women", "Women", "Dresses, tops, denim, skirts, outerwear and ethnic wear for women.", 2),
    ("accessories", "Accessories", "Watches, bags, sunglasses, jewelry and finishing touches.", 3),
]
# slug, dept, name, description, slot, scale, art
CATEGORIES = [
    ("men-tshirts", "men", "T-Shirts & Polos", "Crew necks, graphic tees and polos.", "TOP", "ALPHA"),
    ("men-shirts", "men", "Shirts", "Oxford, linen, denim and dress shirts.", "TOP", "ALPHA"),
    ("men-jeans", "men", "Jeans", "Slim, straight and relaxed denim.", "BOTTOM", "WAIST"),
    ("men-trousers", "men", "Trousers", "Chinos, tailored trousers and joggers.", "BOTTOM", "WAIST"),
    ("men-jackets", "men", "Jackets", "Denim, bomber, track and tailored jackets.", "LAYER", "ALPHA"),
    ("men-hoodies", "men", "Hoodies & Sweatshirts", "Pullover and zip hoodies, crew sweatshirts.", "LAYER", "ALPHA"),
    ("men-sweaters", "men", "Sweaters", "Knitwear: crew, V-neck and cable knits.", "LAYER", "ALPHA"),
    ("men-ethnic-wear", "men", "Ethnic Wear", "Kurta sets and festive wear.", "FULL", "ALPHA"),
    ("women-dresses", "women", "Dresses", "Day, midi, slip and party dresses.", "FULL", "ALPHA"),
    ("women-tops", "women", "Tops", "Blouses, tees, knit tops and shirts.", "TOP", "ALPHA"),
    ("women-jeans", "women", "Jeans", "Skinny, straight, wide-leg and mom jeans.", "BOTTOM", "WAIST"),
    ("women-trousers", "women", "Trousers", "Tailored, wide-leg and relaxed trousers.", "BOTTOM", "WAIST"),
    ("women-skirts", "women", "Skirts", "Mini, midi and pleated skirts.", "BOTTOM", "ALPHA"),
    ("women-jackets", "women", "Jackets", "Blazers, denim, trench and puffer jackets.", "LAYER", "ALPHA"),
    ("women-hoodies", "women", "Hoodies & Sweatshirts", "Cropped, oversized and zip hoodies.", "LAYER", "ALPHA"),
    ("women-ethnic-wear", "women", "Ethnic Wear", "Kurtas, kurta sets and festive wear.", "FULL", "ALPHA"),
    ("watches", "accessories", "Watches", "Analog, chronograph and minimalist watches.", "ACCESSORY", "ONE_SIZE"),
    ("sunglasses", "accessories", "Sunglasses", "Aviator, square and cat-eye sunglasses.", "ACCESSORY", "ONE_SIZE"),
    ("handbags", "accessories", "Handbags", "Totes, crossbody and shoulder bags.", "ACCESSORY", "ONE_SIZE"),
    ("backpacks", "accessories", "Backpacks", "Everyday, laptop and sport backpacks.", "ACCESSORY", "ONE_SIZE"),
    ("wallets", "accessories", "Wallets", "Bifold, card holders and zip wallets.", "ACCESSORY", "ONE_SIZE"),
    ("belts", "accessories", "Belts", "Leather and canvas belts.", "ACCESSORY", "ONE_SIZE"),
    ("jewelry", "accessories", "Jewelry", "Necklaces, bracelets, earrings and rings.", "ACCESSORY", "ONE_SIZE"),
    ("caps", "accessories", "Caps & Hats", "Baseball caps and beanies.", "ACCESSORY", "ONE_SIZE"),
    ("scarves", "accessories", "Scarves", "Wool, cotton and silk scarves.", "ACCESSORY", "ONE_SIZE"),
]
CAT = {c[0]: c for c in CATEGORIES}
APPAREL = [c[0] for c in CATEGORIES if c[1] != "accessories"]

STYLES = [
    ("casual", "Casual", "Relaxed everyday pieces that are easy to wear."),
    ("streetwear", "Streetwear", "Oversized fits, graphics and sporty layers."),
    ("formal", "Formal", "Tailored, polished pieces for work and events."),
    ("minimalist", "Minimalist", "Clean lines and a neutral palette."),
    ("party-wear", "Party Wear", "Statement pieces for evenings out."),
]
OCCASIONS = [
    ("everyday", "Everyday", "Errands, coffee and everything in between."),
    ("weekend", "Weekend", "Brunch, day trips and relaxed plans."),
    ("office", "Office", "Smart pieces for the working week."),
    ("party", "Party", "Nights out and celebrations."),
    ("date-night", "Date Night", "Dressed up, but still you."),
    ("festive", "Festive & Wedding", "Weddings, festivals and family celebrations."),
    ("travel", "Travel", "Comfortable layers that pack well."),
    ("workout", "Workout", "Training, running and active days."),
]

# color key -> (display name, hex, family)
COLORS = {
    "black": ("Black", "#1F1F1F", "NEUTRAL"), "white": ("White", "#F7F7F5", "NEUTRAL"),
    "navy": ("Navy", "#1F2A44", "NEUTRAL"), "grey": ("Heather Grey", "#9A9DA1", "NEUTRAL"),
    "charcoal": ("Charcoal", "#3C3F44", "NEUTRAL"), "beige": ("Beige", "#D8C3A5", "NEUTRAL"),
    "cream": ("Cream", "#F3EAD7", "NEUTRAL"), "camel": ("Camel", "#C19A6B", "NEUTRAL"),
    "brown": ("Brown", "#6B4423", "NEUTRAL"), "olive": ("Olive", "#5B6B3A", "NEUTRAL"),
    "khaki": ("Khaki", "#B9A77C", "NEUTRAL"), "tan": ("Tan", "#B5835A", "NEUTRAL"),
    "dark-wash": ("Dark Wash", "#26324F", "DENIM"), "mid-wash": ("Mid Wash", "#4F6D9A", "DENIM"),
    "light-wash": ("Light Wash", "#8FAACB", "DENIM"), "black-denim": ("Washed Black", "#2B2B2E", "DENIM"),
    "red": ("Red", "#C8323C", "ACCENT"), "burgundy": ("Burgundy", "#6D1A2A", "ACCENT"),
    "coral": ("Coral", "#F06A5C", "ACCENT"), "pink": ("Blush Pink", "#E8A9B8", "ACCENT"),
    "lavender": ("Lavender", "#B9A6E0", "ACCENT"), "emerald": ("Emerald", "#1E7B5E", "ACCENT"),
    "sky": ("Sky Blue", "#8DB8E0", "ACCENT"), "mustard": ("Mustard", "#D4A017", "ACCENT"),
    "sage": ("Sage", "#9CAF88", "ACCENT"), "royal": ("Royal Blue", "#2C4FB8", "ACCENT"),
    "maroon": ("Maroon", "#7A1F2B", "ACCENT"), "teal": ("Teal", "#1F6F78", "ACCENT"),
    "gold": ("Gold", "#C9A227", "METAL"), "silver": ("Silver", "#BFC3C7", "METAL"),
    "rose-gold": ("Rose Gold", "#B76E79", "METAL"), "tortoise": ("Tortoise", "#7B4B2A", "NEUTRAL"),
}

SIZES = {
    ("ALPHA", "MEN"): ["S", "M", "L", "XL", "XXL"],
    ("ALPHA", "WOMEN"): ["XS", "S", "M", "L", "XL"],
    ("ALPHA", "UNISEX"): ["XS", "S", "M", "L", "XL"],
    ("WAIST", "MEN"): ["28", "30", "32", "34", "36", "38"],
    ("WAIST", "WOMEN"): ["24", "26", "28", "30", "32"],
    ("ONE_SIZE", None): ["One size"],
}

CARE = {
    "cotton": "Machine wash cold with like colors. Tumble dry low. Do not bleach.",
    "denim": "Machine wash cold inside out. Line dry to keep the color. Do not bleach.",
    "wool": "Hand wash cold or dry clean. Dry flat. Do not tumble dry.",
    "synthetic": "Machine wash cold. Do not iron prints. Line dry.",
    "linen": "Machine wash cold on a gentle cycle. Iron while slightly damp.",
    "silk": "Dry clean only.",
    "viscose": "Hand wash cold. Do not wring. Cool iron.",
    "leather": "Wipe clean with a dry cloth. Keep away from direct heat. Condition occasionally.",
    "metal": "Wipe with a soft cloth. Avoid water, perfume and chemicals.",
    "canvas": "Spot clean with a damp cloth. Air dry.",
}

BRANDS = [
    ("Nike", "Athletic and lifestyle apparel built around movement: training tees, fleece and sporty layers.", 2, True),
    ("Adidas", "Sportswear classics and everyday athletic apparel with signature three-stripe details.", 2, True),
    ("Puma", "Sport-inspired apparel and accessories for training and the street.", 1, False),
    ("Zara", "Trend-led womenswear and menswear with tailored and minimalist pieces.", 2, True),
    ("H&M", "Affordable everyday fashion and wardrobe basics for women and men.", 1, False),
    ("Levi's", "Denim specialists: jeans, trucker jackets and casual essentials.", 2, True),
    ("Tommy Hilfiger", "Preppy American classics: polos, shirts, knitwear and accessories.", 3, True),
    ("Calvin Klein", "Clean, minimalist essentials, denim and modern accessories.", 3, True),
    ("Ralph Lauren", "Timeless tailoring, oxford shirts, cable knits and leather goods.", 3, True),
    ("Lacoste", "Sport-chic polos, knitwear and dresses with a relaxed elegance.", 3, False),
    ("Uniqlo", "Functional, high-quality basics designed for everyday comfort.", 1, False),
    ("Guess", "Glamorous denim, statement dresses, watches and accessories.", 2, False),
    ("Mango", "Contemporary womenswear with refined tailoring and Mediterranean style.", 2, False),
    ("Under Armour", "Performance training apparel and accessories for active days.", 2, False),
    ("Michael Kors", "Jet-set luxury: watches, handbags, leather goods and polished dresses.", 3, True),
]
BRAND_CODE = {"Nike": "NIK", "Adidas": "ADI", "Puma": "PUM", "Zara": "ZAR", "H&M": "HNM", "Levi's": "LEV",
              "Tommy Hilfiger": "TMY", "Calvin Klein": "CKL", "Ralph Lauren": "RLR", "Lacoste": "LAC",
              "Uniqlo": "UNI", "Guess": "GSS", "Mango": "MNG", "Under Armour": "UAR", "Michael Kors": "MKR"}

# brand, category, name, price, sale%, gender, fit, pattern, formality(1-5), styles, occasions, colors, flags(n/t/f), fabric, material text, extra (attr overrides)
P = []
def p(brand, cat, name, price, sale, gender, fit, pattern, formality, styles, occasions, colors, flags, fabric, material, blurb, **extra):
    P.append(dict(brand=brand, cat=cat, name=name, price=price, sale=sale, gender=gender, fit=fit, pattern=pattern,
                  formality=formality, styles=styles.split(), occasions=occasions.split(), colors=colors.split(),
                  flags=flags, fabric=fabric, material=material, blurb=blurb, extra=extra))

# --- Nike
p("Nike", "men-tshirts", "Nike Dri-Tech Training Tee", 35, 0, "MEN", "Regular", "Solid", 1, "casual streetwear", "workout weekend everyday", "black white grey royal", "t", "synthetic", "100% recycled polyester", "A lightweight training tee in quick-drying knit that moves with you from warm-up to cool-down.")
p("Nike", "men-hoodies", "Nike Club Fleece Pullover Hoodie", 65, 20, "MEN", "Relaxed", "Solid", 1, "streetwear casual", "weekend everyday travel", "black grey navy olive", "tf", "cotton", "80% cotton, 20% polyester brushed fleece", "A brushed-back fleece hoodie with a roomy hood and kangaroo pocket: the everyday layer.")
p("Nike", "men-jackets", "Nike Windrunner Hooded Jacket", 110, 0, "MEN", "Regular", "Colorblock", 2, "streetwear casual", "weekend travel workout", "black navy royal", "n", "synthetic", "100% polyester woven shell, mesh lining", "A light, wind-resistant shell with the classic chevron colorblock and a packable hood.")
p("Nike", "women-tops", "Nike One Fitted Crop Top", 40, 0, "WOMEN", "Slim", "Solid", 1, "streetwear casual", "workout weekend", "black white pink lavender", "tn", "synthetic", "88% polyester, 12% spandex", "A fitted, stretchy crop top that holds its shape through every workout.")
p("Nike", "women-hoodies", "Nike Phoenix Oversized Fleece Hoodie", 75, 15, "WOMEN", "Oversized", "Solid", 1, "streetwear casual", "weekend everyday travel", "grey sage black cream", "t", "cotton", "80% cotton, 20% polyester heavyweight fleece", "An oversized, heavyweight fleece hoodie with dropped shoulders and a cozy hood.")
p("Nike", "caps", "Nike Heritage86 Cap", 28, 0, "UNISEX", None, "Solid", 1, "streetwear casual", "weekend workout travel", "black white navy", "", "cotton", "100% cotton twill, adjustable strap", "A six-panel cotton cap with a curved bill and adjustable back strap.")
p("Nike", "backpacks", "Nike Brasilia Training Backpack", 45, 0, "UNISEX", None, "Solid", 1, "streetwear casual", "workout travel everyday", "black navy grey", "", "canvas", "100% polyester, padded straps", "A roomy training backpack with a ventilated shoe pocket and padded straps.")
p("Nike", "women-trousers", "Nike Tech Fleece Joggers", 110, 0, "WOMEN", "Slim", "Solid", 1, "streetwear casual", "weekend travel workout", "black grey olive", "n", "cotton", "66% cotton, 34% polyester tech fleece", "Tapered tech-fleece joggers with zip pockets and ribbed cuffs.")
# --- Adidas
p("Adidas", "men-tshirts", "Adidas Essentials 3-Stripes Tee", 30, 0, "MEN", "Regular", "Striped", 1, "casual streetwear", "weekend everyday workout", "white black navy", "", "cotton", "100% cotton single jersey", "A soft cotton tee with the signature three stripes down the shoulders.")
p("Adidas", "men-jackets", "Adidas Adicolor Classics Track Jacket", 80, 25, "MEN", "Regular", "Striped", 2, "streetwear casual", "weekend travel", "black navy red", "t", "synthetic", "100% recycled polyester tricot", "The archive track top with a full zip, stand-up collar and contrast stripes.")
p("Adidas", "men-hoodies", "Adidas Trefoil Essentials Hoodie", 70, 0, "MEN", "Relaxed", "Graphic", 1, "streetwear casual", "weekend everyday", "black grey burgundy", "n", "cotton", "70% cotton, 30% recycled polyester fleece", "A cotton-blend fleece hoodie with an embroidered Trefoil on the chest.")
p("Adidas", "men-trousers", "Adidas Tiro Track Pants", 50, 0, "MEN", "Slim", "Striped", 1, "streetwear casual", "workout weekend travel", "black navy", "t", "synthetic", "100% recycled polyester", "Slim, ankle-zip track pants with side stripes, from the pitch to the street.")
p("Adidas", "women-jackets", "Adidas Firebird Track Top", 85, 0, "WOMEN", "Regular", "Striped", 2, "streetwear casual", "weekend travel", "black pink sky", "n", "synthetic", "100% recycled polyester tricot", "A retro track top with shiny tricot, contrast stripes and a stand collar.")
p("Adidas", "women-tops", "Adidas Adicolor Classics Tank", 30, 0, "WOMEN", "Slim", "Striped", 1, "casual streetwear", "weekend workout", "white black lavender", "", "cotton", "95% cotton, 5% elastane", "A fitted rib tank with three-stripe trim and a racer back.")
p("Adidas", "caps", "Adidas Trefoil Baseball Cap", 25, 0, "UNISEX", None, "Solid", 1, "streetwear casual", "weekend workout", "black white burgundy", "", "cotton", "100% cotton twill", "A classic six-panel cap with an embroidered Trefoil logo.")
p("Adidas", "backpacks", "Adidas Classic Badge of Sport Backpack", 40, 15, "UNISEX", None, "Solid", 1, "casual streetwear", "everyday travel", "black navy olive", "", "canvas", "100% recycled polyester", "An everyday backpack with a laptop sleeve and front zip pocket.")
# --- Puma
p("Puma", "men-tshirts", "Puma Essentials Logo Tee", 25, 0, "MEN", "Regular", "Graphic", 1, "casual streetwear", "weekend everyday workout", "black white navy red", "", "cotton", "100% cotton", "A crew-neck cotton tee with a printed chest logo, built for everyday wear.")
p("Puma", "men-hoodies", "Puma Classics Relaxed Hoodie", 60, 20, "MEN", "Relaxed", "Solid", 1, "streetwear casual", "weekend everyday", "grey black olive", "t", "cotton", "66% cotton, 34% polyester French terry", "A relaxed French-terry hoodie with a lined hood and ribbed hem.")
p("Puma", "women-tops", "Puma Her Ribbed Crop Tee", 30, 0, "WOMEN", "Slim", "Solid", 1, "streetwear casual", "weekend workout", "white black pink", "n", "cotton", "95% cotton, 5% elastane", "A cropped, ribbed tee with a fitted shape and small chest logo.")
p("Puma", "women-hoodies", "Puma Downtown Oversized Hoodie", 65, 0, "WOMEN", "Oversized", "Graphic", 1, "streetwear", "weekend everyday", "cream black lavender", "n", "cotton", "68% cotton, 32% polyester", "An oversized hoodie with a back graphic and dropped shoulders.")
p("Puma", "caps", "Puma Archive Logo Cap", 22, 0, "UNISEX", None, "Solid", 1, "streetwear casual", "weekend workout", "black navy", "", "cotton", "100% cotton", "A low-profile cap with a stitched archive logo.")
p("Puma", "backpacks", "Puma Phase Backpack", 30, 0, "UNISEX", None, "Solid", 1, "casual", "everyday workout", "black royal", "", "canvas", "100% polyester", "A compact everyday backpack with a front zip pocket.")
p("Puma", "men-trousers", "Puma Essentials Sweatpants", 45, 0, "MEN", "Regular", "Solid", 1, "casual streetwear", "weekend workout travel", "black grey", "", "cotton", "68% cotton, 32% polyester fleece", "Soft fleece sweatpants with an elastic waist and side pockets.")
# --- Zara
p("Zara", "men-shirts", "Zara Textured Relaxed Shirt", 50, 0, "MEN", "Relaxed", "Solid", 3, "casual minimalist", "weekend date-night office", "white beige olive", "n", "cotton", "100% cotton textured weave", "A relaxed-fit shirt in a subtle textured cotton with a camp collar.")
p("Zara", "men-trousers", "Zara Pleated Tailored Trousers", 60, 0, "MEN", "Relaxed", "Solid", 4, "formal minimalist", "office date-night", "black charcoal beige", "t", "synthetic", "68% polyester, 30% viscose, 2% elastane", "Pleated, relaxed-leg trousers with a clean drape for smart looks.")
p("Zara", "men-jackets", "Zara Structured Blazer", 120, 20, "MEN", "Slim", "Solid", 5, "formal minimalist", "office party date-night festive", "black navy beige", "f", "synthetic", "62% polyester, 33% viscose, 5% elastane", "A structured single-breasted blazer with notch lapels and a sharp shoulder.")
p("Zara", "women-dresses", "Zara Satin Slip Midi Dress", 70, 0, "WOMEN", "Slim", "Solid", 4, "party-wear minimalist", "party date-night festive", "black emerald burgundy cream", "tnf", "viscose", "100% viscose satin", "A bias-cut satin midi dress with adjustable straps and a fluid drape.")
p("Zara", "women-tops", "Zara Poplin Corset Top", 40, 0, "WOMEN", "Slim", "Solid", 3, "party-wear casual", "party date-night weekend", "white black pink", "t", "cotton", "97% cotton, 3% elastane poplin", "A structured poplin top with corset seaming and thin straps.")
p("Zara", "women-skirts", "Zara High-Waist Midi Skirt", 50, 25, "WOMEN", "Regular", "Solid", 3, "minimalist formal", "office date-night", "black beige olive", "", "viscose", "100% viscose", "A flowing high-waist midi skirt with a hidden side zip.")
p("Zara", "women-trousers", "Zara Wide-Leg Tailored Trousers", 60, 0, "WOMEN", "Relaxed", "Solid", 4, "formal minimalist", "office date-night", "black beige navy", "t", "synthetic", "65% polyester, 33% viscose, 2% elastane", "High-rise wide-leg trousers with front pleats and a long, clean line.")
p("Zara", "handbags", "Zara Mini Shoulder Bag", 46, 0, "WOMEN", None, "Solid", 3, "minimalist party-wear", "party date-night office", "black beige", "n", "leather", "Polyurethane faux leather, magnetic closure", "A compact shoulder bag with a curved shape and short strap.")
p("Zara", "men-ethnic-wear", "Zara Linen Blend Kurta Set", 90, 0, "MEN", "Regular", "Solid", 4, "formal minimalist", "festive", "cream sage navy", "n", "linen", "55% linen, 45% cotton", "A linen-blend kurta with matching straight trousers, cut for festive days.")
# --- H&M
p("H&M", "men-tshirts", "H&M Regular Fit Cotton Tee", 13, 0, "MEN", "Regular", "Solid", 1, "casual minimalist", "everyday weekend travel", "white black navy grey olive", "", "cotton", "100% cotton jersey", "A wardrobe-staple crew-neck tee in soft cotton jersey.")
p("H&M", "men-jeans", "H&M Slim Jeans", 30, 0, "MEN", "Slim", "Solid", 2, "casual", "everyday weekend", "dark-wash mid-wash black-denim", "", "denim", "99% cotton, 1% elastane", "Five-pocket slim jeans in stretch denim with a regular waist.")
p("H&M", "men-sweaters", "H&M Fine-Knit Merino Sweater", 35, 20, "MEN", "Regular", "Solid", 3, "minimalist casual", "office weekend travel", "navy charcoal camel", "", "wool", "100% merino wool", "A lightweight merino crew-neck that layers neatly over shirts.")
p("H&M", "women-dresses", "H&M Floral Wrap Dress", 35, 30, "WOMEN", "Regular", "Printed", 2, "casual party-wear", "weekend date-night", "coral navy sage", "tn", "viscose", "100% viscose", "A floaty wrap dress in a printed floral with flutter sleeves and a tie waist.")
p("H&M", "women-tops", "H&M Ribbed Knit Top", 18, 0, "WOMEN", "Slim", "Solid", 2, "casual minimalist", "everyday office weekend", "black cream lavender", "", "cotton", "95% cotton, 5% elastane", "A fitted rib-knit top with a round neck and long sleeves.")
p("H&M", "women-skirts", "H&M Pleated Mini Skirt", 25, 0, "WOMEN", "Regular", "Checked", 2, "casual party-wear", "party weekend", "navy black", "n", "synthetic", "65% polyester, 35% viscose", "A pleated mini skirt in a classic check with a concealed zip.")
p("H&M", "scarves", "H&M Wool-Blend Scarf", 20, 0, "UNISEX", None, "Checked", 2, "casual minimalist", "travel everyday office", "camel grey burgundy", "", "wool", "50% wool, 50% polyester", "A soft, generous wool-blend scarf with fringed ends.")
p("H&M", "women-ethnic-wear", "H&M Embroidered Cotton Kurta", 40, 0, "WOMEN", "Regular", "Embroidered", 4, "formal casual", "festive", "cream maroon teal", "n", "cotton", "100% cotton", "A straight-cut cotton kurta with tonal embroidery at the neckline.")
# --- Levi's
p("Levi's", "men-jeans", "Levi's 511 Slim Jeans", 70, 0, "MEN", "Slim", "Solid", 2, "casual", "everyday weekend date-night", "dark-wash mid-wash black-denim", "tf", "denim", "99% cotton, 1% elastane", "Slim through the hip and thigh with a narrow leg; the go-to everyday jean.")
p("Levi's", "men-jeans", "Levi's 501 Original Straight Jeans", 80, 15, "MEN", "Regular", "Solid", 2, "casual streetwear", "everyday weekend", "mid-wash light-wash dark-wash", "t", "denim", "100% cotton rigid denim", "The original button-fly straight jean in rigid denim that breaks in with you.")
p("Levi's", "men-jackets", "Levi's Trucker Denim Jacket", 98, 0, "MEN", "Regular", "Solid", 2, "casual streetwear", "weekend travel everyday", "mid-wash dark-wash black-denim", "f", "denim", "100% cotton denim", "The iconic trucker jacket with point collar, chest pockets and button front.")
p("Levi's", "women-jeans", "Levi's Ribcage Straight Ankle Jeans", 98, 0, "WOMEN", "Regular", "Solid", 2, "casual streetwear", "everyday weekend", "light-wash mid-wash black-denim", "tn", "denim", "99% cotton, 1% elastane", "Ultra-high rise straight jeans that hit at the ankle.")
p("Levi's", "women-jackets", "Levi's Original Trucker Jacket", 98, 20, "WOMEN", "Regular", "Solid", 2, "casual", "weekend everyday travel", "mid-wash light-wash", "", "denim", "100% cotton denim", "A classic women's trucker jacket in authentic denim.")
p("Levi's", "men-tshirts", "Levi's Housemark Graphic Tee", 30, 0, "MEN", "Regular", "Graphic", 1, "casual streetwear", "weekend everyday", "white red grey", "", "cotton", "100% cotton", "A soft cotton tee with the batwing housemark printed on the chest.")
p("Levi's", "belts", "Levi's Classic Leather Belt", 35, 0, "MEN", None, "Solid", 2, "casual", "everyday office weekend", "brown black", "", "leather", "100% leather, metal buckle", "A full-grain leather belt with a classic metal buckle.")
p("Levi's", "women-jeans", "Levi's 726 High Rise Flare Jeans", 90, 0, "WOMEN", "Slim", "Solid", 2, "casual party-wear", "weekend date-night", "dark-wash black-denim", "n", "denim", "92% cotton, 6% polyester, 2% elastane", "A high-rise flare that is fitted through the thigh and kicks out at the hem.")
# --- Tommy Hilfiger
p("Tommy Hilfiger", "men-shirts", "Tommy Hilfiger Oxford Slim Shirt", 90, 0, "MEN", "Slim", "Solid", 4, "formal casual", "office date-night", "white sky navy", "f", "cotton", "100% cotton oxford", "A button-down oxford shirt with a slim fit and embroidered flag at the hem.")
p("Tommy Hilfiger", "men-tshirts", "Tommy Hilfiger Classic Polo", 70, 20, "MEN", "Regular", "Solid", 2, "casual formal", "weekend office everyday", "navy white red", "t", "cotton", "100% cotton pique", "A cotton pique polo with a two-button placket and flag logo.")
p("Tommy Hilfiger", "men-sweaters", "Tommy Hilfiger Cable Knit Sweater", 120, 0, "MEN", "Regular", "Textured", 3, "casual formal", "office weekend travel", "cream navy camel", "n", "wool", "70% cotton, 30% wool", "A chunky cable-knit crew-neck in a soft cotton-wool blend.")
p("Tommy Hilfiger", "women-tops", "Tommy Hilfiger Striped Breton Top", 60, 0, "WOMEN", "Regular", "Striped", 2, "casual minimalist", "weekend travel", "navy red", "", "cotton", "100% cotton", "A classic Breton stripe top with three-quarter sleeves.")
p("Tommy Hilfiger", "watches", "Tommy Hilfiger Leather Strap Watch", 155, 0, "MEN", None, "Solid", 4, "formal minimalist", "office date-night festive", "brown black", "f", "leather", "Stainless steel case, genuine leather strap", "A 44 mm stainless steel watch with a sunray dial and leather strap.", strap="Leather")
p("Tommy Hilfiger", "wallets", "Tommy Hilfiger Bifold Leather Wallet", 55, 0, "MEN", None, "Solid", 3, "formal casual", "office everyday", "black brown", "", "leather", "100% leather", "A slim bifold wallet with card slots and a bill compartment.")
p("Tommy Hilfiger", "belts", "Tommy Hilfiger Reversible Belt", 50, 15, "MEN", None, "Solid", 3, "formal casual", "office everyday", "black brown", "", "leather", "Leather, rotating buckle", "A reversible leather belt with a rotating buckle: black on one side, brown on the other.")
p("Tommy Hilfiger", "sunglasses", "Tommy Hilfiger Aviator Sunglasses", 125, 0, "UNISEX", None, "Solid", 3, "casual minimalist", "weekend travel", "gold silver", "", "metal", "Metal frame, UV400 lenses", "Metal aviator frames with gradient UV400 lenses.")
# --- Calvin Klein
p("Calvin Klein", "men-tshirts", "Calvin Klein Monogram Logo Tee", 45, 0, "MEN", "Regular", "Graphic", 1, "minimalist casual", "everyday weekend", "black white grey", "t", "cotton", "100% organic cotton", "A clean organic-cotton tee with a small monogram logo.")
p("Calvin Klein", "men-jeans", "Calvin Klein Slim Tapered Jeans", 90, 25, "MEN", "Slim", "Solid", 2, "minimalist casual", "everyday date-night", "black-denim dark-wash", "", "denim", "98% cotton, 2% elastane", "Slim, tapered jeans in a clean wash with minimal hardware.")
p("Calvin Klein", "women-dresses", "Calvin Klein Sheath Dress", 130, 0, "WOMEN", "Slim", "Solid", 4, "formal minimalist", "office date-night", "black navy burgundy", "f", "synthetic", "95% polyester, 5% elastane scuba crepe", "A sleek sheath dress in stretch scuba crepe with a back zip.")
p("Calvin Klein", "women-tops", "Calvin Klein Satin Camisole", 60, 0, "WOMEN", "Regular", "Solid", 3, "minimalist party-wear", "date-night party office", "cream black emerald", "n", "silk", "100% polyester satin", "A satin camisole with a V-neck and adjustable straps.")
p("Calvin Klein", "watches", "Calvin Klein Minimal Mesh Watch", 180, 0, "UNISEX", None, "Solid", 4, "minimalist formal", "office date-night party", "silver black rose-gold", "tf", "metal", "Stainless steel case and mesh bracelet", "A slim 38 mm watch with a clean dial and stainless mesh bracelet.", strap="Metal")
p("Calvin Klein", "wallets", "Calvin Klein Leather Card Holder", 40, 0, "UNISEX", None, "Solid", 3, "minimalist", "everyday office", "black", "", "leather", "100% leather", "A slim leather card holder with four card slots.")
p("Calvin Klein", "handbags", "Calvin Klein Structured Tote", 170, 20, "WOMEN", None, "Solid", 3, "minimalist formal", "office travel", "black camel", "", "leather", "Faux leather, cotton lining", "A structured tote with a zip-top closure and laptop sleeve.")
p("Calvin Klein", "sunglasses", "Calvin Klein Square Sunglasses", 140, 0, "UNISEX", None, "Solid", 3, "minimalist", "weekend travel", "black tortoise", "n", "synthetic", "Acetate frame, UV400 lenses", "Bold square acetate frames with dark UV400 lenses.")
# --- Ralph Lauren
p("Ralph Lauren", "men-shirts", "Ralph Lauren Custom Fit Oxford Shirt", 110, 0, "MEN", "Regular", "Solid", 4, "formal casual", "office date-night weekend", "white sky pink", "f", "cotton", "100% cotton oxford", "An oxford shirt with a button-down collar and signature embroidered pony.")
p("Ralph Lauren", "men-sweaters", "Ralph Lauren Cable-Knit Cotton Sweater", 150, 15, "MEN", "Regular", "Textured", 3, "formal casual", "office weekend travel", "cream navy olive", "t", "cotton", "100% cotton cable knit", "A cable-knit cotton sweater, a preppy layering classic.")
p("Ralph Lauren", "men-trousers", "Ralph Lauren Stretch Slim Chinos", 98, 0, "MEN", "Slim", "Solid", 3, "formal casual", "office weekend", "khaki navy olive", "", "cotton", "98% cotton, 2% elastane twill", "Slim stretch-twill chinos with a flat front and clean finish.")
p("Ralph Lauren", "women-dresses", "Ralph Lauren Belted Shirtdress", 160, 0, "WOMEN", "Regular", "Striped", 3, "formal casual", "office weekend", "sky navy", "n", "cotton", "100% cotton poplin", "A crisp poplin shirtdress with a self-tie belt and roll-tab sleeves.")
p("Ralph Lauren", "belts", "Ralph Lauren Leather Dress Belt", 75, 0, "MEN", None, "Solid", 4, "formal", "office festive", "black brown", "", "leather", "100% leather", "A polished leather dress belt with a slim brass-tone buckle.")
p("Ralph Lauren", "wallets", "Ralph Lauren Pebbled Leather Wallet", 85, 0, "UNISEX", None, "Solid", 3, "formal", "everyday office", "brown black", "", "leather", "100% pebbled leather", "A pebbled-leather billfold with an embossed pony.")
p("Ralph Lauren", "scarves", "Ralph Lauren Cashmere-Blend Scarf", 120, 25, "UNISEX", None, "Solid", 3, "formal minimalist", "travel office", "camel navy grey", "", "wool", "70% wool, 30% cashmere", "A soft cashmere-blend scarf with fringed ends.")
p("Ralph Lauren", "sunglasses", "Ralph Lauren Pilot Sunglasses", 180, 0, "UNISEX", None, "Solid", 3, "formal casual", "travel weekend", "gold silver", "f", "metal", "Metal frame, polarized lenses", "Lightweight metal pilot frames with polarized lenses.")
# --- Lacoste
p("Lacoste", "men-tshirts", "Lacoste Classic Fit Pique Polo", 98, 0, "MEN", "Regular", "Solid", 2, "casual formal", "weekend office everyday", "white navy emerald red", "tf", "cotton", "100% cotton petit pique", "The original petit-pique polo with ribbed collar and crocodile emblem.")
p("Lacoste", "men-sweaters", "Lacoste Wool Crew Neck Sweater", 140, 20, "MEN", "Regular", "Solid", 3, "minimalist formal", "office weekend", "navy grey burgundy", "", "wool", "100% wool", "A fine wool crew-neck sweater with ribbed trims.")
p("Lacoste", "women-dresses", "Lacoste Polo Dress", 140, 0, "WOMEN", "Regular", "Solid", 2, "casual minimalist", "weekend travel", "navy white pink", "n", "cotton", "94% cotton, 6% elastane pique", "A stretch-pique polo dress with a three-button placket.")
p("Lacoste", "women-tops", "Lacoste Slim Fit Polo Shirt", 90, 0, "WOMEN", "Slim", "Solid", 2, "casual", "weekend office", "white navy pink", "", "cotton", "95% cotton, 5% elastane", "A slim, stretch-pique polo with a fine ribbed collar.")
p("Lacoste", "caps", "Lacoste Sport Cap", 50, 0, "UNISEX", None, "Solid", 1, "casual", "weekend workout travel", "navy white", "", "synthetic", "100% polyester", "A lightweight sport cap with an adjustable strap.")
p("Lacoste", "men-jackets", "Lacoste Water-Repellent Hooded Jacket", 220, 15, "MEN", "Regular", "Solid", 3, "casual minimalist", "travel weekend", "navy black", "n", "synthetic", "100% polyamide, water-repellent finish", "A water-repellent hooded jacket with a packable hood.")
# --- Uniqlo
p("Uniqlo", "men-tshirts", "Uniqlo Supima Cotton Crew Tee", 15, 0, "MEN", "Regular", "Solid", 1, "minimalist casual", "everyday weekend travel", "white black navy grey", "", "cotton", "100% Supima cotton", "A smooth, durable crew-neck tee in long-staple cotton.")
p("Uniqlo", "men-trousers", "Uniqlo Smart Ankle Pants", 40, 0, "MEN", "Slim", "Solid", 3, "minimalist formal", "office travel", "black navy beige charcoal", "t", "synthetic", "70% polyester, 25% rayon, 5% elastane", "Comfortable stretch ankle pants with a smart, tailored look.")
p("Uniqlo", "men-sweaters", "Uniqlo Extra Fine Merino Crew", 35, 0, "MEN", "Regular", "Solid", 3, "minimalist", "office travel everyday", "navy grey olive black", "", "wool", "100% extra fine merino wool", "A fine, washable merino crew-neck that layers easily.")
p("Uniqlo", "women-tops", "Uniqlo Linen Blend Shirt", 30, 0, "WOMEN", "Relaxed", "Solid", 2, "minimalist casual", "weekend travel office", "white beige sky", "n", "linen", "55% linen, 45% cotton", "A breezy linen-blend shirt with a relaxed fit.")
p("Uniqlo", "women-trousers", "Uniqlo Wide Pleated Pants", 40, 20, "WOMEN", "Relaxed", "Solid", 3, "minimalist formal", "office travel", "black beige navy", "", "synthetic", "86% polyester, 14% rayon", "Wide, pleated pants with a flowing drape and comfortable waist.")
p("Uniqlo", "scarves", "Uniqlo Heattech Scarf", 15, 0, "UNISEX", None, "Solid", 1, "minimalist casual", "travel everyday", "grey navy burgundy", "", "synthetic", "Acrylic, polyester and rayon blend", "A warm, soft knit scarf in a heat-retaining yarn.")
p("Uniqlo", "women-hoodies", "Uniqlo Sweat Full-Zip Hoodie", 40, 0, "WOMEN", "Regular", "Solid", 1, "casual minimalist", "weekend travel", "grey sage black", "", "cotton", "70% cotton, 30% polyester", "A soft full-zip hoodie with a lined hood.")
p("Uniqlo", "backpacks", "Uniqlo Utility Backpack", 30, 0, "UNISEX", None, "Solid", 1, "minimalist casual", "travel everyday", "black olive", "", "canvas", "100% nylon", "A lightweight utility backpack with multiple organiser pockets.")
# --- Guess
p("Guess", "men-jeans", "Guess Slim Tapered Jeans", 99, 30, "MEN", "Slim", "Solid", 2, "casual party-wear", "weekend party date-night", "dark-wash black-denim", "", "denim", "98% cotton, 2% elastane", "Slim tapered jeans with a clean wash and signature back-pocket stitching.")
p("Guess", "women-dresses", "Guess Sequin Mini Dress", 150, 0, "WOMEN", "Slim", "Embellished", 4, "party-wear", "party festive date-night", "black gold silver", "tn", "synthetic", "100% polyester, sequin embellishment", "A sequin mini dress with a square neck and thin straps.")
p("Guess", "women-tops", "Guess Logo Bodysuit", 45, 0, "WOMEN", "Slim", "Graphic", 2, "party-wear streetwear", "party weekend", "black white red", "", "cotton", "95% cotton, 5% elastane", "A fitted long-sleeve bodysuit with a logo print.")
p("Guess", "handbags", "Guess Quilted Crossbody Bag", 98, 15, "WOMEN", None, "Quilted", 3, "party-wear", "party date-night weekend", "black pink beige", "t", "leather", "Faux leather, gold-tone chain strap", "A quilted crossbody bag with a chain strap and turn-lock closure.")
p("Guess", "watches", "Guess Glitz Crystal Watch", 165, 0, "WOMEN", None, "Embellished", 4, "party-wear formal", "party festive date-night", "gold rose-gold silver", "n", "metal", "Stainless steel, crystal accents", "A crystal-set watch with a sunray dial and link bracelet.", strap="Metal")
p("Guess", "sunglasses", "Guess Cat-Eye Sunglasses", 98, 0, "WOMEN", None, "Solid", 3, "party-wear", "weekend party travel", "black tortoise", "", "synthetic", "Acetate frame, UV400 lenses", "Statement cat-eye frames with gradient lenses.")
p("Guess", "jewelry", "Guess Logo Charm Bracelet", 55, 0, "WOMEN", None, "Solid", 3, "party-wear", "party date-night", "gold silver rose-gold", "", "metal", "Stainless steel with plating", "A charm bracelet with logo charms and a lobster clasp.")
# --- Mango
p("Mango", "women-dresses", "Mango Linen Midi Dress", 80, 0, "WOMEN", "Regular", "Solid", 3, "minimalist casual", "weekend travel date-night", "cream sage camel", "tf", "linen", "100% linen", "A sleeveless linen midi dress with a square neck and buttoned front.")
p("Mango", "women-tops", "Mango Satin Blouse", 50, 0, "WOMEN", "Regular", "Solid", 4, "formal party-wear", "office date-night party", "cream black emerald", "n", "viscose", "100% viscose satin", "A fluid satin blouse with a concealed placket and cuffed sleeves.")
p("Mango", "women-skirts", "Mango Pleated Midi Skirt", 60, 20, "WOMEN", "Regular", "Solid", 4, "formal party-wear", "office party festive", "camel black burgundy", "", "synthetic", "100% polyester", "A sunray-pleated midi skirt with an elastic waist.")
p("Mango", "women-jackets", "Mango Structured Blazer", 130, 0, "WOMEN", "Regular", "Solid", 5, "formal minimalist", "office date-night", "black camel cream", "tf", "synthetic", "63% polyester, 33% viscose, 4% elastane", "A double-breasted blazer with structured shoulders and flap pockets.")
p("Mango", "women-trousers", "Mango Straight Suit Trousers", 70, 0, "WOMEN", "Regular", "Solid", 4, "formal minimalist", "office", "black camel navy", "", "synthetic", "63% polyester, 33% viscose, 4% elastane", "Straight-leg suit trousers with pressed creases.")
p("Mango", "handbags", "Mango Leather-Effect Shoulder Bag", 50, 0, "WOMEN", None, "Solid", 3, "minimalist formal", "office everyday", "black brown", "", "leather", "Polyurethane, fabric lining", "A slouchy shoulder bag with a zip closure.")
p("Mango", "jewelry", "Mango Gold Hoop Earrings", 25, 0, "WOMEN", None, "Solid", 3, "minimalist party-wear", "party date-night office", "gold silver", "n", "metal", "Zinc alloy with gold-tone plating", "Chunky hoop earrings with a polished finish.")
p("Mango", "women-ethnic-wear", "Mango Festive Printed Kurta Set", 110, 0, "WOMEN", "Regular", "Printed", 4, "formal party-wear", "festive", "maroon emerald mustard", "n", "viscose", "100% viscose", "A printed kurta with matching palazzo trousers and a sheer dupatta.")
p("Mango", "men-shirts", "Mango Slim-Fit Linen Shirt", 60, 0, "MEN", "Slim", "Solid", 3, "minimalist casual", "weekend date-night travel", "white sky beige", "", "linen", "100% linen", "A slim-fit linen shirt with a classic collar, for warm days.")
# --- Under Armour
p("Under Armour", "men-tshirts", "Under Armour Tech 2.0 Tee", 25, 0, "MEN", "Regular", "Solid", 1, "streetwear casual", "workout weekend", "black royal grey red", "t", "synthetic", "100% polyester", "A quick-drying, anti-odor training tee with a loose, light feel.")
p("Under Armour", "men-hoodies", "Under Armour Rival Fleece Hoodie", 55, 25, "MEN", "Regular", "Solid", 1, "streetwear casual", "workout weekend", "black grey navy", "", "cotton", "80% cotton, 20% polyester fleece", "A warm fleece hoodie with a brushed interior and kangaroo pocket.")
p("Under Armour", "women-tops", "Under Armour Seamless Tank", 35, 0, "WOMEN", "Slim", "Solid", 1, "streetwear", "workout", "black lavender teal", "n", "synthetic", "92% nylon, 8% elastane", "A seamless, stretchy training tank with targeted ventilation.")
p("Under Armour", "women-hoodies", "Under Armour Essential Fleece Hoodie", 55, 0, "WOMEN", "Relaxed", "Solid", 1, "streetwear casual", "workout weekend", "grey black pink", "", "cotton", "60% cotton, 40% polyester", "A relaxed fleece hoodie with a soft brushed interior.")
p("Under Armour", "caps", "Under Armour Blitzing Cap", 25, 0, "UNISEX", None, "Solid", 1, "streetwear", "workout weekend", "black navy", "", "synthetic", "100% polyester stretch fit", "A stretch-fit cap with a sweat-wicking headband.")
p("Under Armour", "backpacks", "Under Armour Hustle Backpack", 55, 0, "UNISEX", None, "Solid", 1, "streetwear casual", "workout travel everyday", "black grey", "t", "canvas", "100% polyester, water-resistant finish", "A water-resistant backpack with a padded laptop sleeve.")
p("Under Armour", "men-trousers", "Under Armour Unstoppable Joggers", 75, 0, "MEN", "Slim", "Solid", 1, "streetwear casual", "workout travel weekend", "black olive", "n", "synthetic", "88% polyester, 12% elastane", "Tapered, stretch-woven joggers with zip pockets.")
# --- Michael Kors
p("Michael Kors", "watches", "Michael Kors Lexington Chronograph", 275, 0, "MEN", None, "Solid", 5, "formal party-wear", "office party festive", "gold silver black", "tf", "metal", "Stainless steel, chronograph movement", "A 44 mm chronograph with a link bracelet and three subdials.", strap="Metal")
p("Michael Kors", "watches", "Michael Kors Runway Watch", 225, 15, "WOMEN", None, "Solid", 4, "formal minimalist", "office date-night", "rose-gold gold silver", "t", "metal", "Stainless steel", "A 38 mm three-hand watch with a polished link bracelet.", strap="Metal")
p("Michael Kors", "handbags", "Michael Kors Jet Set Tote", 298, 0, "WOMEN", None, "Solid", 4, "formal minimalist", "office travel", "brown black beige", "f", "leather", "Saffiano leather, logo hardware", "A Saffiano leather tote with a top zip and organised interior.")
p("Michael Kors", "handbags", "Michael Kors Crossbody Bag", 168, 25, "WOMEN", None, "Solid", 3, "party-wear minimalist", "party date-night weekend", "black pink camel", "n", "leather", "Pebbled leather, chain strap", "A compact pebbled-leather crossbody with a chain strap.")
p("Michael Kors", "wallets", "Michael Kors Zip-Around Wallet", 98, 0, "WOMEN", None, "Solid", 3, "formal", "everyday office", "black brown pink", "", "leather", "Saffiano leather", "A zip-around wallet with twelve card slots and a coin pocket.")
p("Michael Kors", "sunglasses", "Michael Kors Oversized Sunglasses", 150, 0, "WOMEN", None, "Solid", 3, "party-wear", "weekend travel party", "tortoise black", "n", "synthetic", "Acetate frame, gradient lenses", "Oversized square frames with gradient lenses and metal logo detail.")
p("Michael Kors", "jewelry", "Michael Kors Pavé Pendant Necklace", 110, 0, "WOMEN", None, "Embellished", 4, "party-wear formal", "party festive date-night", "gold silver rose-gold", "t", "metal", "Sterling silver, cubic zirconia", "A delicate pendant necklace with a pavé-set charm.")
p("Michael Kors", "women-dresses", "Michael Kors Wrap Midi Dress", 175, 0, "WOMEN", "Regular", "Printed", 4, "party-wear formal", "party date-night festive", "black emerald burgundy", "n", "synthetic", "100% polyester crepe", "A printed crepe wrap dress with flutter sleeves and a self-tie waist.")

# --- additions for thinner categories
p("H&M", "men-ethnic-wear", "H&M Cotton Nehru Collar Kurta", 35, 0, "MEN", "Regular", "Solid", 4, "formal minimalist", "festive", "cream navy maroon", "", "cotton", "100% cotton", "A straight cotton kurta with a Nehru collar and side slits, easy to dress up.")
p("Mango", "men-ethnic-wear", "Mango Jacquard Bandhgala Jacket", 120, 0, "MEN", "Slim", "Textured", 5, "formal party-wear", "festive party", "navy black maroon", "n", "synthetic", "60% polyester, 40% viscose jacquard", "A tailored bandhgala jacket in a tonal jacquard, made for weddings and festive evenings.")
p("Zara", "women-ethnic-wear", "Zara Mirror-Work Anarkali Dress", 95, 15, "WOMEN", "Regular", "Embellished", 5, "party-wear formal", "festive party", "emerald maroon mustard", "t", "viscose", "100% viscose, mirror embellishment", "A flared anarkali dress with mirror-work detailing at the yoke.")
p("H&M", "women-jeans", "H&M Wide High Waist Jeans", 35, 0, "WOMEN", "Relaxed", "Solid", 2, "casual streetwear", "everyday weekend", "light-wash mid-wash", "t", "denim", "100% cotton", "High-waisted wide-leg jeans in rigid cotton denim.")
p("Calvin Klein", "women-jeans", "Calvin Klein High Rise Skinny Jeans", 90, 20, "WOMEN", "Slim", "Solid", 2, "minimalist casual", "everyday date-night", "black-denim dark-wash", "", "denim", "92% cotton, 6% polyester, 2% elastane", "A high-rise skinny jean in a clean stretch denim.")
p("Guess", "women-jeans", "Guess 1981 Straight Jeans", 108, 0, "WOMEN", "Regular", "Solid", 2, "casual party-wear", "weekend date-night", "mid-wash light-wash", "n", "denim", "99% cotton, 1% elastane", "Vintage-inspired high-rise straight jeans with a clean hem.")
p("H&M", "men-shirts", "H&M Regular Fit Oxford Shirt", 25, 0, "MEN", "Regular", "Solid", 3, "casual formal", "office weekend", "white sky", "", "cotton", "100% cotton oxford", "A regular-fit oxford shirt with a button-down collar.")
p("Levi's", "men-shirts", "Levi's Barstow Western Denim Shirt", 70, 0, "MEN", "Regular", "Solid", 2, "casual streetwear", "weekend everyday", "mid-wash dark-wash", "t", "denim", "100% cotton denim", "A western-style denim shirt with snap buttons and twin chest pockets.")
p("H&M", "women-jackets", "H&M Cropped Puffer Jacket", 50, 25, "WOMEN", "Relaxed", "Solid", 1, "streetwear casual", "travel weekend", "black cream sage", "", "synthetic", "100% polyester, recycled padding", "A cropped, quilted puffer with a stand-up collar.")

# ------------------------------------------------------------------ SQL
out = []
w = out.append
w("-- Fashion demo catalog (mock data for development and demonstration).")
w("-- Brand names are used as text only: no logos or brand imagery. Products, prices and stock are")
w("-- invented and not affiliated with or endorsed by the brands. Generated; do not edit by hand.")
w("-- Kept in db/seed so a deployment can start empty (FLYWAY_LOCATIONS=classpath:db/migration).")
w("")
w("-- 1. Retire the previous demo catalog without touching users, orders or saved bundles ----------")
w("DELETE FROM compatibility_rules;")
w("UPDATE goal_templates SET active = FALSE;")
w("-- products that were never ordered or used in a bundle can go; the rest are archived (order history)")
w("DELETE FROM products p WHERE NOT EXISTS (SELECT 1 FROM order_items oi WHERE oi.product_id = p.id)")
w("    AND NOT EXISTS (SELECT 1 FROM bundle_items bi WHERE bi.product_id = p.id);")
w("UPDATE products SET status = 'INACTIVE', featured = FALSE, trending = FALSE, new_arrival = FALSE;")
w("UPDATE categories SET active = FALSE;")
w("DELETE FROM brands b WHERE NOT EXISTS (SELECT 1 FROM products p WHERE p.brand_id = b.id);")
w("UPDATE brands SET active = FALSE;")
w("")
w("-- 2. Departments and categories ----------------------------------------------------------------")
w("INSERT INTO categories (slug, name, description, sort_order) VALUES")
w(",\n".join(f"    ({q(s)}, {q(n)}, {q(d)}, {o})" for s, n, d, o in DEPARTMENTS) + ";")
w("")
rows = []
for i, (slug, dept, name, desc, slot, scale) in enumerate(CATEGORIES):
    rows.append(f"    ({q(dept)}, {q(slug)}, {q(name)}, {q(desc)}, {i + 1}, {q(slot)}, {q(scale)})")
w("INSERT INTO categories (parent_id, slug, name, description, sort_order, outfit_slot, size_scale)")
w("SELECT d.id, v.slug, v.name, v.description, v.sort_order, v.slot, v.scale FROM (VALUES")
w(",\n".join(rows))
w(") AS v(dept, slug, name, description, sort_order, slot, scale) JOIN categories d ON d.slug = v.dept;")
w("")
w("-- Attribute definitions: filters, and what the outfit-matching rules read")
attr_rows = []
for slug, dept, *_ in CATEGORIES:
    if dept != "accessories":
        attr_rows.append(f"    ({q(slug)}, 'fit', 'Fit', 'TEXT', NULL, TRUE, 1)")
        attr_rows.append(f"    ({q(slug)}, 'pattern', 'Pattern', 'TEXT', NULL, TRUE, 2)")
    attr_rows.append(f"    ({q(slug)}, 'formality', 'Formality (1 casual - 5 formal)', 'NUMBER', NULL, FALSE, 3)")
attr_rows.append("    ('watches', 'strap', 'Strap', 'TEXT', NULL, TRUE, 4)")
w("INSERT INTO attribute_definitions (category_id, key, label, data_type, unit, filterable, sort_order)")
w("SELECT c.id, v.key, v.label, v.data_type, v.unit, v.filterable, v.sort_order FROM (VALUES")
w(",\n".join(attr_rows))
w(") AS v(category, key, label, data_type, unit, filterable, sort_order) JOIN categories c ON c.slug = v.category;")
w("")
w("-- 3. Brands, styles, occasions ------------------------------------------------------------------")
w("INSERT INTO brands (slug, name, description, featured, sort_order) VALUES")
w(",\n".join(f"    ({q(slugify(n))}, {q(n)}, {q(d)}, {'TRUE' if f else 'FALSE'}, {i + 1})" for i, (n, d, _, f) in enumerate(BRANDS)) + ";")
w("")
w("INSERT INTO styles (slug, name, description, sort_order) VALUES")
w(",\n".join(f"    ({q(s)}, {q(n)}, {q(d)}, {i + 1})" for i, (s, n, d) in enumerate(STYLES)) + ";")
w("")
w("INSERT INTO occasions (slug, name, description, sort_order) VALUES")
w(",\n".join(f"    ({q(s)}, {q(n)}, {q(d)}, {i + 1})" for i, (s, n, d) in enumerate(OCCASIONS)) + ";")
w("")
w("-- 4. Products -----------------------------------------------------------------------------------")
tier = {n: t for n, _, t, _ in BRANDS}
seq = {}
prod_rows, var_rows, attr_val_rows, style_rows, occ_rows, tag_rows = [], [], [], [], [], []
slugs = set()
total_variants = 0
for i, x in enumerate(P):
    code = BRAND_CODE[x["brand"]]
    seq[code] = seq.get(code, 0) + 1
    sku = f"{code}-{seq[code]:03d}"
    slug = slugify(x["name"])
    assert slug not in slugs, slug
    slugs.add(slug)
    price = float(x["price"])
    sale_price = round(price * (1 - x["sale"] / 100.0)) - 0.01 if x["sale"] else None
    current = sale_price if sale_price else price - 0.01 if price > 20 else price
    compare = f"{price - 0.01:.2f}" if x["sale"] else "NULL"
    current = round(current, 2)
    rating = round(random.uniform(3.8, 4.9), 1)
    count = random.randint(12, 950)
    flags = x["flags"]
    fabric = x["fabric"]
    desc = x["blurb"] + (f" {x['fit']} fit." if x["fit"] else "")
    prod_rows.append(
        f"    ({q(x['cat'])}, {q(x['brand'])}, {q(sku)}, {q(slug)}, {q(x['name'])}, {q(desc)}, {current:.2f}, {compare}, "
        f"{rating}, {count}, {tier[x['brand']]}, {q(x['gender'])}, {'TRUE' if 'f' in flags else 'FALSE'}, "
        f"{'TRUE' if 'n' in flags else 'FALSE'}, {'TRUE' if 't' in flags else 'FALSE'}, {q(x['material'])}, {q(CARE[fabric])}, {i})")
    scale = CAT[x["cat"]][5]
    sizes = SIZES[(scale, None)] if scale == "ONE_SIZE" else SIZES[(scale, "WOMEN" if x["gender"] == "WOMEN" else "MEN")]
    order = 0
    for ck in x["colors"]:
        cname, chex, fam = COLORS[ck]
        for size in sizes:
            vsku = f"{sku}-{ck.upper().replace('-', '')[:4]}-{size.replace(' ', '').upper()[:4]}"
            r = random.random()
            stock = 0 if r < 0.08 else random.randint(1, 4) if r < 0.22 else random.randint(5, 40)
            if size in ("XXL", "XS", "38", "24"):
                stock = min(stock, random.randint(0, 6))
            var_rows.append(f"    ({q(sku)}, {q(vsku)}, {q(size)}, {q(cname)}, {q(chex)}, {q(fam)}, {order}, {stock})")
            order += 1
            total_variants += 1
    if x["fit"]:
        attr_val_rows.append(f"    ({q(sku)}, 'fit', {q(x['fit'])}, NULL)")
        attr_val_rows.append(f"    ({q(sku)}, 'pattern', {q(x['pattern'])}, NULL)")
    attr_val_rows.append(f"    ({q(sku)}, 'formality', NULL, {x['formality']})")
    if "strap" in x["extra"]:
        attr_val_rows.append(f"    ({q(sku)}, 'strap', {q(x['extra']['strap'])}, NULL)")
    for s in x["styles"]:
        style_rows.append(f"    ({q(sku)}, {q(s)})")
        tag_rows.append(f"    ({q(sku)}, {q(s)})")
    for o in x["occasions"]:
        occ_rows.append(f"    ({q(sku)}, {q(o)})")
        tag_rows.append(f"    ({q(sku)}, {q(o)})")

w("INSERT INTO products (category_id, brand_id, brand, sku, slug, name, description, price, compare_at_price, rating_avg,")
w("                      rating_count, quality_tier, gender, featured, new_arrival, trending, material, care, created_at)")
w("SELECT c.id, b.id, b.name, v.sku, v.slug, v.name, v.description, v.price, v.compare_at, v.rating, v.count, v.tier,")
w("       v.gender, v.featured, v.new_arrival, v.trending, v.material, v.care, now() - make_interval(hours => v.age)")
w("FROM (VALUES")
w(",\n".join(prod_rows))
w(") AS v(category, brand, sku, slug, name, description, price, compare_at, rating, count, tier, gender, featured,")
w("       new_arrival, trending, material, care, age)")
w("JOIN categories c ON c.slug = v.category JOIN brands b ON b.name = v.brand;")
w("")
w("-- 5. Variants (size x color) and their stock ----------------------------------------------------")
w("CREATE TEMPORARY TABLE seed_variants (product_sku VARCHAR(40), sku VARCHAR(48), size VARCHAR(12), color VARCHAR(40),")
w("    color_hex VARCHAR(7), color_family VARCHAR(10), sort_order INT, stock INT) ON COMMIT DROP;")
w("INSERT INTO seed_variants VALUES")
w(",\n".join(var_rows) + ";")
w("INSERT INTO product_variants (product_id, sku, size, color, color_hex, color_family, sort_order)")
w("SELECT p.id, s.sku, s.size, s.color, s.color_hex, s.color_family, s.sort_order")
w("FROM seed_variants s JOIN products p ON p.sku = s.product_sku;")
w("INSERT INTO variant_stock (variant_id, quantity_on_hand)")
w("SELECT v.id, s.stock FROM seed_variants s JOIN product_variants v ON v.sku = s.sku;")
w("")
w("-- 6. Specifications, styles, occasions and tags ------------------------------------------------")
w("INSERT INTO product_attributes (product_id, attribute_id, value_text, value_number)")
w("SELECT p.id, ad.id, v.text_value, v.number_value FROM (VALUES")
w(",\n".join(attr_val_rows))
w(") AS v(sku, key, text_value, number_value)")
w("JOIN products p ON p.sku = v.sku JOIN attribute_definitions ad ON ad.category_id = p.category_id AND ad.key = v.key;")
w("")
w("INSERT INTO product_styles (product_id, style_id)")
w("SELECT p.id, s.id FROM (VALUES")
w(",\n".join(style_rows))
w(") AS v(sku, style) JOIN products p ON p.sku = v.sku JOIN styles s ON s.slug = v.style;")
w("")
w("INSERT INTO product_occasions (product_id, occasion_id)")
w("SELECT p.id, o.id FROM (VALUES")
w(",\n".join(occ_rows))
w(") AS v(sku, occasion) JOIN products p ON p.sku = v.sku JOIN occasions o ON o.slug = v.occasion;")
w("")
w("-- tags mirror styles and occasions: the recommendation engine scores relevance on product tags")
w("INSERT INTO product_tags (product_id, tag)")
w("SELECT p.id, v.tag FROM (VALUES")
w(",\n".join(tag_rows))
w(") AS v(sku, tag) JOIN products p ON p.sku = v.sku;")

open(OUT, "w", encoding="utf-8", newline="\n").write("\n".join(out) + "\n")
print(f"{len(P)} products, {total_variants} variants, {len(BRANDS)} brands")
from collections import Counter
print(Counter(x["brand"] for x in P))
print(Counter(x["cat"] for x in P))
