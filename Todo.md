# ProximityVC 実装 Todo

> **サブエージェントチェックの凡例**
> - `/code-review` — 実装の正確性・バグ検出レビュー
> - `/verify` — 実際にアプリを起動して動作確認
> - `/simplify` — リファクタリング・簡潔化の提案
> - `/security-review` — セキュリティ観点のレビュー

---

## Phase 1 — bridge: Discord IPC 接続・認証確認

- [ ] `bridge/package.json` 作成（`@xhayper/discord-rpc`, `express` を依存に追加）
- [ ] `bridge/config.json` テンプレート作成（`clientId`, `clientSecret`, `port` フィールド）
- [ ] `bridge/discordRpc.js` 作成
  - [ ] `@xhayper/discord-rpc` で Discord IPC 接続（インデックス 0〜9 フォールバック）
  - [ ] AUTHORIZE コマンド送信（scopes: `rpc`, `rpc.voice.read`）
  - [ ] OAuth2 REST API でアクセストークン取得
  - [ ] `token.json` へのトークンキャッシュ保存・読み込み
  - [ ] AUTHENTICATE コマンド送信・認証完了確認
  - [ ] `client.user.setVoiceSettings()` の動作確認（手動テスト）
- [ ] `bridge/start.bat` 作成（Windows 用起動スクリプト）
- [ ] `bridge/start.sh` 作成（Linux/macOS 用起動スクリプト）

### Phase 1 チェック
- [ ] `/code-review` — `discordRpc.js` の IPC 接続・認証フロー・トークンキャッシュの実装正確性を確認
- [ ] `/verify` — 実際に Discord を起動して認証ダイアログ表示・`setVoiceSettings()` 動作を確認

---

## Phase 2 — bridge: HTTP サーバー実装

- [ ] `bridge/index.js` 作成
  - [ ] `config.json` 読み込み（未設定検出・起動中断）
  - [ ] Express HTTP サーバー起動（`config.port`）
  - [ ] `POST /volume` エンドポイント実装（`userId`, `volume` → `setVoiceSettings` 呼び出し）
  - [ ] `GET /status` エンドポイント実装（接続状態を `{ connected: bool }` で返す）
  - [ ] 未接続時のエラーレスポンス `{ ok: false, error: "not connected" }` 対応
  - [ ] Discord 未起動時の 30 秒ごと再接続ループ実装

### Phase 2 チェック
- [ ] `/code-review` — `index.js` のエンドポイント実装・エラーレスポンス・再接続ロジックを確認
- [ ] `/security-review` — `config.json` の Client Secret 取り扱い・HTTP エンドポイントの入力バリデーションを確認
- [ ] `/verify` — `curl` で `POST /volume` / `GET /status` を叩いて期待レスポンスを確認

---

## Phase 3 — mod: プロジェクト雛形 & BridgeClient

- [ ] NeoForge 21.1.228 の Gradle プロジェクト雛形作成（`mod/` 以下）
- [ ] `ProximityChatMod.java` 作成（エントリポイント・`FMLClientSetupEvent` 登録）
- [ ] `BridgeClient.java` 実装
  - [ ] `POST /volume` 非同期送信（`HttpURLConnection`、接続 1000ms・読取 2000ms タイムアウト）
  - [ ] `GET /status` 同期確認（`checkStatus()` → `boolean`）
  - [ ] Mod 起動時の疎通確認・未接続時の警告チャット表示
  - [ ] 30 秒ごとの再確認ループ実装

### Phase 3 チェック
- [ ] `/code-review` — `BridgeClient.java` の非同期送信・タイムアウト設定・スレッド安全性を確認
- [ ] `/verify` — bridge 起動状態・未起動状態それぞれで Mod の疎通確認動作を確認

---

## Phase 4 — mod: PlayerIdMapper

- [ ] `minecraft_discord_map.json` テンプレート作成（`version`, `mappings` フィールド）
- [ ] `PlayerIdMapper.java` 実装
  - [ ] Gson で `minecraft_discord_map.json` 読み込み
  - [ ] `Map<UUID, String>` キャッシュ構築
  - [ ] `getDiscordId(UUID)` → `Optional<String>` 実装
  - [ ] ファイル不在・JSON パースエラー時の警告ログ・空マップフォールバック

### Phase 4 チェック
- [ ] `/code-review` — `PlayerIdMapper.java` の JSON 読み込み・エラーハンドリング・`Optional` の正しい使用を確認
- [ ] `/simplify` — マッピングキャッシュ構築ロジックの簡潔化余地を確認

---

## Phase 5 — mod: ProximityHandler（距離計算ループ）

