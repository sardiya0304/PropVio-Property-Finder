import React, { useState } from 'react';
import { Calculator, MapPin, Home, IndianRupee, TrendingUp, Loader2, AlertCircle } from 'lucide-react';
import { aiCalculatorAPI } from '../../services/api';

const SUPPORTED_CITIES = [
  'Mumbai', 'Delhi', 'Delhi NCR', 'Bangalore', 'Pune', 'Hyderabad',
  'Chennai', 'Kolkata', 'Ahmedabad', 'Gurgaon', 'Noida', 'Jaipur',
  'Lucknow', 'Indore', 'Nagpur', 'Chandigarh', 'Kochi', 'Surat',
  'Thane', 'Navi Mumbai', 'Mysore', 'Vadodara', 'Nashik',
];

const FURNISHING_OPTIONS = ['Unfurnished', 'Semi-Furnished', 'Fully Furnished'];

interface CalculatorResult {
  estimatedPrice: string;
  minPriceFormatted: string;
  maxPriceFormatted: string;
  predictedPriceLakhs: number;
  pricePerSqft: number;
  insight: string;
  marketLabel: string;
  city: string;
  bhk: number;
  areaSqft: number;
  furnishing: string;
}

const MARKET_COLORS: Record<string, string> = {
  Premium:    'bg-amber-100 text-amber-800 border-amber-200',
  'Mid-Range': 'bg-blue-100 text-blue-800 border-blue-200',
  Affordable: 'bg-green-100 text-green-800 border-green-200',
};

