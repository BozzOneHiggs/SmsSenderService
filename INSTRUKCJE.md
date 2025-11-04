# Instrukcje: Jak naprawić błąd uwierzytelniania Firebase

Ten plik zawiera instrukcje, jak rozwiązać błąd "Błąd autoryzacji z Firebase. Sprawdź konfigurację aplikacji", który pojawia się na fizycznym telefonie.

## Przyczyna problemu

Firebase wymaga, aby odcisk palca SHA-1 certyfikatu Twojej aplikacji był dodany do konfiguracji projektu Firebase. Certyfikat deweloperski (używany podczas uruchamiania aplikacji z Android Studio) jest zazwyczaj dodawany automatycznie, dlatego aplikacja działa w emulatorze. Jednak dla wersji produkcyjnej (release) musisz ręcznie dodać odcisk palca SHA-1 wygenerowany z Twojego magazynu kluczy (keystore).

## Krok 1: Wygeneruj odcisk palca SHA-1

Musisz wygenerować odcisk palca SHA-1 z magazynu kluczy (pliku `.jks` lub `.keystore`), którego używasz do podpisywania swojej aplikacji w wersji produkcyjnej.

1.  **Otwórz terminal** (wiersz polecenia) w Android Studio (`View -> Tool Windows -> Terminal`) lub dowolny inny terminal na swoim komputerze.
2.  **Użyj następującego polecenia**, aby uzyskać odcisk palca SHA-1. Zastąp `sciezka/do/twojego/magazynu.jks` i `twoj_alias` odpowiednimi wartościami:

    ```bash
    keytool -list -v -keystore sciezka/do/twojego/magazynu.jks -alias twoj_alias
    ```

    *   `sciezka/do/twojego/magazynu.jks`: Ścieżka do pliku magazynu kluczy.
    *   `twoj_alias`: Alias klucza, którego używasz do podpisywania aplikacji.

3.  **Wprowadź hasło** do swojego magazynu kluczy, gdy zostaniesz o to poproszony.
4.  **Skopiuj wartość SHA-1**, która zostanie wyświetlona w terminalu. Wygląda ona mniej więcej tak: `DA:39:A3:EE:5E:6B:4B:0D:32:55:BF:EF:95:60:18:90:AF:D8:07:09`.

## Krok 2: Dodaj odcisk palca SHA-1 do Firebase

1.  **Przejdź do konsoli Firebase:** [https://console.firebase.google.com/](https://console.firebase.google.com/)
2.  **Wybierz swój projekt** (`sms-sender-app-b1f94`).
3.  **Kliknij ikonę zębatki** (Ustawienia projektu) w lewym górnym rogu, a następnie wybierz `Ustawienia projektu`.
4.  **Przewiń w dół do sekcji "Twoje aplikacje"** i wybierz swoją aplikację Android (`com.example.smssenderservice`).
5.  **Kliknij "Dodaj odcisk palca"** w sekcji "Odciski palców certyfikatu SHA".
6.  **Wklej skopiowany wcześniej odcisk palca SHA-1** i kliknij "Zapisz".

## Krok 3: Zaktualizuj plik `google-services.json` (opcjonalnie, ale zalecane)

Po dodaniu odcisku palca SHA-1 w konsoli Firebase, pobierz zaktualizowany plik `google-services.json` i zastąp nim istniejący plik w `app/`. To zapewni, że Twoja lokalna konfiguracja jest zsynchronizowana z Firebase.

1.  W ustawieniach projektu w konsoli Firebase, w sekcji "Twoje aplikacje", znajdź swoją aplikację Android.
2.  Kliknij przycisk `google-services.json`, aby pobrać najnowszą wersję pliku.
3.  Zastąp stary plik `app/google-services.json` nowo pobranym plikiem.

Po wykonaniu tych kroków, przebuduj swoją aplikację i zainstaluj ją na telefonie. Błąd uwierzytelniania powinien zniknąć.
