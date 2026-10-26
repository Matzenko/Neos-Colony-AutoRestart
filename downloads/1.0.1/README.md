# Neos Colony AutoRestart 1.0.1

Quellcommit: eafc8e00d24ea751a284e4acf22ce5d40dc53507
JAR: neos-colony-automodpack-autorestart-1.21.1-1.0.1.jar
SHA-256: afe7944786348f93b57255f4512740afab5f504fd68aee9bffc57c8e15c9b2cd
44548 Bytes; Minecraft 1.21.1, NeoForge 21.1.251, AutoModpack 4.0.6, Windows x64 / Java 21.

Korrigierte Dialogübergänge ausschließlich für exakt konfigurierte private Server.
Neustartkern, Erfolgsgate, Integritätsprüfung und bisherige Mixins unverändert.
177 Assertions, zwei Mixin-Transformationen, synthetische Prozessneustarttests sowie
echter Minecraft-Client-Dialogtest einschließlich fremdem Server mit erhaltenem
200-Tick-Bestätigungstimer bestanden. Kein neuer kompletter Produktiv-Downloadzyklus.

**Einmalige Einrichtung erforderlich:** config/neoscolony-autorestart-trusted.properties
mit geprüftem Serverhost/-port, Modpackhost/-port und bereits bekanntem Zertifikatspin
auf jedem Client bereitstellen. Ohne diese Datei bleiben Original-Sicherheitsdialoge aktiv.
Private Verbindungswerte sind nicht in der öffentlichen JAR oder diesem Repository.
Eine lokal vorbereitete private Vorlage wurde nicht installiert.

[Einrichtung](../../README.md), [Konfigurationsbeispiel](../../docs/trusted-server.example.properties),
[Prüfbericht und Grenzen](../../docs/VERIFICATION-1.0.1.md).
Dies ist die neueste JAR dieser Version; 1.0.0 und bestehende Assets bleiben erhalten.