const AIPriceCalculator: React.FC = () => {
  const [city, setCity]           = useState('Bangalore');
  const [bhk, setBhk]             = useState(2);
  const [areaSqft, setAreaSqft]   = useState(1200);
  const [ageYears, setAgeYears]   = useState(5);
  const [furnishing, setFurnishing] = useState('Semi-Furnished');

  const [result, setResult]   = useState<CalculatorResult | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError]     = useState('');

  const calculate = async () => {
    setLoading(true);
    setError('');
    setResult(null);
    try {
      const res = await aiCalculatorAPI.calculate({ city, bhk, areaSqft, ageYears, furnishing });
      setResult(res.data.data);
    } catch (err: any) {
      const msg = err.response?.data?.message || 'Could not connect to the price calculator. Make sure the ML service is running.';
      setError(msg);
    } finally {
      setLoading(false);
    }
  };

  return (
    <section className="py-20 bg-gradient-to-b from-[#FAF8F4] to-white">
      <div className="max-w-[1280px] mx-auto px-8">

        {/* Header */}
        <div className="text-center mb-12">
          <div className="inline-flex items-center gap-2 bg-[rgba(212,117,91,0.1)] border border-[rgba(212,117,91,0.25)] rounded-full px-4 py-2 mb-4">
            <Calculator className="w-4 h-4 text-[#D4755B]" />
            <span className="font-manrope text-sm font-semibold text-[#D4755B] uppercase tracking-wide">
              AI Price Calculator
            </span>
          </div>
          <h2 className="font-fraunces text-4xl font-bold text-[#221410] mb-3">
            Know Your Property's Worth
          </h2>
          <p className="font-manrope text-[#6b7280] text-lg max-w-xl mx-auto">
            Powered by a Random Forest model trained on Indian real estate data.
            Get an instant price estimate for any property.
          </p>
        </div>

        <div className="grid grid-cols-1 lg:grid-cols-5 gap-8 items-start">

          {/* ── Input Form ─────────────────────────────────────── */}
          <div className="lg:col-span-2 bg-white rounded-2xl border border-[#e5e0d8] shadow-sm p-8">
            <h3 className="font-fraunces text-xl font-semibold text-[#221410] mb-6">
              Property Details
            </h3>

            {/* City */}
            <div className="mb-5">
              <label className="block font-manrope text-sm font-medium text-[#374151] mb-2">
                <MapPin className="inline w-4 h-4 mr-1 text-[#D4755B]" />City
              </label>
              <select
                value={city}
                onChange={e => setCity(e.target.value)}
                className="w-full border border-[#e5e0d8] rounded-xl px-4 py-3 font-manrope text-[#221410] focus:outline-none focus:border-[#D4755B] focus:ring-2 focus:ring-[rgba(212,117,91,0.15)] bg-white"
              >
                {SUPPORTED_CITIES.map(c => (
                  <option key={c} value={c}>{c}</option>
                ))}
              </select>
            </div>

            {/* BHK */}
            <div className="mb-5">
              <label className="block font-manrope text-sm font-medium text-[#374151] mb-2">
                <Home className="inline w-4 h-4 mr-1 text-[#D4755B]" />BHK Configuration
              </label>
              <div className="grid grid-cols-5 gap-2">
                {[1, 2, 3, 4, 5].map(n => (
                  <button
                    key={n}
                    onClick={() => setBhk(n)}
                    className={`py-3 rounded-xl font-manrope font-semibold text-sm transition-all ${
                      bhk === n
                        ? 'bg-[#D4755B] text-white shadow-md'
                        : 'bg-[#FAF8F4] text-[#6b7280] border border-[#e5e0d8] hover:border-[#D4755B] hover:text-[#D4755B]'
                    }`}
                  >
                    {n}BHK
                  </button>
                ))}
              </div>
            </div>

            {/* Area */}
            <div className="mb-5">
              <label className="block font-manrope text-sm font-medium text-[#374151] mb-2">
                Area (sq ft) — {areaSqft} sqft
              </label>
              <input
                type="range"
                min={300} max={5000} step={50}
                value={areaSqft}
                onChange={e => setAreaSqft(Number(e.target.value))}
                className="w-full accent-[#D4755B]"
              />
              <div className="flex justify-between font-manrope text-xs text-[#9ca3af] mt-1">
                <span>300 sqft</span><span>5000 sqft</span>
              </div>
            </div>

            {/* Property Age */}
            <div className="mb-5">
              <label className="block font-manrope text-sm font-medium text-[#374151] mb-2">
                Property Age — {ageYears === 0 ? 'New / Under Construction' : `${ageYears} years`}
              </label>
              <input
                type="range"
                min={0} max={30} step={1}
                value={ageYears}
                onChange={e => setAgeYears(Number(e.target.value))}
                className="w-full accent-[#D4755B]"
              />
              <div className="flex justify-between font-manrope text-xs text-[#9ca3af] mt-1">
                <span>New</span><span>30 years</span>
              </div>
            </div>

            {/* Furnishing */}
            <div className="mb-8">
              <label className="block font-manrope text-sm font-medium text-[#374151] mb-2">
                Furnishing Status
              </label>
              <div className="grid grid-cols-1 gap-2">
                {FURNISHING_OPTIONS.map(f => (
                  <button
                    key={f}
                    onClick={() => setFurnishing(f)}
                    className={`py-3 px-4 rounded-xl font-manrope text-sm text-left transition-all ${
                      furnishing === f
                        ? 'bg-[#D4755B] text-white shadow-md'
                        : 'bg-[#FAF8F4] text-[#6b7280] border border-[#e5e0d8] hover:border-[#D4755B] hover:text-[#D4755B]'
                    }`}
                  >
                    {f}
                  </button>
                ))}
              </div>
            </div>

            <button
              onClick={calculate}
              disabled={loading}
              className="w-full bg-[#D4755B] hover:bg-[#B86851] disabled:opacity-60 text-white font-manrope font-bold text-base py-4 rounded-xl transition-all shadow-lg hover:shadow-xl flex items-center justify-center gap-2"
            >
              {loading ? (
                <><Loader2 className="w-5 h-5 animate-spin" />Calculating...</>
              ) : (
                <><Calculator className="w-5 h-5" />Calculate Price</>
              )}
            </button>
          </div>

          {/* ── Result Panel ───────────────────────────────────── */}
          <div className="lg:col-span-3">
            {error && (
              <div className="bg-red-50 border border-red-200 rounded-2xl p-6 flex gap-3">
                <AlertCircle className="w-5 h-5 text-red-500 flex-shrink-0 mt-0.5" />
                <p className="font-manrope text-red-700 text-sm">{error}</p>
              </div>
            )}

            {!result && !error && !loading && (
              <div className="bg-[#FAF8F4] border-2 border-dashed border-[#e5e0d8] rounded-2xl p-12 text-center">
                <IndianRupee className="w-12 h-12 text-[#D4755B] mx-auto mb-4 opacity-40" />
                <p className="font-fraunces text-xl text-[#9ca3af] mb-2">Your estimate appears here</p>
                <p className="font-manrope text-sm text-[#9ca3af]">
                  Fill in the property details and click Calculate Price
                </p>
              </div>
            )}

            {loading && (
              <div className="bg-white border border-[#e5e0d8] rounded-2xl p-12 text-center">
                <Loader2 className="w-10 h-10 text-[#D4755B] mx-auto mb-4 animate-spin" />
                <p className="font-manrope text-[#6b7280]">Running AI model...</p>
              </div>
            )}

            {result && (
              <div className="space-y-4">

                {/* Main Price Card */}
                <div className="bg-gradient-to-br from-[#221410] to-[#3d2318] rounded-2xl p-8 text-white">
                  <div className="flex items-start justify-between mb-6">
                    <div>
                      <p className="font-manrope text-[#9ca3af] text-sm mb-1">Estimated Market Price</p>
                      <p className="font-fraunces text-5xl font-bold">{result.estimatedPrice}</p>
                      <p className="font-manrope text-[#9ca3af] text-sm mt-2">
                        Range: {result.minPriceFormatted} – {result.maxPriceFormatted}
                      </p>
                    </div>
                    <span className={`text-xs font-manrope font-bold px-3 py-1.5 rounded-full border ${MARKET_COLORS[result.marketLabel] || 'bg-gray-100 text-gray-700 border-gray-200'}`}>
                      {result.marketLabel}
                    </span>
                  </div>

                  {/* Stats row */}
                  <div className="grid grid-cols-3 gap-4 pt-6 border-t border-[rgba(255,255,255,0.1)]">
                    <div>
                      <p className="font-manrope text-[#9ca3af] text-xs mb-1">Price/sqft</p>
                      <p className="font-manrope font-bold text-lg">₹{result.pricePerSqft.toLocaleString('en-IN')}</p>
                    </div>
                    <div>
                      <p className="font-manrope text-[#9ca3af] text-xs mb-1">Configuration</p>
                      <p className="font-manrope font-bold text-lg">{result.bhk}BHK · {result.areaSqft} sqft</p>
                    </div>
                    <div>
                      <p className="font-manrope text-[#9ca3af] text-xs mb-1">In Lakhs</p>
                      <p className="font-manrope font-bold text-lg">₹{result.predictedPriceLakhs.toFixed(2)} L</p>
                    </div>
                  </div>
                </div>

                {/* AI Insight Card */}
                <div className="bg-white border border-[#e5e0d8] rounded-2xl p-6">
                  <div className="flex items-center gap-2 mb-3">
                    <TrendingUp className="w-5 h-5 text-[#D4755B]" />
                    <h4 className="font-manrope font-semibold text-[#221410]">AI Insight</h4>
                  </div>
                  <p className="font-manrope text-[#4b5563] text-sm leading-relaxed">{result.insight}</p>
                </div>

                {/* Confidence range bar */}
                <div className="bg-white border border-[#e5e0d8] rounded-2xl p-6">
                  <p className="font-manrope text-sm font-medium text-[#374151] mb-4">
                    Price Confidence Range (±15%)
                  </p>
                  <div className="relative h-3 bg-[#FAF8F4] rounded-full overflow-hidden">
                    <div
                      className="absolute inset-y-0 left-0 right-0 bg-gradient-to-r from-[rgba(212,117,91,0.3)] via-[#D4755B] to-[rgba(212,117,91,0.3)] rounded-full"
                    />
                    {/* Center marker */}
                    <div className="absolute inset-y-0 left-1/2 -translate-x-1/2 w-0.5 bg-white" />
                  </div>
                  <div className="flex justify-between font-manrope text-xs text-[#9ca3af] mt-2">
                    <span>{result.minPriceFormatted}</span>
                    <span className="font-semibold text-[#D4755B]">{result.estimatedPrice}</span>
                    <span>{result.maxPriceFormatted}</span>
                  </div>
                </div>

                {/* Disclaimer */}
                <p className="font-manrope text-xs text-[#9ca3af] text-center px-4">
                  Estimates are based on a Random Forest model trained on synthetic Indian housing data.
                  Actual market prices may vary. Use this as a reference, not a valuation.
                </p>
              </div>
            )}
          </div>
        </div>
      </div>
    </section>
  );
};

export default AIPriceCalculator;
