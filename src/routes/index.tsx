import { createFileRoute } from '@tanstack/react-router';
import Report from '@/screens/report';
import { pageHead } from '@/lib/page-head';
export const Route = createFileRoute('/')({ head: () => pageHead('Raport Twojej nocy', 'Poranny raport chrapania, faz snu, saturacji i tętna. Spokojnie poznaj swój sen z Chrapak.'), component: Report });
