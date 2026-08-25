/**
 * Derleme aninda yerine yazilan sabitler; bkz. vite.config.ts.
 *
 * Dosya adi buildInfo.ts ile AYNI OLAMAZ: TypeScript ayni taban adli bir
 * .d.ts dosyasini o modulun BILDIRIM dosyasi sayar ve global tanim gorunmez
 * olur. Ayni aile: iki modul adi yalnizca harf buyuklugu ile de ayrilmaz.
 */
declare const __BUILD_TIME__: string;
