# Kara Delik Asistan

Android için, ekranda sürüklenebilen animasyonlu bir kara delik balonu ve Türkçe sesli sohbet örneği.

## Neler yapar?

- Balon, kullanıcı başlattığında kayan pencere olarak açık kalır; bildirimdeki **Durdur** eylemiyle kapanır.
- Mikrofon sürekli açık değildir. Ses tanıma yalnızca balona dokununca başlar.
- “Google'ı aç” Google uygulamasını, yoksa web sayfasını açar.
- “Ahmet'e şu mesajı gönder: Yarın görüşelim” biçimindeki açık komut, rehberde tek bir eşleşme bulur ve SMS izni verilmişse SMS'i gönderir.
- WhatsApp için numara ve metin içeren taslak açılır; Telegram için paylaşım ekranı açılır. Her ikisinde de gönderme kullanıcı onayı gerektirir.
- Diğer sohbet soruları `openai-fast` modeliyle Pollinations'ın herkese açık uç noktasına gönderilir ve Türkçe sesle okunur. API anahtarı gerekmez; internet ve hizmetin kullanılabilir olması gerekir.

## Gizlilik ve Android izinleri

Uygulama balon için “diğer uygulamaların üzerinde gösterme”, ses tanıma için mikrofon, kişi adıyla SMS için rehber ve otomatik SMS için SMS izni ister. Ses tanımayı Android'in seçili tanıma hizmeti yapar; hizmet sağlayıcısına göre ağ kullanabilir. Sohbet yanıtı almak için ses tanımanın metin çıktısı Pollinations hizmetine gönderilir. SMS komutları yapay zekâ hizmetine gönderilmeden cihazda ayrıştırılır. Uygulama sürekli mikrofon dinlemez.

SMS izni olmayan, rehber eşleşmesi bulunamayan veya komutu kesin ayrıştıramayan durumlarda SMS otomatik gönderilmez. Bu ilk sürüm, uygulama mağazasında yayınlanmadan önce SMS ve özel foreground-service izinleri bakımından ilgili mağaza politikalarına göre ayrıca değerlendirilmelidir.

## APK oluşturma ve telefona yükleme

Android Studio kurmadan GitHub Actions ile APK oluşturabilirsin:

1. Bu projenin içindeki dosyaları GitHub deposuna yükle. `.github/workflows/android-apk.yml` dosyası da depoya eklenmiş olmalı.
2. GitHub deposunda **Actions** sekmesine gir. İlk kullanımda GitHub Actions'ı etkinleştirmen istenirse etkinleştir.
3. `Android APK` iş akışının başarılı olmasını bekle. Kaynak dosyalarını `main` veya `master` dalına yüklediğinde otomatik başlar; daha sonra **Run workflow** ile de elle başlatabilirsin.
4. Tamamlanan çalışmayı aç. **Artifacts** bölümündeki `kara-delik-asistan-debug-apk` dosyasını indir.
5. İndirilen ZIP'i açıp `app-debug.apk` dosyasını bul. APK'yi telefona gönder veya GitHub'ı telefonda açıp artifact ZIP'ini indir.
6. Telefonda APK dosyasına dokun. Android'in istediği durumda tarayıcı veya Dosyalar uygulaması için **Bu kaynaktan uygulama yüklemeye izin ver** seçeneğini aç, sonra kurulumu tamamla.

GitHub artifact'i 14 gün saklar. APK yükleme/saklama için telefonunda ya da bilgisayarında Android Studio kurulması gerekmez. İlk derleme başarısız olursa GitHub'daki Actions çalışmasının hata ayrıntısını kontrol et.

## Yerelde derleme

Android Studio'da bu klasörü açıp Android SDK 35 ile çalıştır. Komut satırı derlemesi için JDK 17 ve Android SDK 35 gerekir. Windows PowerShell'de:

```text
.\gradlew.bat assembleDebug
```

İlk açılışta kayan balon iznini ve ihtiyaç duyduğun mikrofon/rehber/SMS izinlerini ver. SMS'i otomatik göndermek için SMS izni gereklidir; rehberde aynı ada ait birden fazla farklı numara varsa hiçbir mesaj gönderilmez.
