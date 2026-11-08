// User-supplied demo assets; exact public names avoid guessing identity from IDs.
// Exact public location names are intentional: province alone can identify many cities.
export interface DestinationAsset { key: string; name: string; locationNames: readonly string[]; file: string; imageUrl: string | null }
export const destinationAssets: readonly DestinationAsset[] = [
  { key: "ho-chi-minh", name: "TP. Hồ Chí Minh", locationNames: ["Bến xe TP. Hồ Chí Minh", "TP. Hồ Chí Minh"], file: "destination-ho-chi-minh.webp", imageUrl: "/images/busgo/destination-ho-chi-minh.webp" },
  { key: "da-lat", name: "Đà Lạt", locationNames: ["Bến xe Đà Lạt", "Đà Lạt"], file: "destination-da-lat.webp", imageUrl: "/images/busgo/destination-da-lat.webp" },
  { key: "buon-ma-thuot", name: "Buôn Ma Thuột", locationNames: ["Bến xe Buôn Ma Thuột", "Buôn Ma Thuột"], file: "destination-buon-ma-thuot.webp", imageUrl: "/images/busgo/destination-buon-ma-thuot.webp" },
  { key: "nha-trang", name: "Nha Trang", locationNames: ["Bến xe Nha Trang", "Nha Trang"], file: "destination-nha-trang.webp", imageUrl: "/images/busgo/destination-nha-trang.webp" },
  { key: "da-nang", name: "Đà Nẵng", locationNames: ["Bến xe Đà Nẵng", "Đà Nẵng"], file: "destination-da-nang-optimized.webp", imageUrl: "/images/busgo/destination-da-nang-optimized.webp" },
  { key: "hue", name: "Huế", locationNames: ["Bến xe Huế", "Huế"], file: "destination-hue.webp", imageUrl: "/images/busgo/destination-hue.webp" },
];
const normalize = (value: string) => value.normalize("NFC").trim().replace(/\s+/g, " ").toLocaleLowerCase("vi");
export function destinationForLocation(locationId: number, locations: readonly { id: number; name: string }[] = []) {
  const location = locations.find(l => l.id === locationId);
  return location ? destinationAssets.find(a => a.locationNames.some(name => normalize(name) === normalize(location.name))) : undefined;
}
export function safeImageUrl(value?: string | null): string | undefined {
  const url = value?.trim();
  if (!url || /[\s\\\u0000-\u001f\u007f]/.test(url)) return undefined;
  if (/^\/(?!\/)/.test(url)) return url;
  try { const parsed = new URL(url); return parsed.protocol === "https:" && parsed.hostname && !parsed.username && !parsed.password ? url : undefined; } catch { return undefined; }
}
export const operatorDescription = (description?: string | null) => description?.trim() || "Thông tin giới thiệu chưa được cung cấp.";
export const heroAsset = { imageUrl: null as string | null, fallbackUrl: "/images/busgo/hero-coach.jpg", desktopPosition: "62% center", mobilePosition: "62% center" };

export const interiorAssets = {
  sleeper: "/images/busgo/interior-sleeper-standard.webp",
  cabin: "/images/busgo/interior-limousine-cabin.webp",
  vip: "/images/busgo/interior-limousine-seat-vip.webp",
} as const;
// Catalogue LIMO22 has one floor, 11 rows, columns 1/3 (1+1 seating).
// The supplied seat image matches that basic layout only; its VIP amenities are unverified.
// Generic limousine names or STANDARD seatType do not establish a layout.
export function interiorForBusType(busType: { name: string }) {
  const name = normalize(busType.name);
  if (name === normalize("Giường nằm 34 chỗ")) return interiorAssets.sleeper;
  if (name === normalize("Limousine 22 chỗ")) return interiorAssets.vip;
  return undefined;
}
const demoLogos: Readonly<Record<string, string>> = {
  "An Phú Express": "operator-an-phu-express-logo.webp",
  "Minh Thành Limousine": "operator-minh-thanh-limousine-logo.webp",
  "Tây Nguyên Travel": "operator-tay-nguyen-travel-logo.webp",
};
export function demoOperatorLogo(name: string) {
  const file = demoLogos[name.normalize("NFC").trim()];
  return file ? `/images/busgo/${file}` : undefined;
}
