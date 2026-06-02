# 距離減衰VC Mod 仕様書

**プロジェクト名:** ProximityVC  
**対象プラットフォーム:** NeoForge 21.1.228 / Minecraft 1.21.1  
**動作環境:** クライアントサイドのみ（Windows / Linux / macOS）  
**最終更新:** 2026-06-02

---

## 1. 概要

Minecraftワールド内のプレイヤー間距離に応じて、Discord通話の音量をリアルタイムに変化させるクライアントMod。  
Discord RPC の制御は **ネイティブ Node.js プロセス（proximityvc-bridge）** が担当し、NeoForge Mod とは **HTTP (localhost)** で通信する。  
これにより、Mod 側は IPC の低レベル処理から切り離され、OS 差異（Windows Named Pipe / Unix Socket）を Node.js 側に吸収できる。

---

## 2. システム構成

```
【各プレイヤーのPC上】

[Minecraft Client (NeoForge Mod)]
  │  HTTP POST localhost:7878
  ▼
[Node.js プロセス (proximityvc-bridge)]
  │  Discord IPC
  │  Windows : \\?\pipe\discord-ipc-{0-9}  (0〜9 を順にフォールバック試行)
  │  Linux   : ${XDG_RUNTIME_DIR} → ${TMPDIR} → ${TMP} → ${TEMP} → /tmp
  │            の順で discord-ipc-{0-9} を解決
  │            ※ Snap版:    /run/user/{UID}/snap.discord/discord-ipc-{0-9}
  │            ※ Flatpak版: /run/user/{UID}/app/com.discordapp.Discord/discord-ipc-{0-9}
  │  macOS   : ${TMPDIR} → ${TMP} → ${TEMP} → /tmp の順で解決
  ▼
[Discord クライアント (ローカル)]
  └─ 指定ユーザーの音量を更新
```

> **IPC接続の実装方針:**  
> - インデックス 0〜9 を順に試行し、接続できた最初のパスを使用する  
> - Linux では環境変数 `XDG_RUNTIME_DIR` → `TMPDIR` → `TMP` → `TEMP` → `/tmp` の優先順でプレフィックスを解決する（公式仕様）  
> - Linux Snap / Flatpak 環境では上記に加えて専用パスへのフォールバックを試みる  
> - `@xhayper/discord-rpc` はこのフォールバック処理を内部で実装済み

### 前提条件

- 全参加プレイヤーが本 Mod を導入していること
- 全参加プレイヤーが `proximityvc-bridge` (Node.js) を起動していること
- 全参加プレイヤーが Discord の同一 VC チャンネルに参加していること
- **各プレイヤーが自分自身の** Discord Developer Portal アプリケーションを作成し、`bridge/config.json` に設定していること
- `minecraft_discord_map.json` に Minecraft UUID ↔ Discord ID のマッピングが登録されていること

---

## 3. Discordアプリケーションの準備

### 3-1. 運用方針

**各プレイヤーが自分のPCで自分のDiscordクライアントを制御するため、プレイヤー全員が個別にアプリを作成する。**  
1つのアプリを共有する運用ではなく、各自が自分のアプリを持つことで以下のメリットがある：

- Discord RPCはローカルのDiscordクライアントに対して動作するため、各自のアプリが各自のDiscordを制御する構造が自然
- テスター登録・人数上限・承認申請が一切不要
- 他プレイヤーのClient SecretをPC上に保持しなくて済む（セキュリティ面で安全）

### 3-2. アプリ作成手順（全員が各自実施）

