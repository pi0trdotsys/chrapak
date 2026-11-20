import { createFileRoute } from '@tanstack/react-router';
import Events from '@/screens/events';
import { pageHead } from '@/lib/page-head';
export const Route = createFileRoute('/zdarzenia')({ head: () => pageHead('Nagrania chrapania', 'Przeglądaj epizody chrapania, odsłuchuj symulowane klipy i oznaczaj nagrania.'), component: Events });
