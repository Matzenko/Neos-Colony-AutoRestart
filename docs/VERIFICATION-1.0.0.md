# Releaseprüfung 1.0.0 – 17.10.2026

## Ergebnis

Release-JAR: neos-colony-automodpack-autorestart-1.21.1-1.0.0.jar
Größe: 36.999 Bytes
SHA-256: f19483e231d46d2769a043492c9b192aa27a0d454f5fb09f65683a86fb539ca1

Java-21-Release-Build erfolgreich. 162 Assertions im erweiterten Client-Mod-Testlauf, zwei tatsächliche Offline-Mixin-Transformationen sowie 68 historische Prototyp-Assertions (44 + 10 + 14) und vollständige JNA-Archivprüfung bestanden. Das sind keine 230 Ingame-Szenarien.

Der Benutzer bestätigte zuvor test.3 im echten CurseForge-/AutoModpack-Ablauf: automatische kontrollierte Schließung, dieselbe Instanz startet ohne Benutzerinteraktion neu, normales späteres Beenden bleibt geschlossen. Die neue Versionsanzeige allein wurde nicht erneut Ingame getestet.

## Laufzeitänderungen

Keine Änderung an der funktionierenden Neustartlogik. Alle 12 Produktionsklassen wurden mit dem aufbewahrten test.3-Artefakt verglichen; in AutoRestartClient wurde ausschließlich der Logtext der Versionsanzeige normalisiert. Die übrigen kompilierten Klassen sind bytegleich.
Metadaten und Artefaktname wurden auf 1.0.0 gesetzt; MIT-Lizenz liegt der JAR bei. test.3 blieb unverändert erhalten.

Mod-ID neoscolony_autorestart; Client-Pflichtabhängigkeit automodpack_mod exakt 4.0.6, NeoForge 21.1.251 und Minecraft 1.21.1. JAR enthält ausschließlich eigene Produktionsklassen, Modmetadaten, zwei Client-Mixins und Lizenz; keine Test-, Prototyp-, Minecraft-, AutoModpack- oder JNA-Klassen.

## Prüfungen

- Abschluss aus startUpdate plus ausdrückliche ReLauncher-/ScreenImpl-Neustartanforderung; keine Freigabe durch bloße Beendigung oder fremde Startup-Anforderung.
- Original-/GUI-Ersatzscreen über identisches NeoForge-Opening-Ereignis, Einmalverarbeitung, Abbruch bei Bildschirm-/Weltwechsel.
- Datei-/Metadatenintegrität, dynamischer gameDir-Abgleich, native Umgebung, Arbeitsverzeichnis und verifizierte Java-21-JLI-Argumente.
- Genau ein synthetischer Neustart nach vollständigem Prozessende; alle effektiven Argumente (außer eigener Marker) und vollständiger nativer Umgebungsblock verglichen.
- @Quelldatei nach Erfassung entfernt, JVM-Option trotzdem erhalten.
- Abbruch/EOF, veränderte Startdatei, Helfer ohne Mainklasse, fehlgeschlagener CreateProcess-Aufruf und keine Wiederholung.
- Inhaltsmarker blockiert dasselbe Update im Nachfolger; kein automatischer Server-/Welt-/Realms-Beitritt.
- Original-GUI- und Minecraft-Bytecode sowie beide Mixin-Transformationen geprüft.

## Dedizierte Serverseite

Die tatsächlich installierte FML-4.0.44-Implementierung aus NeoForge 21.1.251 wurde mit den aus unserer JAR gelesenen Annotationen geprüft: Der echte loadMod-Seitenfilter lehnt unseren Einstieg auf DEDICATED_SERVER ab und nimmt ihn auf CLIENT an. Die Manipulation der Test-Seitenvariable fand ausschließlich im isolierten Test-JVM statt.
Keine gemeinsamen/Server-Mixins oder Discovery-Services in der Release-JAR.

Ein vollständiger dedizierter NeoForge-Server wurde nicht gestartet. Die bevorzugte Verteilung setzt keine Ausführung der Erweiterung im Server-Mods-Ordner voraus.

## AutoModpack-Verteilung

Zehn Assertions gegen die unveränderte AutoModpack-4.0.6-Originalbibliothek bestätigen:
- separater Hostpfad automodpack/host-modpack/main,
- Mod-ID-Erkennung,
- erfolgreiche Manifestgenerierung mit /mods/<JAR>,
- leerer Server-Mods-Ordner vor und nach Generierung,
- kein Laden von Minecraft-, AutoRestart- oder GUI-Einstiegsklassen,
- sauberes Beenden der Test-Generator-Threads.

Original-AutoModpack SHA-256:
e76570a113ac9cd7fecc85fc6d2323ef2e04318e4b7615d34519843adfe838f5

Keine Installation, kein Produktivserver-Neustart und keine Änderungen an bestehenden Profilen oder fremden Mods wurden ausgeführt. Die produktive Konfiguration war nicht erreichbar; die Anleitung basiert auf bestätigten Administratorangaben und dem getesteten Originalgenerator. [Verteilungsanleitung](DISTRIBUTION.md).

## Grenzen

Kein universeller Nachweis für sämtliche Windows-/CurseForge-/Java-Kombinationen, weitere Spieler oder erneuerte Online-Anmeldung. Interne JLI-Schnittstelle bewusst nur Windows x64 / Java 21; unsicherer Kontext führt zu manueller Bedienung. Zugangsdaten werden übernommen, nicht erneuert. Keine Garantie, dass ein Betriebssystem-/Launcherfehler nach Prozessende automatisch repariert wird; es gibt genau einen Versuch.

Die historischen Prototyptests liegen als eigene Quellen bei; sie sind nicht Bestandteil der Mod-JAR. Referenzbibliotheken und maschinenspezifische Build-/Testlogs werden nicht veröffentlicht.