- [ ] `ProximityHandler.java` 実装
  - [ ] `ClientTickEvent.Post`（GAME_BUS）登録・`updateIntervalTicks` カウンタ管理
  - [ ] `Minecraft.getInstance().level.players()` でプレイヤー一覧取得
  - [ ] 自プレイヤー除外・ディメンション違い → 音量 0 処理
  - [ ] 3D 距離計算（`Math.sqrt(dx² + dy² + dz²)`）
  - [ ] `PlayerIdMapper` で Discord ID 変換（未登録はスキップ）
  - [ ] 前回音量との差分比較・変化時のみ `BridgeClient.setVolume()` 呼び出し
  - [ ] 前回一覧に存在し今回不在のプレイヤーへ音量 0 送信・キャッシュ削除
- [ ] `VolumeCalculator.java` 実装（LINEAR 固定で動作確認）
  - [ ] `distance ≤ minDistance` → `maxVolume`
  - [ ] `distance ≥ maxDistance` → `minVolume`
  - [ ] LINEAR 計算式・`(int)` キャスト・clamp 処理

### Phase 5 チェック
- [ ] `/code-review` — `ProximityHandler.java` のスレッド安全性・ディメンション判定・差分比較ロジックを確認
- [ ] `/code-review` — `VolumeCalculator.java` の境界値（`minDistance`/`maxDistance`）と clamp 処理を確認
- [ ] `/verify` — Minecraft 内で複数プレイヤーを動かして音量変化が Discord に反映されることを確認

---

## Phase 6 — mod: ProximityConfig & 減衰式切り替え

- [ ] `proximity_config.json` テンプレート作成
- [ ] `ProximityConfig.java` 実装（Gson で設定読み込み・全フィールド対応）
- [ ] `VolumeCalculator.java` に QUADRATIC・STEPPED 実装追加
- [ ] `falloffType` による動的切り替え
- [ ] `GameShuttingDownEvent` で全プレイヤー音量を `maxVolume` にリセット後切断

### Phase 6 チェック
- [ ] `/code-review` — QUADRATIC・STEPPED 各減衰式の計算正確性と `falloffType` 切り替えを確認
- [ ] `/simplify` — `VolumeCalculator.java` の減衰式分岐を整理・簡潔化
- [ ] `/verify` — 各 `falloffType` を切り替えてゲーム内で音量変化の挙動を確認

---

## Phase 7 — チャットコマンド・エラーハンドリング・統合テスト

- [ ] チャットコマンド実装
  - [ ] `/proximitychat reload` — 設定・IDマッピング再読み込み
  - [ ] `/proximitychat status` — ブリッジ接続状態・マッピング件数表示
  - [ ] `/proximitychat debug` — 近隣プレイヤーの距離・音量値をチャット表示
- [ ] エラーハンドリング最終確認（仕様 §10 の全ケース）
  - [ ] `bridge/config.json` 未設定検出（デフォルト値のまま起動中断）
  - [ ] Discord RPC 認証失敗時のエラーログ・停止処理
  - [ ] `token.json` 削除による再認証フロー確認
- [ ] README / セットアップ手順書作成（開発者向け）
- [ ] 複数PC / 複数プレイヤーでの結合テスト

### Phase 7 チェック（最終リリース前）
- [ ] `/code-review ultra` — 全変更差分のマルチエージェントによる総合レビュー
- [ ] `/security-review` — Client Secret 漏洩リスク・`token.json` 保護・HTTP エンドポイントの全体確認
- [ ] `/verify` — チャットコマンド 3 種・全エラーケース・Minecraft 終了→ブリッジ終了の順序確認

---

## レビュー指摘・修正項目（コードレビューで発覚）

> Phase 1-3 完了後の `/code-review high` で発見。重要度順に記載。

- [x] `ProximityHandler.java:80` — **[Critical]** gone プレイヤーに `maxVolume` を送っていた → `minVolume(0)` に修正（仕様§7-3）
- [x] `discordRpc.js:83` — **[High]** キャッシュトークン無効時に `connect()` が二重起動 → `scheduleReconnect()` に統一
- [x] `index.js:44` — **[Medium]** エラー時 HTTP 200 → 503 に修正
- [x] `ProximityChatMod.java:32` — **[Low]** `FMLClientSetupEvent` はパラレルスレッドで発火 → `event.enqueueWork()` でメインスレッドに移行
- [x] `BridgeClient.java:85` — **[Low]** スケジューラスレッドから `Minecraft.getInstance()` が null の可能性 → null ガード追加
- [x] `index.js:52` — **[Low]** `startHttpServer()` の TCP バインド完了を待たず `rpc.connect()` が走る → `Promise` 化して `await`
