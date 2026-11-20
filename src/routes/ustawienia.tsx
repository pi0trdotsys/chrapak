import { createFileRoute } from '@tanstack/react-router';
import Settings from '@/screens/settings';
import { pageHead } from '@/lib/page-head';
export const Route = createFileRoute('/ustawienia')({ head: () => pageHead('Ustawienia', 'Źródła danych RingConn i Mi Band, wykrywanie chrapania, pamięć i prywatność w Chrapak.'), component: Settings });
