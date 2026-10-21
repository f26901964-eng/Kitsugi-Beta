# Listem — görünüm / rozet uyumu

## Sorun

`KitsugiMediaEntryCard`, ek kaynak rozetlerini görünüm kartının dışında ayrı bir `FlowRow` içinde çiziyordu. Özellikle Tümü ızgarasında rozetler çerçeveden kopuyor, farklı başlık uzunlukları ve kaynak sayıları kart hizalarını bozuyordu. Izgaranın durum ve puan rozetleri de diğer dört görünümden farklıydı.

## Değişiklikler

- Kompakt, rahat, büyük, minimalist ve ızgara için kart içeriği ile ek kaynakları kapsayan tek dış kart/tema çerçevesi.
- Ana kaynak logosu poster köşesinde korunur. Diğer üyelikler kartın içinde, tema yüzeylerini kullanan ortak alt şeritte gösterilir. Logoların orijinal renkleri korunur; dar alanda şerit satır atlar.
- Kaynak takma adları normalleştirilir, ana kaynak ve yinelenen kaynaklar tekrar çizilmez. Büyük harf dönüşümleri Türkçe cihaz dilinden bağımsızdır.
- Durum rozetleri ortak kapsülü kullanır: opak, temaya bağlı yüzey; durum rengiyle ince çerçeve; tema metin rengi. Poster rengi metnin okunurluğunu etkilemez.
- Puan kapsülü bütün görünümlerde ortaktır. Puan gizleme/puan biçimi seçenekleri korunur.
- Kompakt, rahat ve minimalist durum/ilerleme/puan satırı dar alanda satır atlayabilir.
- Izgara başlık alanı iki satırdır. Gruplu ve düz ızgarada aynı satırdaki kartların yüksekliği eşitlenir, ek kaynak şeridi alta hizalanır.
- Tıklama/TV odağı dış kartta tutulur; kaynak şeridi ayrı bir odak durağı oluşturmaz. Poster uzun basma ve ilerletme işlemleri korunur.

## Doğrulama

- `git diff --check`: başarılı.
- `LibraryBadgeSourcesTest`: yinelenen kaynaklar, ana kaynak takma adları, boş veri ve Türkçe yerel ayar senaryoları eklendi.
- `KitsugiMediaEntryCardTest`: beş görünümde rozetlerin kart sınırları içinde kalması, dar ızgarada sarılması, yinelenmemesi ve kart tıklaması için cihaz testleri eklendi.
- `bash ./gradlew :app:testDebugUnitTest --tests '*LibraryBadgeSourcesTest' --console=plain` denendi, ancak ortamda Java/JAVA_HOME bulunmadığından Gradle başlamadı. Derleme, birim testleri ve Android cihaz testleri bu ortamda çalıştırılamadı; görsel doğrulama cihazda yapılmalı.
