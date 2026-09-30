# Kara Delik Asistan

Android için kayan kara delik balonu, Türkçe sesli sohbet ve sesli uygulama komutları.

## Sesli komutlar

- “Hey Asistan” ile konuşmayı başlat; bu özellik uygulama içinden asistanı başlattıktan sonra mikrofon ve görünür bildirimle çalışır.
- “YouTube'u aç”, “Chrome'u aç” veya “google.com'a gir” diyerek yüklü uygulama ya da site aç.
- Ekran kontrolü iznini açtıktan sonra “arama kutusuna kediler yaz”, “Giriş düğmesine tıkla”, “aşağı kaydır” ve “geri git” komutlarıyla görünen kontrolleri kullan.
- “Ahmet'e şu mesajı gönder: Yarın görüşelim” komutu, izin ve tekil rehber eşleşmesi varsa SMS gönderir. WhatsApp/Telegram mesajları gönderim onayı gerektirir.
- Sohbet soruları internet üzerinden Pollinations'ın ücretsiz uç noktasına iletilir; API anahtarı gerekmez.

## Ekran kontrolünü açma

Uygulamayı başlat, **Ekran kontrolünü aç (Erişilebilirlik)** düğmesine dokun, Android Erişilebilirlik ayarlarında **Kara Delik Asistan ekran kontrolü** hizmetini kendin etkinleştir. Bu izin ekrandaki erişilebilirlik metinlerini okumaya, düğmelere dokunmaya ve alanlara metin yazmaya olanak verir. Ekran içeriği uygulama tarafından kaydedilmez veya sohbet hizmetine gönderilmez. Parola alanlarına yazmaz; mesaj gönderme, silme ve ödeme gibi son adımlara otomatik basmaz. Bazı uygulamalar ekran kontrollerini erişilebilir hâle getirmediği için her öğe üzerinde çalışmayabilir.

## Gizlilik ve izinler

Kayan balon için diğer uygulamaların üzerinde gösterme, uyandırma için mikrofon ve durum bildirimi izinleri gerekir. Ses tanıma Android'in seçili hizmetini kullanır ve çevrimdışı çalışma garanti edilmez. Sohbet yanıtı için tanınan metin Pollinations hizmetine gönderilir. Mikrofonu kalıcı bildirimdeki **Durdur** düğmesiyle kapatabilirsin.

## APK oluşturma ve yükleme

1. Depoda **Actions** sekmesini aç ve **Android APK** iş akışının başarıyla tamamlanmasını bekle.
2. Başarılı çalışmayı açıp **Artifacts** bölümünden `kara-delik-asistan-debug-apk` ZIP'ini indir.
3. ZIP'i açıp `app-debug.apk` dosyasını telefona yükle. Android sorarsa Dosyalar/tarayıcı için bu kaynaktan uygulama yüklemeye izin ver.
4. Uygulamayı aç; balon ve mikrofon izinlerini ver. Ekran komutları için yukarıdaki Erişilebilirlik iznini ayrıca etkinleştir.

Yerel derleme için JDK 17 ve Android SDK 35 gerekir: `gradlew.bat assembleDebug`.
