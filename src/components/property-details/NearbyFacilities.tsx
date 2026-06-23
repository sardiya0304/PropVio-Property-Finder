import React, { useState, useEffect, useCallback } from 'react';
import { Loader2, RefreshCw } from 'lucide-react';

interface Facility {
  id: string;
  name: string;
  distance: number;
}

interface CategoryResult {
  key: string;
  label: string;
  emoji: string;
  color: string;
  bgColor: string;
  facilities: Facility[];
}

interface NearbyFacilitiesProps {
  location?: string;
  googleMapLink?: string;
}

const RADIUS_METERS = 5000;

const CATEGORY_DEFS = [
  { key: 'hospital',   label: 'Hospitals',     emoji: '🏥', color: '#EF4444', bgColor: 'rgba(239,68,68,0.08)' },
  { key: 'school',     label: 'Schools',        emoji: '🏫', color: '#3B82F6', bgColor: 'rgba(59,130,246,0.08)' },
  { key: 'metro',      label: 'Metro Stations', emoji: '🚇', color: '#8B5CF6', bgColor: 'rgba(139,92,246,0.08)' },
  { key: 'mall',       label: 'Shopping Malls', emoji: '🛍️', color: '#F59E0B', bgColor: 'rgba(245,158,11,0.08)' },
  { key: 'restaurant', label: 'Restaurants',    emoji: '🍽️', color: '#10B981', bgColor: 'rgba(16,185,129,0.08)' },
];

// Overpass endpoints to try in order
const OVERPASS_ENDPOINTS = [
  'https://overpass-api.de/api/interpreter',
  'https://overpass.kumi.systems/api/interpreter',
  'https://maps.mail.ru/osm/tools/overpass/api/interpreter',
];

function haversine(lat1: number, lon1: number, lat2: number, lon2: number): number {
  const R = 6371000;
  const toRad = (d: number) => (d * Math.PI) / 180;
  const dLat = toRad(lat2 - lat1);
  const dLon = toRad(lon2 - lon1);
  const a =
    Math.sin(dLat / 2) ** 2 +
    Math.cos(toRad(lat1)) * Math.cos(toRad(lat2)) * Math.sin(dLon / 2) ** 2;
  return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
}

function formatDistance(meters: number): string {
  return meters < 1000 ? `${Math.round(meters)} m` : `${(meters / 1000).toFixed(1)} km`;
}

function categorize(tags: Record<string, string>): string | null {
  const a = tags.amenity;
  const r = tags.railway;
  const s = tags.shop;
  const hc = tags.healthcare;
  if (a === 'hospital' || a === 'clinic' || hc === 'hospital' || hc === 'clinic') return 'hospital';
  if (a === 'school' || a === 'college' || a === 'university') return 'school';
  if (r === 'station' || r === 'subway_entrance') return 'metro';
  if (s === 'mall' || s === 'supermarket' || s === 'department_store') return 'mall';
  if (a === 'restaurant' || a === 'fast_food' || a === 'cafe') return 'restaurant';
  return null;
}

async function resolveCoords(
  location: string,
  googleMapLink?: string,
): Promise<{ lat: number; lon: number } | null> {
  // 1. Try to extract coordinates directly from a Google Maps link
  if (googleMapLink) {
    const m = googleMapLink.match(/@(-?\d+\.\d+),(-?\d+\.\d+)/);
    if (m) return { lat: parseFloat(m[1]), lon: parseFloat(m[2]) };
  }

  // 2. Geocode via Nominatim — no custom User-Agent (forbidden browser header)
  try {
    const url =
      `https://nominatim.openstreetmap.org/search` +
      `?q=${encodeURIComponent(location)}&format=json&limit=1&addressdetails=0`;
    const res = await fetch(url);
    if (!res.ok) throw new Error(`Nominatim ${res.status}`);
    const data = await res.json();
    if (Array.isArray(data) && data.length > 0) {
      return { lat: parseFloat(data[0].lat), lon: parseFloat(data[0].lon) };
    }
  } catch (err) {
    console.error('[NearbyFacilities] Geocoding failed:', err);
  }
  return null;
}

function buildOverpassQuery(lat: number, lon: number, r: number): string {
  // Use explicit = conditions (not ~ regex) for maximum server compatibility
  return `
[out:json][timeout:30];
(
  node["amenity"="hospital"](around:${r},${lat},${lon});
  way["amenity"="hospital"](around:${r},${lat},${lon});
  node["amenity"="clinic"](around:${r},${lat},${lon});
  way["amenity"="clinic"](around:${r},${lat},${lon});
  node["healthcare"="hospital"](around:${r},${lat},${lon});
  node["amenity"="school"](around:${r},${lat},${lon});
  way["amenity"="school"](around:${r},${lat},${lon});
  node["amenity"="college"](around:${r},${lat},${lon});
  way["amenity"="college"](around:${r},${lat},${lon});
  node["amenity"="university"](around:${r},${lat},${lon});
  way["amenity"="university"](around:${r},${lat},${lon});
  node["railway"="station"](around:${r},${lat},${lon});
  node["railway"="subway_entrance"](around:${r},${lat},${lon});
  node["shop"="mall"](around:${r},${lat},${lon});
  way["shop"="mall"](around:${r},${lat},${lon});
  node["shop"="supermarket"](around:${r},${lat},${lon});
  way["shop"="supermarket"](around:${r},${lat},${lon});
  node["shop"="department_store"](around:${r},${lat},${lon});
  way["shop"="department_store"](around:${r},${lat},${lon});
  node["amenity"="restaurant"](around:${r},${lat},${lon});
  node["amenity"="fast_food"](around:${r},${lat},${lon});
  node["amenity"="cafe"](around:${r},${lat},${lon});
);
out center;
`.trim();
}

