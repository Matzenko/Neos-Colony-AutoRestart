# Neos Colony AutoRestart 1.0.1

**[JAR herunterladen](https://github.com/Matzenko/Neos-Colony-AutoRestart/blob/master/downloads/1.0.1/neos-colony-automodpack-autorestart-1.21.1-1.0.1.jar)**

1.0.1 korrigiert ausschließlich die Dialogübergänge für eine explizit konfigurierte private Serververbindung. Die bisherigen GUI-Pins passen laut echten Laufzeitlogs nicht mehr zu den Verbindungsadressen/Modpack-Ports; der Zertifikatsfingerabdruck stimmt weiterhin. Der vorhandene Neustartkern bleibt unverändert.

## Automatische Einrichtung für alle Spieler

Das private Serverpaket verteilt JAR 1.0.1 und die vorhandene Vertrauensdatei über den separaten AutoModpack-Hostinhalt. Spieler müssen keine Konfigurationsdateien kopieren; nur die erste eigene Server-Vertrauensentscheidung bleibt bestehen. Die öffentliche JAR enthält weiterhin keine privaten Zielwerte.

Der Administrator verwendet das lokal vorbereitete private Paket und [deployment/SERVERPAKET.md](deployment/SERVERPAKET.md). Paketvorbereitung aus vorhandener geprüfter private/neoscolony-autorestart-trusted.properties: deployment/New-PrivateServerPackage.ps1. Niemals das erzeugte private ZIP auf GitHub hochladen. [Prüfbericht und verbleibende Serverinstallation](docs/ROLLOUT-1.0.1.md).


**Alternative einmalige manuelle Einrichtung:** [docs/trusted-server.example.properties](docs/trusted-server.example.properties) mit den fünf administrativ geprüften Zielwerten als `config/neoscolony-autorestart-trusted.properties` in jedem Client bereitstellen. Keine privaten Werte ins öffentliche Git übernehmen. Die vorbereitete lokale Datei unter `private/` wird nicht installiert und nicht veröffentlicht. Ohne gültige Konfiguration bleiben die bisherigen Sicherheitsdialoge bestehen. Beim Update einer laufenden Instanz kann die neue Logik erst nach Laden der neuen JAR greifen.

Danach bleibt das erste Fenster mit „JARs herunterladen“ und „Zurück“ erhalten. Der Klick delegiert direkt an den bisherigen Download. Nach bestätigtem Erfolg erscheint nur „Minecraft wird neu gestartet ...“, während die vorhandene Integritäts-/Helferprüfung und der bestehende AutoRestart laufen. Bei gescheiterter Vorbereitung bleibt der manuelle Fallback erhalten. Fremde Server und normale Beenden-Vorgänge behalten ihr Verhalten.

[Prüfung 1.0.1 und Grenzen](docs/VERIFICATION-1.0.1.md). Die folgenden Angaben beschreiben den weiterhin gültigen unveränderten Kern und dessen bisherige Veröffentlichung.


# Neos Colony AutoRestart 1.0.0

Client-Erweiterung für **Minecraft 1.21.1**, **NeoForge 21.1.251** und **AutoModpack 4.0.6** unter **Windows x64 / Java 21**.

Nach einem abgeschlossenen AutoModpack-Update mit ausdrücklicher Neustartanforderung wird Minecraft kontrolliert geschlossen. Ein separater Helfer wartet auf das vollständige Prozessende und startet dieselbe Installation einmal mit dem geprüften ursprünglichen Startkontext. Kein automatischer Serverbeitritt und kein Neustart beim normalen Beenden.

[Version 1.0.0 herunterladen](https://github.com/Matzenko/Neos-Colony-AutoRestart/releases/tag/v1.0.0)

## Automatische Verteilung über AutoModpack

Die Release-JAR gehört vorzugsweise **nicht in den Mods-Ordner des dedizierten Servers**, sondern hierhin, relativ zu dessen Arbeitsverzeichnis:

    automodpack/host-modpack/main/mods/neos-colony-automodpack-autorestart-1.21.1-1.0.0.jar

AutoModpack 4.0.6 scannt diesen separaten Host-Ordner zusätzlich zu syncedFiles und erzeugt daraus den Clientpfad /mods/… . Das wurde mit der unveränderten Originalbibliothek isoliert getestet, ohne die Erweiterung serverseitig zu laden.

Bei modpackHost=true und generateModpackOnStart=true ist für diesen Ordner kein zusätzlicher syncedFiles-Eintrag erforderlich. Ein vorhandenes /mods/*.jar kann unverändert bleiben. Die konkrete produktive Konfiguration wurde vom Administrator bestätigt; sie wurde nicht aus einem erreichbaren Produktivserver ausgelesen.

Spieler laden die Mod beim normalen AutoModpack-Abgleich automatisch. Beim allerersten Download läuft sie noch nicht im bestehenden Prozess: **den ersten erforderlichen Neustart manuell einplanen**. Ab dem folgenden Start ist sie aktiv.

Vollständige Anleitung einschließlich Manifestkontrolle, Migration alter Test-JARs und Rücknahme: [docs/DISTRIBUTION.md](docs/DISTRIBUTION.md).

## Verhalten und Kompatibilität

- Mod-ID: neoscolony_autorestart.
- Pflichtabhängigkeit auf der Clientseite: **automodpack_mod 4.0.6**; das ist die tatsächliche ID der eingebundenen AutoModpack-Mod.
- Erfolgserkennung prüft den AutoModpack-startUpdate-/ReLauncher-/ScreenImpl-Aufrufweg und ordnet den konkreten RestartScreen seinem Update zu.
- Zwei beobachtende NeoForge-Opening-Listener übernehmen einen im selben Ereignis ersetzten Bildschirm. Damit bleibt die geprüfte Neos Colony GUI 0.1.2 kompatibel.
- Metadaten, Größen und SHA-1 der Update-Dateien sowie Startdateien werden geprüft. Bei Versionswechsel von Minecraft/NeoForge oder unvollständigem Startkontext bleibt der manuelle Neustart.
- Ein Inhaltsfingerabdruck blockiert denselben automatischen Neustart im Nachfolger. Keine Wiederholung nach Helferfehlern.
- AutoModpack-Download-/Synchronisierungsfunktionen und andere Mods werden nicht verändert.

Die Mod besitzt nur einen mit Dist.CLIENT deklarierten Einstieg und ausschließlich clientseitige Mixins. Der tatsächliche NeoForge-Seitenfilter wurde separat geprüft. Ein vollständiger dedizierter Serverstart mit dieser JAR im Server-Mods-Ordner wurde nicht durchgeführt und wird für die empfohlene Verteilung nicht benötigt.

## Voraussetzungen und Grenzen

Keine festgelegten Profilnamen oder Benutzerpfade. Java-EXE, Arbeitsverzeichnis, Umgebungsblock und effektive Java-21-JLI-Argumente stammen aus dem laufenden Prozess. Das Verfahren ist kein dokumentierter CurseForge-API-Aufruf und erzeugt keine neue authentifizierte Launcher-Sitzung.

Interne JLI-Schnittstelle nur für Windows x64 / Java 21. Nicht unterstützte Startkontexte werden sicher verweigert. Zusätzliche Java-Agenten, JVM-Injektion über Umgebungsoptionen und direkte Server-/Welt-/Realms-Startargumente werden abgelehnt. Zugangsdaten bleiben in RAM/anonymer Pipe; keine vollständigen Startargumente oder Tokens im Log. Anmeldung wird übernommen, nicht erneuert.

Der Administrator bestätigte den erfolgreichen test.3-Praxislauf: automatisches Schließen und Wiederstart derselben CurseForge-Instanz nach Update; normales späteres Beenden bleibt geschlossen. Die Release-Laufzeitklassen wurden dagegen verglichen; lediglich der Versionsanzeigetext unterscheidet sich. Andere Spieler/Launcherumgebungen und die Online-Sitzung müssen jeweils praktisch geprüft werden.

## Build und Tests

Vorhandenes JDK 21 und eine Installation mit den exakten Laufzeitbibliotheken erforderlich. Keine automatischen Bibliotheksdownloads, keine externen Build-Caches:

    .\build.ps1 -LibrariesDirectory '<Installation>\libraries' -VersionsDirectory '<Installation>\versions' -AutoModpackJar '<vorhandene AutoModpack-4.0.6-JAR>' -GuiJar '<vorhandene Neos-Colony-GUI-JAR>' -JavaHome '<JDK 21>'

GuiJar ist optional und ermöglicht die zusätzlichen Kompatibilitätsprüfungen der tatsächlichen GUI. Ein lokal vorhandenes test.3-Artefakt ermöglicht zusätzlich den Laufzeitklassenvergleich. Es wird weder mitgeliefert noch für einen frischen Klon vorausgesetzt.

Historische Tests des unabhängig geprüften Neustartprototyps:

    .\prototype\build.ps1 -JnaJar '<vorhandene jna-5.14.0.jar>' -UnionJar '<vorhandene securejarhandler-3.0.8.jar>'

Alle Testprozesse und Ausgaben bleiben in diesem Repository. Fremde Profile und Server werden nicht geändert. Ausgaben: dist/, out/, temp/, test-results/, reference/ sowie die entsprechenden Prototyp-Verzeichnisse; sie sind nicht versioniert.

[Prüfbericht zu 1.0.0](docs/VERIFICATION-1.0.0.md). Lizenz: MIT. AutoModpack und dessen Referenzbibliotheken werden nicht gebündelt.
