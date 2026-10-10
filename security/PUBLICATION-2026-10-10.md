# Publication review — 10 October 2026 / Verificare publicare — 10 octombrie 2026

## English

Scope: the main Android `app` module, resolved runtime dependencies, source/release publication metadata and local build configuration. The legacy prototype, private Git history, local SDK settings, development keys, emulators and test output are excluded from this public repository.

Controls verified in source: HTTPS merchant validation and redirect checks; bounded requests and store backoff; negative URL/image parsing tests; no permissive TLS manager or native WebView bridge; no file/content access, mixed content, geolocation or WebView debugging; Android backup/device-transfer exclusions; parameterized Room queries and immutable notification PendingIntents.

Public release 0.5.3 enables R8 code/resource shrinking. Gson-persisted model classes retain their schema. VCS metadata is excluded from the APK; demo PNG provenance chunks were removed without changing pixels. Production certificate subject identifies only PretRadar. Private signing material is excluded from source and release assets.

Local verification: 54 main-module unit tests passed; debug and optimized release builds completed. OSV checked 150 resolved Maven module/version records with zero advisories returned. Gitleaks found no secrets in the curated publication source. Device tests and the final signed APK checks are recorded in release validation. GitHub Actions independently repeats unit tests, Android lint, build, OSV, Gitleaks, neutral-history/metadata checks and deterministic Android device tests. The release is published after that workflow succeeds.

Evidence: [dependency audit](osv-audit.json), [official Gradle checksums](gradle-checksums.json) and validation assets in the release. Inspect the corresponding Actions run to distinguish a successful run from a pending/failed one. Live store tests are opt-in, excluded from deterministic CI, and do not certify continued store availability.

Limits: scanners and source review do not prove absence of all vulnerabilities or personal data. URL validation is structural, without DNS pinning. Android/WebView/store services are outside this audit; app data has no extra application-level encryption beyond the OS sandbox. APK signatures establish the signing key, not independent security certification. Signing secrets are not shared; Windows-encrypted password backup is bound to the local Windows profile and must be recovered securely before changing computers.

## Română

Domeniu verificat: modulul principal Android `app`, dependențele efective, metadatele surselor/APK-ului și configurația locală de compilare. Prototipul vechi, istoricul Git privat, configurația SDK, cheile de dezvoltare, emulatorul și rezultatele temporare sunt excluse din repository-ul public.

Protecții revizuite: validare HTTPS și redirecturi, cereri limitate și pauze între accesările magazinelor, teste negative pentru URL/imagini, fără TLS permisiv sau native WebView bridge, fără file/content access, mixed content, geolocalizare ori WebView debugging, excluderi backup/transfer Android, interogări Room parametrizate și PendingIntent imuabil pentru notificări.

Versiunea 0.5.3 activează optimizarea R8 a codului și resurselor, păstrând schema modelelor salvate prin Gson. APK-ul exclude metadatele VCS; imaginile demonstrative au metadatele de proveniență eliminate, cu pixelii păstrați. Certificatul de producție identifică doar PretRadar. Cheia și parola nu sunt publicate.

Verificări locale: 54 de teste unitare ale aplicației trecute, build-uri debug și release optimizat reușite; 150 de module/versiuni Maven scanate în OSV, fără advisories returnate; Gitleaks fără secrete în sursele curate. Testele Android și controalele APK-ului semnat sunt în validarea Release. GitHub Actions repetă independent testele, lint, compilarea, OSV, Gitleaks, verificarea istoricului neutru/metadatelor și testele Android deterministe. Release-ul este publicat după reușita workflow-ului.

Dovezi: [scanarea dependențelor](osv-audit.json), [checksum-uri Gradle](gradle-checksums.json) și fișierele de validare din Release. Consultă rularea Actions aferentă pentru starea reală. Testele live sunt opționale, excluse din CI determinist, și nu certifică disponibilitatea continuă a magazinelor.

Limite: scanările și revizuirea nu demonstrează absența tuturor vulnerabilităților sau datelor personale. URL-urile sunt validate structural, fără DNS pinning. Android/WebView/magazinele sunt în afara auditului; datele locale nu au criptare suplimentară proprie peste sandbox-ul Android. Semnătura confirmă cheia, nu certifică independent securitatea. Backup-ul parolei criptate prin Windows depinde de profilul Windows local și trebuie recuperat sigur înainte de schimbarea calculatorului.
