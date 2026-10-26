# Rollout-Paket 1.0.1 – 26.10.2026

## Ergebnis und Reichweite

C:\NeosColony war in der verfügbaren Umgebung nicht vorhanden. Keine produktive Server-/Clientinstallation, kein Serverstart/-stopp, kein Eingriff in andere Projekte. Gemäß Auftrag wurde stattdessen ein vollständiges privates ZIP mit unveränderter veröffentlichter JAR, bereits vorbereiteter und vom Nutzer bestätigter Vertrauensdatei, Installer und Anleitung erstellt. Das ZIP liegt ausschließlich im ignorierten private/ und wird nicht auf GitHub veröffentlicht.

Mod-Version bleibt 1.0.1. Keine Produktions-Java-/Mixin-/Metadatenänderung und kein unnötiger JAR-Neubuild. SHA-256 der verwendeten veröffentlichten JAR bleibt afe7944786348f93b57255f4512740afab5f504fd68aee9bffc57c8e15c9b2cd.

## Verteilweg

Der Installer prüft auf dem tatsächlich erreichbaren Zielrechner AutoModpack 4.0.6, vorhandene modpackHost=true / generateModpackOnStart=true und konkurrierende AutoRestart-JARs. Er ergänzt nur den exakten forceCopyFilesToStandardLocation-Pfad /config/neoscolony-autorestart-trusted.properties und die native, SHA-1-begrenzte 1.0.0-Migration in nonModpackFilesToDelete. Bestehende andere Regeln und Werte bleiben erhalten. Nur die JSON-Formatierung kann sich ändern.

Client-Payload:
- automodpack/host-modpack/main/mods/neos-colony-automodpack-autorestart-1.21.1-1.0.1.jar
- automodpack/host-modpack/main/config/neoscolony-autorestart-trusted.properties

Altversionen mit bekannten Bytes werden aus beiden aktiven Quellen (Hostinhalt und gegebenenfalls direkte mods) in eine Sicherung außerhalb des Serverbaums zurückgenommen. Unbekannte/umbenannte Versionen werden nicht blind entfernt. Die private Datei wird nicht in den aktiven Server-config-Ordner installiert.

AutoModpack erzeugt nach normalem Serverstart sein Manifest selbst. -Verify prüft JAR-Name, SHA-1, Größe, genau eine aktuelle Version, Konfigurationshash/forceCopy und die tatsächlich generierte Altversions-Migration. Fehlt bei einem erstmalig erzeugten Manifest die Löschregel, muss AutoModpack regulär erneut generieren: Im Original 4.0.6 werden diese Regeln aus serverConfig innerhalb des vorhandenen-Altmanifest-Pfades übernommen. Ein fehlender Eintrag wird nicht als fertiger Rollout gemeldet und das Manifest nicht künstlich bearbeitet.

## Tatsächlich ausgeführte Prüfungen

- 13 Installerprüfungen in temporärer Serverfixture: Dry-run ohne Mutation, verweigerte Installation ohne Stoppbestätigung, beide Altquellen bereinigt, neue JAR/Privatdatei bytegleich, bestehende Verteilregeln/sonstige Einstellungen erhalten, wiederholte Installation inklusive Zeitstempeln idempotent, fehlendes/veraltetes Manifest abgelehnt, korrekte Fixture akzeptiert, umbenannte Mod-ID ohne Veränderung abgelehnt.
- Echter unveränderter AutoModpack-4.0.6-Generator erstellt zunächst ein Altmanifest und danach den Versionswechsel. Sieben Assertions bestätigen ausschließlich 1.0.1, Konfigpfad, forceCopy trotz allgemeiner Editable-Regel, vorhandene hashgesicherte Löschregel und beendete Generator-Threads.
- Fünf getrennte isolierte Test-JVMs/Clientverzeichnisse, jeweils 13 Assertions (65 insgesamt): ein Profil bereits mit Konfiguration, vier ohne. Originale ModpackUtils.getFilesNotToCopy/correctFilesLocations kopieren die Datei korrekt nach Client-config; die ursprüngliche verwaltete Bereinigung entfernt 1.0.0 aus Cache und passender mods-Kopie. 1.0.1 bleibt erhalten.
- Originale Zertifikats-Callbacklogik: synthetischer Zertifikatsdatensatz und Test-ScreenService simulieren ausdrückliche Ablehnung/Zustimmung. Ablehnung bleibt ohne Vertrauen, erst Zustimmung schreibt das native known-host-Mapping, bekannte passende Zertifikate fragen nicht erneut. Gelieferte Konfiguration allein aktiviert keine Vertrauensfreigabe. Fremde Hosts werden nicht automatisch akzeptiert. Keine echte TLS-Verbindung oder Zustimmung eines wirklichen Spielers behauptet.
- Native Remote-Löschfunktion respektiert allowRemoteNonModpackDeletions=false; bei true entfernt sie nur exakt passende alte JAR-Inhalte. Abweichender Dateiinhalt bleibt erhalten.
- 166 bestehende Client-/Engine-/Integritäts-/GUI-/Distributions-Assertions erneut mit der unveränderten veröffentlichten JAR bestanden, inklusive echter synthetischer Windows-Prozessneustarts und kein dritter/unbewaffneter Start. Der frühere Test.3-Archivvergleich war beim Aufruf aus downloads nicht aktiviert; separat sind die elf unveränderten alten Produktionsklassen gegenüber 1.0.0 bytegleich geprüft.
- Frühere reale 1.0.1-Clientdialogtests und erfolgreiche produktive AutoRestart-1.0.0-Logs bleiben dokumentierte Vorbelege. Sie wurden nicht als neuer kompletter Produktiv-Download-/CurseForge-Zyklus ausgegeben.

## Noch auf dem echten Server erforderlich

Administrator entpackt das private Paket außerhalb des Serverbaums, führt Prüfmodus aus, stoppt Server normal, wendet -Apply -ServerStopped an, startet regulär und prüft -Verify. Erst danach kann die produktive Verteilung als eingerichtet gelten. Zuerst einen Spieler, anschließend die weiteren vier kontrollieren. Spielern ist keine manuelle Konfigkopie abverlangt; jeder muss sein erstes Vertrauen selbst bestätigen.

Bei absichtlich deaktivierter Remote-Löschfunktion und alter manuell außerhalb des verwalteten Modpacks installierter JAR darf keine automatische Entfernung gegen den Spielerwillen erzwungen werden. Das ist eine respektierte Grenze, keine stillschweigende Garantie für unbekannte Spielerzustände. Wer AutoRestart noch nie geladen hat, benötigt beim ersten Installationsdurchlauf seinen ursprünglichen ersten Neustartweg. Bei bereits laufender 1.0.0 übernimmt die vorhandene Neustartfunktion den Versionswechsel.

Private Vertrauenswerte, Paketdateien und known-host-Zustimmungen werden nicht versioniert oder öffentlich veröffentlicht.
