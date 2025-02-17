# Image2Resize
## これは何
`https://i2i.nicovrc.net`のプログラム

## [変更履歴](CHANGELOG.md)

## branch内容
### master
- i2i.nicovrc.netの本番環境に適用されているもの
### v1
- 開発中のVer 1.x.x

## 必要なもの
- Java 21以降
- ffmpegまたはImageMagic (両方ある場合はffmpegが優先されます。 ※ffmpegは6.1.1以降、ImageMagicは6.9.12-98以降なら動くと思います。)

## 動作確認済み環境
- Ubuntu 22.04 64bit (OpenJDK 21.0.6 / ffmpeg 6.1.1-3ubuntu5 / ImageMagick 6.9.12-98)
- Windows 11 24H2 (OpenJDK 22.0.1 / ffmpeg 2024-12-11-git-a518b5540d-full_build-www.gyan.dev / ImageMagic 7.1.1-43)

## 使い方
- 画像を変換する
```
http://(Host)/?url=(URL)
```

## APIもどき
- レスポンスはすべてJSON形式
### /api/v1/test
- リクエスト形式 : GET
- パラメータ : なし
- 結果 : `{"message": "ok"}`
### /api/v1/get_data
- リクエスト形式 : GET
- パラメータ : なし
- 結果 : 現在の画像キャッシュ数とログ書き込み待ち件数(1分ごとに書き込み)が表示される
### /api/v1/get_cachelist
- リクエスト形式 : GET
- パラメータ : なし
- 結果 : URLとキャッシュした時間のリスト
### /api/v1/image_resize
- リクエスト形式 : POST
- `{"filename": "ファイル名", "content": "Base64エンコードした画像バイナリデータ"}`を一緒にPOSTする
- 結果 : 変換後の画像(png)
