/**
 * Bu paketin ne zaman derlendigi.
 *
 * OLCULEN BOSLUK: arka uc `/actuator/info` ile "hangi yapiyi kosuyorum"
 * sorusuna cevap veriyor; arayuzde karsiligi HIC YOKTU. Bunun bedeli ayni gun
 * iki kez odendi -- konteyner bir gun eski bir paketi servis ediyordu, kod
 * dogruydu ve testler yesildi. Soru ancak konteynerin ici kazilarak
 * cevaplanabiliyordu.
 *
 * Damga ekranda duruyor cunku onu ilk soracak kisi HATA BILDIREN kullanicidir:
 * "sende hala eski hali mi gorunuyor" sorusunun cevabi bir tahmin degil bir
 * okuma olmali.
 *
 * `typeof` KONTROLU kasitli: `define` uygulanmamis bir ortamda (ornegin ham
 * bir Node calistirmasi) tanimsiz bir tanimlayiciya dokunmak `ReferenceError`
 * firlatir, `typeof` firlatmaz. Damganin yoklugu uygulamayi dusurmemeli.
 */
export const BUILD_TIME: string =
  typeof __BUILD_TIME__ === 'string' ? __BUILD_TIME__ : '';
