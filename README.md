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
- ffmpegまたはImageMagic

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
