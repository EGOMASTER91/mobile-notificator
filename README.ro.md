# PrețRadar

[English](README.md) · [Română](README.ro.md)

Aplicație Android pentru căutarea produselor în magazine online din România, compararea ofertelor verificate și notificări locale la ieftiniri.

## Funcționalități

- Căutare în Altex, eMAG, ForIT, ITGalaxy și evoMAG. Compari.ro este opțional și dezactivat implicit.
- Potrivirea modelului și variantei cerute; produsele similare sunt excluse.
- Urmărirea produselor, praguri de preț și compararea ofertelor.
- Oferte **Hot** pe categorii, cu discount minim configurabil.
- Istoric local și alerte; notificări și când aplicația nu este deschisă.
- Verificări WorkManager la șase ore implicit, protecție la baterie scăzută și opțiune numai Wi-Fi. Android poate amâna verificările; după oprirea forțată trebuie redeschisă aplicația.
- Teme deschisă/întunecată și produse demonstrative fictive.

Accesul și ofertele depind de paginile publice și restricțiile magazinelor. Nu există ocolirea CAPTCHA, autentificare în magazine sau server al aplicației. Interfața actuală este în română; documentația și notele Release sunt în română și engleză.

## Instalare

Descarcă APK-ul semnat din [Releases](https://github.com/EGOMASTER91/mobile-notificator/releases). Este necesar Android 10+. Permite instalarea din browserul/managerul de fișiere folosit. Pe Android 13+, acordă permisiunea de notificare.

**Dacă ai versiunea debug:** versiunea publică folosește o cheie release separată și nu poate înlocui direct APK-ul semnat cu cheia de dezvoltare. Dezinstalarea șterge produsele, istoricul și setările locale. Păstrează instalarea debug dacă ai nevoie de aceste date; nu există momentan migrare prin export/import.

## Compilare și verificare

Folosește Android Studio sau JDK 21 și Android SDK 36. Configurează SDK-ul tău local; `local.properties` este exclus din Git.

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug :app:lintRelease :app:assembleRelease :app:writeSecurityDependencies
python3 tools/security_audit.py app/build/reports/security/dependencies.json
python3 tools/publication_audit.py
```

În Windows folosește `gradlew.bat`. Build-ul release este optimizat și nesemnat implicit. Semnează cu propria cheie privată, păstrată în afara Git. Nu publica cheia sau parolele.

GitHub Actions rulează teste unitare, lint, compilări, verificarea dependențelor în OSV, Gitleaks și controlul metadatelor de publicare. Un job separat verifică testele deterministe pe emulator Android. Testele live dependente de magazine sunt opționale și omise în CI.

## Confidențialitate și securitate

Produsele, istoricul și preferințele rămân în sandbox-ul aplicației Android. Termenii de căutare, adresele produselor și cererile de imagini merg direct la magazine/CDN; paginile JavaScript publice pot contacta alte servicii HTTPS. Nu există cont de aplicație, SDK de analiză sau date de plată stocate. Vezi [SECURITY.md](SECURITY.md) și [raportul de publicare](security/PUBLICATION-2026-10-10.md).

Repository-ul public are un istoric Git nou, cu identitate neutră. Include aplicația principală, testele și instrumentele de compilare/securitate, fără căi locale SDK, istoric privat vechi, secrete de semnare, emulator sau rezultate de compilare. Imaginile demonstrative sunt generate cu AI; metadatele neesențiale de proveniență au fost eliminate.

## Păstrarea unui proiect mic

După oprirea compilărilor și a emulatorului de test, rulează `./tools/clean-workspace.ps1` în PowerShell. Elimină compilările temporare și instrumentele locale de test, păstrând sursele, Git, fișierele locale de semnare și APK-ul release curent. Fișierele de compilare se regenerează la nevoie. Codul testelor nu este inclus în APK-ul release.
