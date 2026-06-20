import { useEffect } from 'react';

interface SEOProps {
  title?: string;
  description?: string;
}

/**
 * Sets document title and meta description for the current page.
 * Title is appended with " | Propvio" suffix.
 */
export function useSEO({ title, description }: SEOProps) {
  useEffect(() => {
    if (title) {
      document.title = `${title} | Propvio`;
    }

    if (description) {
      let meta = document.querySelector('meta[name="description"]') as HTMLMetaElement | null;
      if (!meta) {
        meta = document.createElement('meta');
        meta.name = 'description';
        document.head.appendChild(meta);
      }
      meta.content = description;
    }

    // Restore default on unmount
    return () => {
      document.title = 'Propvio - AI-Powered Luxury Real Estate | Find Your Dream Home';
    };
  }, [title, description]);
}
