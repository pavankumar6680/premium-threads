import { useEffect } from 'react';
import { useLocation } from 'react-router';

/** Scrolls to the top when the path changes (filter changes on the same page keep the position). */
export function ScrollToTop() {
  const { pathname } = useLocation();
  useEffect(() => {
    window.scrollTo({ top: 0 });
  }, [pathname]);
  return null;
}
