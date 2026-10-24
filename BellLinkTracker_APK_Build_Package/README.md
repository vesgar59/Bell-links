# Bell Link Tracker — Android APK derleme

Bu paket Android Studio gerektirmeden **GitHub Actions** ile APK üretir.

## Derleme
1. GitHub hesabınızda yeni bir depo oluşturun.
2. ZIP içeriğindeki **dosya ve klasörleri depo köküne** yükleyin (`.github` dahil).
3. GitHub > Actions > Android APK Build > Run workflow düğmesini kullanın.
4. İşlem başarılıysa ilgili çalışmanın **Artifacts** bölümünden `BellLinkTracker-debug-APK` dosyasını indirin.
5. İndirilen artifact ZIP'ini açıp `app-debug.apk` dosyasını Android telefona aktarın ve yükleyin. Android yükleme izni sorabilir.

## Kullanım ve sınırlamalar
- Ekran yakalama izni gerekir; Android bildiriminde yakalama sürer.
- Gönderilen 691x1536 ekran görüntüsüne göre hazırlanmış **deneysel** çan tanıyıcıdır.
- Ekran oranı, tarayıcı çubuğu, animasyonlar ve oyun arayüzü değişiklikleri hatalı sayım üretebilir.
- 5 çan bildirimi bir **bonus adayı**dır; oyun ekranından doğrulanmalıdır.
- Bahis veya dönüş başlatmaz; sonraki bonusu tahmin edemez.
- APK henüz gerçek cihazda derlenip test edilmedi.
