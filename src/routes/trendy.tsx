import { createFileRoute } from '@tanstack/react-router';
import Trends from '@/screens/trends';
import { pageHead } from '@/lib/page-head';
export const Route = createFileRoute('/trendy')({ head: () => pageHead('Trendy snu', 'Porównuj chrapanie i saturację z ostatnich nocy. Obserwuj związek wieczornych nawyków ze snem.'), component: Trends });
