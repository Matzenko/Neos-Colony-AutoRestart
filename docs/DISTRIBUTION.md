# AutoModpack-4.0.6-Verteilung

## Empfohlener Weg: separater Hostinhalt

1. Release-JAR und SHA-256-Datei aus dem GitHub-Release v1.0.0 beziehen. Der Administrator erledigt dies einmal; Spieler brauchen keinen GitHub-Download.
2. Änderungen erst im freigegebenen Wartungsfenster vornehmen. Den Server nach eurem üblichen Verfahren kontrolliert stoppen; diese Anleitung startet oder verändert ihn nicht selbst.
3. JAR relativ zum Server-Arbeitsverzeichnis ablegen:

       automodpack/host-modpack/main/mods/neos-colony-automodpack-autorestart-1.21.1-1.0.0.jar

4. Dort nur eine AutoRestart-Version führen. Alte Versionen dieses Projekts aus dem verteilten Hostinhalt entfernen; keine anderen Mods verändern. Die Original-AutoModpack-JAR im Server-Mods-Ordner bleibt unverändert.
5. Bestehende Einstellungen modpackHost=true, generateModpackOnStart=true und syncedFiles mit /mods/*.jar belassen. Für Dateien im separaten Hostinhalt braucht es keinen weiteren syncedFiles-Eintrag und keine forceCopy-Regel.
6. Beim freigegebenen regulären Serverstart erzeugt AutoModpack das Modpack neu. In automodpack/host-modpack/automodpack-content.json muss genau ein aktueller Eintrag /mods/neos-colony-automodpack-autorestart-1.21.1-1.0.0.jar mit Größe und Hash vorhanden sein. Nicht das Manifest von Hand bearbeiten.
7. Zunächst mit einem Spieler kontrollieren: normaler Beitrittsversuch, AutoModpack lädt die Erweiterung, erster erforderlicher Neustart manuell. Danach im Client-Log [NeosAutoRestart] ACTIVE Version 1.0.0 prüfen.
8. Beim nächsten tatsächlichen Update muss der bestätigte Neustartablauf genau einen automatischen Wiederstart bis zum Hauptmenü bewirken. Kein automatischer Serverbeitritt. Anschließend normal beenden: Spiel bleibt geschlossen.
9. Erst nach dieser Kontrolle die übrigen Spieler den normalen Abgleich ausführen lassen.

Dies ist eine vorbereitete Anleitung. Während der Erstellung des Releases wurde kein Produktivserver gestartet, gestoppt oder verändert.

## Warum kein zusätzlicher Server-Mod nötig ist

GlobalVariables definiert hostContentModpackDir als automodpack/host-modpack/main.
ModpackExecutor.init übergibt diesen Inhalt und die bestehenden syncedFiles getrennt an ModpackContent.
ModpackContent.create verarbeitet zuerst Files.walk des Hostinhalts und anschließend die passenden Server-Arbeitsverzeichnisdateien. generateContent formatiert Dateien relativ zum Hostinhalt und erzeugt daraus /mods/... .

Im Test wurde die echte AutoModpack-4.0.6-Klasse ModpackExecutor verwendet. Server-mods blieb leer; der veröffentlichte JAR-Inhalt wurde trotzdem als Mod mit neoscolony_autorestart erkannt und im Clientmanifest eingetragen. Minecraft- und eigene Mod-Einstiegsklassen waren im Test-ClassLoader gesperrt, ohne dass ein Ladeversuch erforderlich wurde.

Primärquellen:
- [GlobalVariables](https://github.com/Skidamek/AutoModpack/blob/v4.0.6/core/src/main/java/pl/skidam/automodpack_core/GlobalVariables.java)
- [ModpackExecutor](https://github.com/Skidamek/AutoModpack/blob/v4.0.6/core/src/main/java/pl/skidam/automodpack_core/modpack/ModpackExecutor.java)
- [ModpackContent](https://github.com/Skidamek/AutoModpack/blob/v4.0.6/core/src/main/java/pl/skidam/automodpack_core/modpack/ModpackContent.java)

## Spieler mit manuell installierten Testversionen

Ein Spieler darf nicht mehrere JARs mit derselben Mod-ID gleichzeitig laden. Für übliche durch AutoModpack verwaltete Versionswechsel übernimmt AutoModpack seine vorhandene Abgleich-/Duplikatbehandlung.

Für außerhalb des verwalteten Inhalts manuell installierte test.3-JARs kann der Administrator zusätzlich die vorhandene AutoModpack-V2-Funktion nonModpackFilesToDelete verwenden. Folgenden Eintrag mit bestehenden Einträgen zusammenführen, nicht die ganze Konfiguration ersetzen:

    "nonModpackFilesToDelete": {
      "/mods/neos-colony-automodpack-autorestart-1.21.1-0.1.0-test.3.jar": "df8f4e7dae2df6c207a7ac3d21dcb8b0cbe5add7"
    }

Der Hash bezeichnet exakt die getestete alte Projekt-JAR. AutoModpack prüft vor dem Entfernen den SHA-1-Inhalt. Es durchsucht gegebenenfalls auch den Elternordner nach demselben Inhalt. Es werden dadurch keine anderen Modinhalte autorisiert.

Das setzt allowRemoteNonModpackDeletions auf dem Client voraus (V2-Standard true). Diese Einstellung wurde bei den Spielern nicht geprüft. Ist sie absichtlich deaktiviert, nicht umgehen: Die alte manuell installierte Testkopie muss dann separat vom Spieler/Administrator bereinigt werden. Der neue normale AutoModpack-Download benötigt weiterhin keinen manuellen JAR-Download.

Diese optionale Migration wurde anhand des Originalquellcodes geprüft; sie wurde nicht auf dem Produktivserver oder einem echten Spielerprofil ausgeführt. Vor Freigabe konkrete alte Dateinamen/Hashes und eure vorhandenen Map-Einträge kontrollieren.

[Original-Migrationsfunktion](https://github.com/Skidamek/AutoModpack/blob/v4.0.6/loader/core/src/main/java/pl/skidam/automodpack_loader_core/client/ModpackUtils.java)

## Rücknahme und Grenzen

Bei Problemen zunächst neue Anmeldungen/Updates kontrollieren und die AutoRestart-JAR aus dem separaten Hostinhalt im Wartungsfenster zurücknehmen. Reguläre Modpack-Neugenerierung verwenden. Keine automatischen Dateilösch- oder Serversteuerungsskripte gehören zu diesem Projekt.

Die tatsächliche Serverkonfiguration war lokal nicht zugänglich. Verwendet wurden die vom Administrator bestätigten Einstellungen und die echte Originalbibliothek. Weitere Ausschluss-/Migrationsregeln, alle fünf individuellen Clientkonfigurationen und ein vollständiger Produktiv-Ende-zu-Ende-Verteilungstest sind vor dem breiten Rollout zu kontrollieren.
