# Prüfung 1.0.1 – 26.10.2026

## Nachgewiesene Ursache

In den vorhandenen Update-Laufzeitlogs des Neos-Colony-AutoUpdate-Profils vom 26.10.2026 meldet GUI 0.1.2 für beide bisherigen Identitätsprofile:
serverHostMatch=false, serverPortMatch=true, modpackHostMatch=false, modpackPortMatch=false, fingerprintMatch=true.
Danach explizit ORIGINAL retained: private identity does not match sowie ORIGINAL retained: restart identity/manifest absent or mismatched.
Die unveränderte AutoRestart 1.0.0 meldet im selben Lauf anschließend UPDATE_FILES_VERIFIED, HELPER_READY und HELPER_COMMITTED. Der Neustart funktionierte; die frühere GUI-Adresszuordnung ließ dagegen die Originaldialoge stehen. AutoRestart 1.0.0 unterdrückt deren Anzeige während seiner Vorbereitung nicht.

Private Adressen, Zertifikatswerte, vollständige Logs und konfigurierte Identitätsdateien sind nicht Bestandteil des öffentlichen Repositorys.

## Änderung

Nur im AutoRestart-Projekt:
- TrustedServer.java: explizite lokale Fünferkonfiguration (Serverhost/-port, Modpackhost/-port, bekannter Zertifikatsfingerabdruck). Keine automatische Vertrauensübernahme, keine Wildcards oder DNS-Äquivalenz. Fehlende/unvollständige/falsche Konfiguration bewahrt Originalverhalten.
- TrustedDialogs.java: genau für diese Konfiguration erstes Zweiknopf-Fenster und direkte Delegation an das vorhandene ModpackUpdater.startUpdate(Set). Keine neue Downloadimplementierung. Der bei erfolgreichem Update bereits autorisierte RestartScreen wird vor Darstellung der Bedienelemente durch eine Fortschrittsmeldung ersetzt. Ohne Erfolgsticket keine Unterdrückung; bei gescheiterter Vorbereitung Rückkehr zur ursprünglichen manuellen Oberfläche.
- AutoRestartClient.java: Aufruf dieser UI-Adapter an den bestehenden Opening-Listenern, Fehler-Fallback sowie Versionslog.
- Metadaten, build.ps1, Tests und Dokumentation. Buildskript behandelt javac-Hinweise unter Windows PowerShell korrekt und prüft weiter den tatsächlichen Compiler-Exitcode.

RestartEngine.java und alle zugehörigen kompilierten Engine-Klassen, UpdateGate und UpdateFingerprint sind gegenüber dem bestätigten alten Stand bytegleich. Die bestehenden Mixins bleiben unverändert. Download, Hash-/Größenprüfung, Helfer, Startkontextprüfung, Commit-vor-Stop, Inhaltsmarker, normaler Exit und Fehler-/Abbruchregeln werden nicht ersetzt. Keine AutoModpack-, GUI-, Server-, Profil- oder anderen Projektdateien geändert.

## Tatsächlich ausgeführt

- Java-21-Produktionsbuild erfolgreich.
- 177 Assertions, darunter 16 neue genaue Vertrauensgrenzen, bestehende Integritäts-/Fehler-/Abbruchtests, echter synthetischer Windows-Prozessneustart genau einmal und kein Neustart bei normalem/unbewaffnetem Ende. Die Anzahl sind Assertions, keine 177 Ingame-Szenarien.
- Zwei echte Offline-Mixin-Transformationen gegen unveränderte AutoModpack-4.0.6-Bytecodes.
- Reale NeoForge-1.21.1-Clientlaufzeit mit Produktions-JAR 1.0.1, AutoModpack 4.0.6 und vorhandener GUI 0.1.2: erstes Fenster genau zwei Aktionen; ein Klick delegiert einmal; kein Anerkennungsschritt für gepinnte Verbindung. Für fremde Verbindung bleiben originales DangerScreen und voller Timer von 200 Ticks erhalten.
- Ohne abgeschlossenes Update bleibt ein manuelles RestartScreen erhalten. Mit synthetisch eingesetztem Erfolgsticket wird vor Darstellung ein bedienungsfreier Fortschrittsbildschirm eingesetzt. Absichtlich ungültiges Dateimanifest wird von der bestehenden Prüfung abgelehnt; manuelle Oberfläche wiederhergestellt und kein Minecraft-Neustart.
- Clientfixture ausschließlich im Projekt unter test-runtime/run; keine Serververbindung, kein echter Netzwerkdownload. Der Downloader-Einstieg wird von einem Test-Subclass gezählt. Das beweist UI-Laufzeit und Delegation, keinen neuen kompletten produktiven Download-/CurseForge-Zyklus.
- Bereits vorhandene echte produktive Logs belegen den alten unveränderten AutoRestart-Kern im erfolgreichen Updatezyklus. Nicht alle fünf Spieler oder deren Launcherumgebungen neu getestet.
- SHA-256 des unveränderten RestartEngine-Quelltexts: 692059156a90c669d469dbde2a58259edd9b88926b8a1b064783f28b89334c02.

## Konfiguration und Einführung

Die neue öffentliche JAR enthält keine privaten Zielwerte. Vor Einsatz bei jedem Client genau eine administrativ geprüfte Datei config/neoscolony-autorestart-trusted.properties bereitstellen, siehe trusted-server.example.properties. Sie kann über den vorhandenen AutoModpack-Konfigurationsabgleich verteilt werden; vorhandene Serverregeln wurden nicht geändert. Eine aus aktueller gespeicherter Verbindung abgeleitete, gegen den bereits vorhandenen GUI-Zertifikatspin geprüfte lokale Vorlage liegt ausschließlich im ignorierten private/; sie ist nicht installiert und wird nicht veröffentlicht.

Ohne diese Datei werden Sicherheitsdialoge ausdrücklich beibehalten. Bei Adress-, Port- oder Zertifikatswechsel ist die Konfiguration bewusst neu zu prüfen.

## Ver�ffentlichung verifiziert

Quellcommit eafc8e00d24ea751a284e4acf22ce5d40dc53507; Downloadcommit f48d4ebf7356be9336e6679e8d80f9f98ceb8773. JAR unter downloads/1.0.1/neos-colony-automodpack-autorestart-1.21.1-1.0.1.jar und Release v1.0.1 ver�ffentlicht. Beide ver�ffentlichten Dateien erneut abgerufen und bytegleich mit dem gepr�ften Produktionsartefakt best�tigt: 44548 Bytes, SHA-256 afe7944786348f93b57255f4512740afab5f504fd68aee9bffc57c8e15c9b2cd. Exakter master-Dateipfad und GitHub-html_url gepr�ft; keine interaktive Browserpr�fung durchgef�hrt.