async function fetchNearby(lat: number, lon: number): Promise<CategoryResult[]> {
  const query = buildOverpassQuery(lat, lon, RADIUS_METERS);
  const body = `data=${encodeURIComponent(query)}`;

  let lastError: unknown;
  for (const endpoint of OVERPASS_ENDPOINTS) {
    try {
      const res = await fetch(endpoint, {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body,
      });
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const data = await res.json();

      const buckets: Record<string, Facility[]> = {};
      CATEGORY_DEFS.forEach(c => (buckets[c.key] = []));

      for (const el of data.elements ?? []) {
        const tags: Record<string, string> = el.tags ?? {};
        const catKey = categorize(tags);
        if (!catKey) continue;
        const elLat: number | undefined = el.lat ?? el.center?.lat;
        const elLon: number | undefined = el.lon ?? el.center?.lon;
        if (elLat == null || elLon == null) continue;
        const name: string = tags['name:en'] || tags.name || 'Unnamed';
        buckets[catKey].push({
          id: `${el.type}-${el.id}`,
          name,
          distance: haversine(lat, lon, elLat, elLon),
        });
      }

      return CATEGORY_DEFS.map(def => ({
        ...def,
        facilities: (buckets[def.key] ?? [])
          .sort((a, b) => a.distance - b.distance)
          .slice(0, 3),
      }));
    } catch (err) {
      console.error(`[NearbyFacilities] Overpass endpoint failed (${endpoint}):`, err);
      lastError = err;
    }
  }

  throw lastError;
}

const NearbyFacilities: React.FC<NearbyFacilitiesProps> = ({ location, googleMapLink }) => {
  const [categories, setCategories] = useState<CategoryResult[]>([]);
  const [status, setStatus] = useState<'idle' | 'loading' | 'done' | 'error'>('idle');

  const load = useCallback(async () => {
    if (!location && !googleMapLink) return;
    setStatus('loading');
    try {
      const coords = await resolveCoords(location ?? '', googleMapLink);
      if (!coords) {
        console.error('[NearbyFacilities] Could not resolve coordinates for:', location);
        setStatus('error');
        return;
      }
      const results = await fetchNearby(coords.lat, coords.lon);
      setCategories(results);
      setStatus('done');
    } catch (err) {
      console.error('[NearbyFacilities] Failed to load:', err);
      setStatus('error');
    }
  }, [location, googleMapLink]);

  useEffect(() => { load(); }, [load]);

  const hasAnyResults = categories.some(c => c.facilities.length > 0);
  const visibleCategories = categories.filter(c => c.facilities.length > 0);

  return (
    <div className="mb-12">
      <div className="flex items-center gap-3 mb-6">
        <div className="w-1 h-6 bg-[#D4755B] rounded-full" />
        <h2 className="font-syne text-2xl text-[#0F172A]">Nearby Facilities</h2>
      </div>

      {status === 'loading' && (
        <div className="flex items-center justify-center py-12 gap-3">
          <Loader2 className="w-5 h-5 text-[#D4755B] animate-spin" />
          <span className="font-manrope text-sm text-[#64748B]">Finding nearby facilities…</span>
        </div>
      )}

      {status === 'error' && (
        <div className="text-center py-10">
          <p className="font-manrope text-sm text-[#64748B] mb-3">Could not load nearby facilities.</p>
          <button
            onClick={load}
            className="inline-flex items-center gap-2 text-[#D4755B] font-manrope text-sm hover:underline"
          >
            <RefreshCw className="w-4 h-4" />
            Try again
          </button>
        </div>
      )}

      {status === 'done' && !hasAnyResults && (
        <div className="bg-white border border-[#E6E0DA] rounded-xl p-6 text-center">
          <p className="font-manrope text-sm text-[#64748B]">
            No nearby facilities found within {RADIUS_METERS / 1000} km.
          </p>
        </div>
      )}

      {status === 'done' && hasAnyResults && (
        <>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            {visibleCategories.map(category => (
              <div
                key={category.key}
                className="bg-white border border-[#E6E0DA] rounded-xl p-5 hover:shadow-sm transition-shadow"
              >
                <div className="flex items-center gap-2 mb-4">
                  <span className="text-xl leading-none">{category.emoji}</span>
                  <h3
                    className="font-manrope font-semibold text-sm uppercase tracking-wide"
                    style={{ color: category.color }}
                  >
                    {category.label}
                  </h3>
                </div>

                <ul className="space-y-2.5">
                  {category.facilities.map(f => (
                    <li key={f.id} className="flex items-center justify-between gap-3">
                      <span className="font-manrope text-sm text-[#374151] truncate">{f.name}</span>
                      <span
                        className="font-manrope text-xs font-semibold shrink-0 px-2 py-0.5 rounded-full whitespace-nowrap"
                        style={{ color: category.color, backgroundColor: category.bgColor }}
                      >
                        {formatDistance(f.distance)}
                      </span>
                    </li>
                  ))}
                </ul>
              </div>
            ))}
          </div>

          <p className="mt-3 font-manrope text-xs text-[#94A3B8] text-right">
            Facilities within {RADIUS_METERS / 1000} km · Data via OpenStreetMap
          </p>
        </>
      )}
    </div>
  );
};

export default NearbyFacilities;
