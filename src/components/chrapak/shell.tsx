import { Link, useLocation } from '@tanstack/react-router';
import { Moon, ChartNoAxesColumnIncreasing, SlidersHorizontal, AudioLines, Sun, ShieldCheck } from 'lucide-react';
import type { ReactNode } from 'react';
import { usePrototype } from './state';
const tabs = [{ to: '/noc', label: 'Noc', icon: Moon }, { to: '/', label: 'Raport', icon: Sun }, { to: '/trendy', label: 'Trendy', icon: ChartNoAxesColumnIncreasing }, { to: '/ustawienia', label: 'Ustawienia', icon: SlidersHorizontal }] as const;
export function Shell({ children }: { children: ReactNode }) {
  const { pathname } = useLocation(); const { listening } = usePrototype();
  return <div className="app-shell"><header className="brand-header"><Link to="/" className="brand"><span className="brand-mark"><AudioLines size={23} /></span>chrapak<span className="brand-dot">.</span></Link><span className="header-status"><span className={listening ? 'status-dot listening-dot' : 'status-dot'} />{listening ? 'Nasłuch aktywny' : 'Spokojnej nocy'}</span></header><main className="main-content">{children}</main><footer className="desktop-privacy"><ShieldCheck size={14} /> Twoje dane zostają na Twoim telefonie</footer><nav className="bottom-nav" aria-label="Nawigacja główna">{tabs.map(({ to, label, icon: Icon }) => <Link key={to} to={to} className={`nav-item ${pathname === to || (to === '/' && pathname === '/zdarzenia') ? 'active' : ''}`}><Icon size={21} strokeWidth={1.7} /><span>{label}</span>{to === '/noc' && listening && <i className="nav-live" />}</Link>)}</nav></div>;
}
export function PageTitle({ eyebrow, title, children }: { eyebrow: string; title: string; children?: ReactNode }) { return <div className="page-heading"><div><p className="eyebrow">{eyebrow}</p><h1>{title}</h1></div>{children}</div>; }
