import { createContext, useContext, useState, type ReactNode } from 'react';
import { latestNight, snoreEvents, nights, type SnoreEvent } from '@/data/mock';
function usePrototypeState() {
  const [listening, setListening] = useState(false);
  const [events, setEvents] = useState(snoreEvents);
  const [nightId, setNightId] = useState(latestNight.id);
  const [tags, setTags] = useState<Record<string, string[]>>(Object.fromEntries(nights.map(n => [n.id, n.tags])));
  const [schedule, setSchedule] = useState(false);
  const [scheduleTime, setScheduleTime] = useState('23:00');
  const editEvent = (id: string, patch: Partial<SnoreEvent>) => setEvents(old => old.map(e => e.id === id ? { ...e, ...patch } : e));
  const deleteEvent = (id: string) => setEvents(old => old.filter(e => e.id !== id));
  return { listening, setListening, events, setEvents, editEvent, deleteEvent, nightId, setNightId, tags, setTags, schedule, setSchedule, scheduleTime, setScheduleTime };
}
const Context = createContext<ReturnType<typeof usePrototypeState> | null>(null);
export function PrototypeProvider({ children }: { children: ReactNode }) { const value = usePrototypeState(); return <Context.Provider value={value}>{children}</Context.Provider>; }
export function usePrototype() { const value = useContext(Context); if (!value) throw new Error('PrototypeProvider required'); return value; }
