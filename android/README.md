# Chrapak na Androida

Aplikacja nagrywa chrapanie przez całą noc w usłudze pierwszoplanowej i zapisuje tylko krótkie klipy ze zdarzeniami. Na razie to szkielet z działającym serwisem nagrywającym i detekcją. Ekrany z makiety (`../src`) zostaną przepisane na Compose później.

## Budowanie i testy

Potrzebujesz JDK 17+ i Android SDK (platforma 37). Utwórz `local.properties` z linią `sdk.dir=<ścieżka do SDK>`.

```sh
./gradlew :app:testDebugUnitTest   # testy detekcji na sztucznym sygnale
./gradlew :app:assembleDebug       # app/build/outputs/apk/debug/app-debug.apk
```

## Jak działa detekcja

Priorytetem jest mało energii, bo telefon pracuje całą noc.

| Etap | Plik | Kiedy działa |
|---|---|---|
| Bramka energii | [`EnergyGate.kt`](app/src/main/kotlin/app/chrapak/detection/EnergyGate.kt) | Każda ramka 32 ms. Filtr pasmowy 100–2000 Hz, adaptacyjny próg względem ciszy w pokoju, wymóg przewagi energii poniżej 600 Hz. Kilka mnożeń na próbkę, bez modelu. |
| Segmentacja | [`SnoreSegmenter.kt`](app/src/main/kotlin/app/chrapak/detection/SnoreSegmenter.kt) | Łączy aktywność rozdzieloną przerwami krótszymi niż 2 s w jeden epizod i odrzuca epizody krótsze niż 3 s. |
| Klasyfikator | `SnoreClassifier` w [`DetectionPipeline.kt`](app/src/main/kotlin/app/chrapak/detection/DetectionPipeline.kt) | Tylko gdy epizod jest otwarty, co ok. 1 s. Test pilnuje, że w ciszy nie wywołuje się ani razu. |
| Bufor kołowy | [`PreRollBuffer.kt`](app/src/main/kotlin/app/chrapak/detection/PreRollBuffer.kt) | 5 s dźwięku przed zdarzeniem w pamięci. Na dysk trafiają tylko klipy ze zdarzeniami. |

Serwis ([`ListeningService.kt`](app/src/main/kotlin/app/chrapak/service/ListeningService.kt)) czyta mikrofon po 0,5 s, wybiera źródło `UNPROCESSED` (bez automatycznej regulacji głośności), jeśli telefon je wspiera, a zapis klipów i bazy robi na osobnej korutynie, żeby dysk nie opóźniał odczytu.

## Dane

Room ([`Entities.kt`](app/src/main/kotlin/app/chrapak/data/Entities.kt)) odpowiada typom `Night` i `SnoreEvent` z makiety. Schemat jest eksportowany do `app/schemas/`. Klipy to pliki WAV w `files/clips/<noc>/`.

## Czego jeszcze nie ma

- **Klasyfikator YAMNet.** Do czasu jego podłączenia zdarzenia przechodzą na samej bramce i mają `confidence = 0.5` (`GATE_ONLY_CONFIDENCE`).
- **Przeliczenie dBFS na dB** pokazywane w makiecie. Wymaga kalibracji mikrofonu danego telefonu. Na razie zapisywane są dBFS pasma 100–2000 Hz.
- **Health Connect** (fazy snu, SpO₂, tętno z pierścienia i opaski) oraz korelacja z zdarzeniami.
- **Ekrany Compose** poza minimalnym ekranem „Noc".
- **Sprawdzenie na prawdziwym telefonie.** Kod się kompiluje i testy detekcji przechodzą, ale serwis, mikrofon i zachowanie HyperOS (autostart, ograniczenia baterii) nie były jeszcze uruchamiane na urządzeniu.
