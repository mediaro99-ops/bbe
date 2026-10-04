# ProfitRide 1.2.0 AUTO — Creat de Plesia Razvan

Versiunea aceasta adaugă fluxul automat cerut pentru Bolt + Waze.

## Ce face automat

1. Când apare o ofertă Bolt, citește prețul și cele două segmente (până la client + cursa), le adună și calculează RON/km, RON/oră și profitul.
2. Când apeși **Refuză**, cardul se resetează imediat la 0 și așteaptă următoarea ofertă.
3. Când apeși **Acceptă**, ProfitRide intră automat pe **Pauză — cursă în desfășurare**.
4. Când Waze devine aplicația din față, cardul ProfitRide se ascunde complet ca să vezi navigația.
5. Când revii în Bolt Driver, cardul ProfitRide reapare. Dacă ești încă în cursă, arată Pauză.
6. Când termini cursa, se resetează la 0. Dacă butonul de finalizare nu este expus Android-ului, următoarea ofertă detectată scoate automat ProfitRide din pauză și pornește calculul noii curse.
7. Există și fallback OCR: dacă oferta dispare și clickul Refuză/Acceptă nu a putut fi citit, după aproximativ 2,2 secunde starea se actualizează automat.

## Permisiune nouă — o singură dată

Pentru detectarea sigură a aplicației Bolt/Waze și a apăsărilor Acceptă/Refuză:

- deschide ProfitRide;
- apasă **ACTIVEAZĂ MODUL AUTOMAT**;
- în Accesibilitate activează serviciul **ProfitRide**;
- revino în ProfitRide;
- apasă **START ANALIZĂ** și acceptă captura ecranului.

Serviciul de Accesibilitate este folosit pentru starea aplicației și acțiunile Acceptă/Refuză/Finalizare. Nu apasă automat butoane în Bolt.

## Build Codemagic

Repository-ul trebuie să conțină la rădăcină:

- `app/`
- `build.gradle`
- `settings.gradle`
- `gradle.properties`
- `codemagic.yaml`

În Codemagic pornește workflow-ul **ProfitRide APK**. APK-ul apare ca `ProfitRide.apk` la Artifacts.

## Actualizare peste repository-ul existent

Dacă repository-ul tău compilează deja versiunea 1.1, este suficient să înlocuiești folderul `app/` cu folderul `app/` din această versiune. Poți înlocui și fișierele root din ZIP pentru a păstra exact configurația furnizată.

Bolt Driver Android folosește pachetul `ee.mtakso.driver`; Waze folosește `com.waze`.
