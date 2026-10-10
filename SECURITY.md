# Security / Securitate

## English

The distributed application is the `app` module (`ro.pretradar`). Dependency and secret scanners, negative URL/parser tests, Android lint and device regression tests are complementary checks; passing them is not a guarantee against every vulnerability. Android, WebView and third-party store services are outside the source audit.

Release APKs are non-debuggable, optimized and signed. Production signing keys and passwords are kept locally outside Git and are never uploaded as release assets. Development APKs permit process debugging and are not distributed in public releases.

The app stores products, history and alerts locally. It does not collect store credentials or payments. Network traffic uses HTTPS; file/content URLs and unsafe image URLs are rejected. WebView native bridges, debugging, mixed content, geolocation, popups and third-party cookies are disabled. Android backup and device-transfer rules exclude app data. Root access or a compromised device can defeat the app sandbox; the local database has no additional application-level encryption.

Use GitHub's private vulnerability reporting for sensitive findings. Do not include personal data, real customer records or secrets in public issues. Verify APK SHA-256 and the public signing certificate in the release before installing.

See [publication review](security/PUBLICATION-2026-10-10.md) for evidence, scope and limitations. Repeat the dependency audit after exporting resolved runtime versions with `:app:writeSecurityDependencies`.

## Română

Aplicația distribuită este modulul `app` (`ro.pretradar`). Scanările de dependențe și secrete, testele negative de URL/parser, Android lint și regresiile pe dispozitiv sunt verificări complementare; trecerea lor nu garantează absența tuturor vulnerabilităților. Android, WebView și serviciile magazinelor sunt în afara auditului surselor.

APK-urile release sunt optimizate, semnate și nu permit depanarea procesului. Cheia și parolele de producție rămân local, în afara Git; nu sunt încărcate în Release. APK-urile debug nu sunt distribuite în Release-urile publice.

Aplicația păstrează local produsele, istoricul și alertele. Nu colectează credențiale de magazin sau date de plată. Traficul este HTTPS; adresele file/content și imaginile cu scheme nesigure sunt respinse. WebView are dezactivate native bridges, debugging, mixed content, geolocalizare, popups și cookies terțe. Backup-ul Android și transferul datelor sunt excluse prin reguli explicite. Root sau compromiterea dispozitivului pot învinge sandbox-ul; baza locală nu are criptare suplimentară proprie.

Folosește raportarea privată GitHub pentru vulnerabilități sensibile. Nu include date personale, înregistrări reale sau secrete în issues publice. Verifică SHA-256 și certificatul public de semnare din Release înainte de instalare.

Vezi [raportul de publicare](security/PUBLICATION-2026-10-10.md) pentru dovezi și limite. Repetă scanarea dependențelor după exportarea versiunilor efective cu `:app:writeSecurityDependencies`.
