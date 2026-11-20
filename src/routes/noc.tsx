import { createFileRoute } from '@tanstack/react-router';
import Tonight from '@/screens/tonight';
import { pageHead } from '@/lib/page-head';
export const Route = createFileRoute('/noc')({ head: () => pageHead('Spokojnej nocy', 'Przygotuj się do snu i rozpocznij symulowany nasłuch chrapania.'), component: Tonight });