1. [Discord Developer Portal](https://discord.com/developers/applications) にアクセス
2. 「New Application」でアプリを作成（名前例: `ProximityVC`）
3. 「OAuth2」→「Client ID」と「Client Secret」をコピーして控えておく
4. 「OAuth2」→「Redirects」に `http://localhost` を追加
5. 取得した `clientId` と `clientSecret` を自分のPC上の `bridge/config.json` に記入する（後述）

### 3-3. 必要なOAuth2スコープ

| スコープ | 用途 |
|---|---|
| `rpc` | RPC 接続・認証の基本権限 |
| `rpc.voice.read` | VC 音量設定の読み取り・変更（読み書き両方がこのスコープに内包される） |

> **注意:** `rpc.voice.write` というスコープは**存在しない**。  
> `SET_USER_VOICE_SETTINGS` の実行権限は `rpc.voice.read` に内包されている。

---

## 4. ファイル構成

```
ProximityVC/
│
├── bridge/                               # Node.js ブリッジプロセス（各自のPCに配置）
│   ├── package.json
│   ├── index.js                          # HTTP サーバー起動・ブリッジ統括
│   ├── discordRpc.js                     # Discord IPC 接続・認証・コマンド送信
│   ├── config.json                       # 【各自が編集】Client ID / Client Secret / ポート番号
│   ├── token.json                        # 取得したアクセストークンのキャッシュ（自動生成）
│   ├── start.bat                         # Windows 用起動スクリプト
│   └── start.sh                          # Linux / macOS 用起動スクリプト
│
└── mod/                                  # NeoForge Mod（各自の .minecraft/mods/ に配置）
    └── src/main/java/com/example/proximitychat/
        ├── ProximityChatMod.java          # Mod エントリポイント・初期化
        ├── bridge/
        │   └── BridgeClient.java          # Node.js ブリッジへの HTTP クライアント
        ├── proximity/
        │   ├── ProximityHandler.java      # プレイヤー距離計算・音量制御ループ
        │   └── VolumeCalculator.java      # 距離→音量変換ロジック
        └── config/
            ├── PlayerIdMapper.java        # JSON 読み込み・UUID ↔ Discord ID マッピング
            └── ProximityConfig.java       # Mod コンフィグ（最大距離・減衰式等）

【設定ファイルの配置場所（Windows）】

バニラランチャー:
  %APPDATA%\.minecraft\config\proximitychat\

Modrinth（プロファイルごとに独立）:
  %APPDATA%\ModrinthApp\profiles\<プロファイル名>\config\proximitychat\

    ├── proximity_config.json             # Mod 動作設定（全員で共通でよい）
    └── minecraft_discord_map.json        # 【全員で共有】MinecraftUUID → DiscordID マッピング

> **補足:** Mod は `Minecraft.getInstance().gameDirectory` を使用してパスを解決するため、
> ランチャーに依存せず起動ディレクトリ相対でファイルを生成する。
> Modrinth でプロファイルごとにゲームディレクトリが分離されていれば、configファイルも自動的に各プロファイル下に生成される。
```

---

## 5. 設定ファイル仕様

### 5-1. `minecraft_discord_map.json`

Minecraft プレイヤー UUID と Discord ユーザー ID のマッピングを記述する。  
全参加者で**同一内容**のファイルを共有する（手動またはファイル共有ツールで配布）。

> **注意:** オフラインモードサーバーでは Minecraft UUID がオンライン UUID と異なる場合がある。本 Mod はオンラインモード（正規アカウント）サーバーでの使用を前提とする。

```json
{
  "version": 1,
  "mappings": {
    "550e8400-e29b-41d4-a716-446655440000": "123456789012345678",
    "6ba7b810-9dad-11d1-80b4-00c04fd430c8": "987654321098765432"
  }
}
```

| フィールド | 型 | 説明 |
|---|---|---|
| `version` | int | フォーマットバージョン（現在は `1`） |
| `mappings` | object | キー: Minecraft UUID（ハイフン付き文字列）、値: Discord ユーザー ID（Snowflake 文字列） |

### 5-2. `proximity_config.json`

全員で共通の設定で動作する。個人での変更も可。

```json
{
  "bridgePort": 7878,
  "maxDistance": 32.0,
  "minDistance": 2.0,
  "falloffType": "LINEAR",
  "updateIntervalTicks": 10,
  "maxVolume": 200,
  "minVolume": 0
}
```

| フィールド | 型 | デフォルト | 説明 |
|---|---|---|---|
| `bridgePort` | int | `7878` | Node.js ブリッジのポート番号 |
| `maxDistance` | float | `32.0` | この距離以上は音量0（ブロック単位） |
| `minDistance` | float | `2.0` | この距離以下は最大音量 |
| `falloffType` | string | `"LINEAR"` | 減衰式種別（後述） |
| `updateIntervalTicks` | int | `10` | 音量更新間隔（tick 単位、20 tick = 1 秒） |
| `maxVolume` | int | `200` | Discord 上の最大音量（0〜200） |
| `minVolume` | int | `0` | Discord 上の最小音量（0〜200） |

### 5-3. `bridge/config.json`

**各プレイヤーが自分のDiscord Developerアプリの情報を設定する。他人と共有しない。**

```json
{
  "clientId": "自分のDiscordアプリのClient ID",
  "clientSecret": "自分のDiscordアプリのClient Secret",
  "port": 7878
}
```

| フィールド | 型 | 説明 |
|---|---|---|
| `clientId` | string | 自分の Discord アプリの Client ID |
| `clientSecret` | string | 自分の Discord アプリの Client Secret（OAuth2 トークン交換に使用） |
| `port` | int | HTTP サーバーのリッスンポート（`proximity_config.json` の `bridgePort` と一致させること） |

> **セキュリティ注意:** `config.json` には Client Secret が含まれる。このファイルを他人と共有したり Git などにコミットしないこと。

---

## 6. Node.js ブリッジ仕様

### 6-1. 概要

| 項目 | 内容 |
|---|---|
| 役割 | Discord IPC との接続維持・Mod からの HTTP リクエストを Discord コマンドに変換 |
| 起動方法 | `start.bat` (Windows) / `start.sh` (Linux/macOS) をダブルクリックまたは実行 |
| 主要 npm パッケージ | `@xhayper/discord-rpc`（IPC 抽象化・TypeScript 対応フォーク、Snap/Flatpak 対応）、`express`（HTTP サーバー） |

### 6-2. 起動フロー

```
1. config.json を読み込む
2. HTTP サーバーを起動（ポート: config.port）
3. token.json が存在する場合はキャッシュ済みトークンで AUTHENTICATE を試みる
4. トークンが無効または存在しない場合:
   a. Discord IPC に接続（インデックス 0〜9 を順にフォールバック試行）
   b. HANDSHAKE 送信 → DISPATCH(READY) 受信
   c. AUTHORIZE コマンド送信（scopes: ["rpc", "rpc.voice.read"]）
      → 自分のDiscordクライアント上で認証ダイアログが表示される（初回のみ）
      → authorization code を取得
   d. OAuth2 REST API（https://discord.com/api/oauth2/token）へ HTTP POST
      → 自分のClient ID + Client Secret + authorization code でアクセストークンを取得
   e. アクセストークンを token.json に保存（次回起動時のキャッシュ用）
   f. AUTHENTICATE コマンド送信（access_token）→ 認証完了
5. Mod からのリクエスト受付開始
```

### 6-3. HTTP API

Mod（Java）からのリクエストを受け付けるエンドポイント。

#### `POST /volume`

指定ユーザーの Discord VC 音量を変更する。

**リクエストボディ (JSON):**

```json
{
  "userId": "123456789012345678",
  "volume": 80
}
```

| フィールド | 型 | 説明 |
|---|---|---|
| `userId` | string | Discord ユーザー ID (Snowflake) |
| `volume` | int | 設定する音量（0〜200 の整数） |

**レスポンス:**

```json
{ "ok": true }
```

エラー時:

```json
{ "ok": false, "error": "not connected" }
```

#### `GET /status`

ブリッジの接続状態を返す。Mod 起動時の疎通確認に使用。

**レスポンス:**

```json
{ "connected": true }
```

### 6-4. Discord RPC コマンド送信

`@xhayper/discord-rpc` の `client.user.setVoiceSettings()` を使用する。

```typescript
// @xhayper/discord-rpc の呼び出し例
await client.user?.setVoiceSettings({
  user_id: "<Discord ユーザーID>",
  volume: 100   // 0〜200 の整数
});
```

内部的に送信される RPC コマンド:

```json
{
  "cmd": "SET_USER_VOICE_SETTINGS",
  "nonce": "<uuid>",
  "args": {
    "user_id": "<Discord ユーザーID>",
    "volume": 100
  }
}
```

> **音声設定の排他制御（公式仕様）:** Discord の公式ドキュメントに明記されている通り、RPC 経由で音声設定を変更したアプリが設定をロックし、他の RPC アプリは同時に音声設定を変更できなくなる。ブリッジが切断されると設定は切断前の状態にリセットされる。  
> 本システムでは各自が自分のアプリを持つため、他プレイヤーとの競合は発生しない。ただし同一PC上で他の RPC 音声制御アプリ（Discord オーバーレイ等）を同時に使用する場合は競合の可能性がある。

---

## 7. NeoForge Mod 仕様

### 7-1. `ProximityChatMod`

| 項目 | 内容 |
|---|---|
| 役割 | Mod の初期化・各コンポーネントのライフサイクル管理 |
| 初期化タイミング | `FMLClientSetupEvent`（MOD_BUS 登録） |
| 初期化処理 | 設定ファイルのロード、`BridgeClient` の疎通確認、`ProximityHandler` のイベント登録 |
| 終了処理 | `GameShuttingDownEvent`（GAME_BUS 登録）で全プレイヤーの音量を `maxVolume` にリセット後、切断 |

> **イベントバスの区別:**  
> `FMLClientSetupEvent` → `MOD_BUS`  
> `GameShuttingDownEvent`, `ClientTickEvent.Post` → `GAME_BUS`

### 7-2. `BridgeClient`

| 項目 | 内容 |
|---|---|
| 役割 | Node.js ブリッジへの HTTP リクエスト送信 |
| 通信方式 | `HttpURLConnection`（Java 標準、追加ライブラリ不要） |
| 接続先 | `http://localhost:{bridgePort}` |
| タイムアウト | 接続: 1000ms、読み取り: 2000ms |
| スレッド | 非同期送信（Minecraft メインスレッドをブロックしない） |

#### 主要メソッド

| メソッド | 戻り値 | 説明 |
|---|---|---|
| `setVolume(discordUserId, volume)` | `void` | `POST /volume` を非同期送信（volume は int でキャストして送信） |
| `checkStatus()` | `boolean` | `GET /status` で接続確認 |

### 7-3. `ProximityHandler`

| 項目 | 内容 |
|---|---|
| 役割 | プレイヤー距離の定期計算と音量指示 |
| 実行タイミング | `net.neoforged.neoforge.client.event.ClientTickEvent.Post`（GAME_BUS 登録） |
| 実行間隔 | `updateIntervalTicks` tick ごとに処理（カウンタで管理） |
| 座標取得 | `Minecraft.getInstance().level.players()` で取得（`List<AbstractClientPlayer>` を返す） |
| 自プレイヤー除外 | `Minecraft.getInstance().player` と比較して自分自身を除外 |
| 範囲外処理 | 前回一覧にいたが今回いないプレイヤーは音量0を送信 |
| ディメンション | 自分と異なるディメンションのプレイヤーは音量0扱い |

> **スレッド安全性:** `level.players()` はメインスレッド専用。`ClientTickEvent.Post` はメインスレッドで発火するため問題ない。

#### 処理フロー

```
1. ブリッジ未接続の場合はスキップ
2. Minecraft.getInstance().player で自プレイヤーの座標・ディメンションを取得
3. level.players() でレンダー範囲内のプレイヤー一覧を取得（AbstractClientPlayer）
4. 自プレイヤーを除外
5. 各プレイヤーについて:
   a. getUUID() で UUID を取得し、PlayerIdMapper で Discord ID に変換（未登録はスキップ）
   b. ディメンションが異なる場合は distance = ∞ 扱い → volume = 0
   c. 3D 距離を計算: Math.sqrt(dx² + dy² + dz²)
   d. VolumeCalculator で音量値を算出（int にキャストして渡す）
   e. 前回と音量が変わった場合のみ BridgeClient.setVolume() を呼ぶ
6. 前回一覧にいたが今回いないプレイヤー → 音量0を送信してキャッシュから削除
```

### 7-4. `PlayerIdMapper`

| 項目 | 内容 |
|---|---|
| 役割 | `minecraft_discord_map.json` の読み込み・キャッシュ・検索 |
| 読み込みタイミング | Mod 初期化時 + `/proximitychat reload` コマンド実行時 |
| 内部形式 | `Map<UUID, String>`（UUID → Discord Snowflake ID） |

#### 主要メソッド

| メソッド | 戻り値 | 説明 |
|---|---|---|
| `load()` | `void` | JSON ファイルを読み込みキャッシュを更新 |
| `getDiscordId(uuid)` | `Optional<String>` | Minecraft UUID から Discord ID を取得 |

### 7-5. `VolumeCalculator`

| 項目 | 内容 |
|---|---|
| 役割 | 距離値から Discord 音量値への変換 |
| 入力 | `distance`（float, ブロック単位）、`ProximityConfig` |
| 出力 | `int`（0〜200、clamp 済み） |

> **注意:** Discord の `SET_USER_VOICE_SETTINGS` の `volume` フィールドは整数（0〜200）のため、float の計算結果を `(int)` キャストしてから渡すこと。

---

## 8. 距離減衰式

`falloffType` で切り替え可能。

### LINEAR（線形）

```
volume = maxVolume × (1 - (distance - minDistance) / (maxDistance - minDistance))
```

### QUADRATIC（二乗）

```
ratio  = 1 - (distance - minDistance) / (maxDistance - minDistance)
volume = maxVolume × ratio²
```

### STEPPED（段階）

```
distance を (maxDistance / 4) ごとに区切り、段階的に音量を下げる（デフォルト4段階）
```

### 共通処理

```
distance ≤ minDistance  → volume = maxVolume
distance ≥ maxDistance  → volume = minVolume (= 0)
それ以外                → 上記各式で計算
最終結果                → (int) キャスト後、clamp(minVolume, maxVolume)
```

---

## 9. チャットコマンド仕様

| コマンド | 説明 |
|---|---|
| `/proximitychat reload` | 設定ファイルと ID マッピングを再読み込み |
| `/proximitychat status` | ブリッジ接続状態・マッピング件数を表示 |
| `/proximitychat debug` | 近隣プレイヤーの距離・音量値をチャットに表示 |

---

## 10. エラーハンドリング

| ケース | 対処 |
|---|---|
| ブリッジが起動していない（Mod 起動時） | 警告をチャットに表示、30 秒ごとに `GET /status` で再確認 |
| ブリッジへの HTTP 送信失敗 | ログに出力してスキップ（ゲームプレイには影響させない） |
| Discord が起動していない（ブリッジ起動時） | エラーを出力し 30 秒ごとに再接続を試行 |
| Discord RPC 認証失敗 | エラーログを出力し停止（ユーザーが `token.json` を削除して再起動） |
| ID マッピングに UUID が存在しない | そのプレイヤーをスキップ（音量変更なし） |
| JSON ファイルが存在しない | 空のマッピングで起動し警告ログを出力 |
| JSON パースエラー | エラーログを出力し空のマッピングにフォールバック |
| `bridge/config.json` が未設定（デフォルト値のまま） | 起動時にエラーを出力し処理を中断 |

---

## 11. 制約事項・注意点

1. **各自でアプリ作成が必要:** 参加者全員が Discord Developer Portal で自分のアプリを作成し、`bridge/config.json` に設定する必要がある。一度設定すれば `token.json` にトークンがキャッシュされるため、2回目以降は認証ダイアログが表示されない。

2. **音声設定の排他制御（公式仕様）:** Discord の公式仕様として、RPC で音声設定を変更したアプリが設定をロックする。各自が自分のアプリを持つ設計のためプレイヤー間の競合は発生しない。同一PC上で他の RPC 音声制御アプリと同時使用する場合のみ競合の可能性がある。

3. **音量リセット:** ブリッジが切断されると、Discord が切断前の音量設定に自動リセットする。Minecraft 終了前にブリッジを切断すると相手の音量が元に戻るため、**Minecraft を先に終了してからブリッジを終了する**ことを推奨する。

4. **Node.js の事前インストール:** 利用者全員が Node.js（v18.20.7 以上）を事前にインストールする必要がある。

5. **オンラインモード限定:** オフラインモードサーバーでは Minecraft UUID がオンライン UUID と異なるため、ID マッピングが一致しない場合がある。

6. **`bridge/config.json` は各自で管理:** Client Secret が含まれるため、他プレイヤーと共有しないこと。`minecraft_discord_map.json` と `proximity_config.json` は全員で共有するファイルだが、`bridge/config.json` は各自が個別に保持する。

---

## 12. 実装優先順位

| フェーズ | コンポーネント | 内容 |
|---|---|---|
| Phase 1 | bridge | `@xhayper/discord-rpc` で Discord IPC 接続・認証・`setVoiceSettings()` 動作確認 |
| Phase 2 | bridge | `POST /volume` / `GET /status` HTTP サーバー実装、`token.json` キャッシュ対応 |
| Phase 3 | mod | `BridgeClient` 実装・ブリッジとの疎通確認 |
| Phase 4 | mod | `PlayerIdMapper` JSON 読み書き |
| Phase 5 | mod | `ProximityHandler` 距離計算ループ（LINEAR 固定で動作確認） |
| Phase 6 | mod | `ProximityConfig` 設定外部化・減衰式切り替え |
| Phase 7 | 両方 | チャットコマンド・エラーハンドリング・再接続処理 |

---

## 13. 依存ライブラリ

### Node.js ブリッジ

| パッケージ | バージョン | 用途 |
|---|---|---|
| `@xhayper/discord-rpc` | `^1.x.x` | Discord IPC 接続・RPC コマンド抽象化（`discord-rpc` の TypeScript 対応フォーク、Snap/Flatpak 対応済み） |
| `express` | `^4.x.x` | HTTP サーバー |

### NeoForge Mod (Java)

| ライブラリ | 用途 | 入手方法 |
|---|---|---|
| NeoForge 21.1.228 | Mod 基盤 | [NeoForge](https://neoforged.net/) |
| Gson（Minecraft 同梱） | JSON 読み書き | 追加不要 |
| Java 標準 HttpURLConnection | HTTP クライアント | JDK 標準、追加不要 |

---

*以上*
