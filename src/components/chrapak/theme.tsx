import { useEffect, useState } from "react";
import { Check } from "lucide-react";

export const themes = [
  { id: "polnoc", name: "Północ", note: "Granat i fiolet. Obecny wygląd." },
  { id: "zar", name: "Żar", note: "Ciepłe róże i brąz. Mniej niebieskiego światła w sypialni." },
  { id: "las", name: "Las", note: "Głęboka zieleń i szałwia. Spokojny, naturalny." },
  {
    id: "oled",
    name: "Czerń OLED",
    note: "Czysta czerń, chłodny akcent. Oszczędza baterię na ekranie OLED.",
  },
  { id: "dzien", name: "Dzień", note: "Jasny, papierowy. Do porannego raportu." },
] as const;

export type ThemeId = (typeof themes)[number]["id"];

const STORAGE_KEY = "chrapak-theme";

/** Inline script run before first paint, so the saved theme does not flash. */
export const themeInitScript = `try{var t=localStorage.getItem('${STORAGE_KEY}');if(t&&t!=='polnoc')document.documentElement.setAttribute('data-theme',t)}catch(e){}`;

function applyTheme(id: ThemeId) {
  if (id === "polnoc") document.documentElement.removeAttribute("data-theme");
  else document.documentElement.setAttribute("data-theme", id);
  try {
    localStorage.setItem(STORAGE_KEY, id);
  } catch {
    /* storage unavailable: theme still applies for this visit */
  }
}

export function ThemePicker() {
  const [active, setActive] = useState<ThemeId>("polnoc");
  useEffect(() => {
    const saved = document.documentElement.getAttribute("data-theme");
    const match = themes.find((t) => t.id === saved);
    if (match) setActive(match.id);
  }, []);
  const choose = (id: ThemeId) => {
    setActive(id);
    applyTheme(id);
  };
  return (
    <section className="settings-section" aria-labelledby="theme-heading">
      <h2 id="theme-heading">Wygląd</h2>
      <div className="theme-grid" role="radiogroup" aria-label="Motyw wyglądu">
        {themes.map((t) => (
          <button
            key={t.id}
            type="button"
            role="radio"
            aria-checked={active === t.id}
            data-theme={t.id}
            className="theme-card"
            onClick={() => choose(t.id)}
          >
            <span className="theme-preview" aria-hidden="true">
              <span className="tp-score" />
              <span className="tp-stages">
                <i />
                <i />
                <i />
                <i />
              </span>
              <span className="tp-bars">
                <i />
                <i />
                <i />
                <i />
                <i />
              </span>
              <span className="tp-dots">
                <i className="tp-primary" />
                <i className="tp-teal" />
                <i className="tp-amber" />
              </span>
            </span>
            <span className="theme-name">
              {t.name}
              {active === t.id && <Check size={15} aria-label="Wybrany" />}
            </span>
            <span className="theme-note">{t.note}</span>
          </button>
        ))}
      </div>
    </section>
  );
}
