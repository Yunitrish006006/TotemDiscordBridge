# Design: Discord zh-TW Event Localization

## Goals

- 讓 Discord Bridge 的 Minecraft 系統文字穩定輸出繁體中文，不受 Dedicated Server 預設語言影響。
- 使用單一 Server-only localization service，避免 advancement、村民、死亡、Boss 等事件各自硬編碼翻譯。
- 保留玩家與 datapack 提供的 literal／custom text。
- 語系缺漏或格式錯誤時安全降級，不影響 server tick 或事件流程。

## Non-goals

- 不翻譯玩家聊天、玩家名稱或自訂名稱。
- 不提供 Discord → Minecraft 雙向翻譯。
- 第一階段不增加管理 GUI locale 選項；Discord-facing locale 固定為 `zh_tw`。
- 不要求 Worker 或 Discord Bot 執行翻譯。

## Localization boundary

事件來源 SHOULD 傳遞尚未 `getString()` 的 `Component`、registry identity 或 semantic enum；Discord formatter 在 Server thread 上建立不可變的 localized message，之後才送入現有非同步 HTTP executor。

```text
Server event
  → semantic event data / Component
  → DiscordLocalizationService (zh_tw)
  → DiscordEventFormatter
  → immutable username/message/event payload
  → existing async HTTP queue
```

不得在 Discord worker thread 中讀取 Entity、Level、registry 或其他 mutable Minecraft state。

## Translation source

`DiscordLocalizationService` 必須在純 Dedicated Server 環境載入 `zh_tw` translation table。實作可使用 runtime 可取得的 Minecraft resource／language data，必要時可提供受版本測試約束的 bundled fallback；但不得依賴 Client-only class 或連線到外部翻譯服務。

Translation table 必須以 immutable snapshot 發布。Resource reload 時建立新 snapshot 後原子替換，既有 Discord 工作不得看到部分更新狀態。

不得修改 Minecraft 全域 `Language` instance，避免改變 command feedback、log、玩家封包或其他模組訊息。

## Component rendering

Resolver 必須遞迴處理：

- literal component：原樣保留。
- translatable component：使用 `zh_tw` translation key 與參數格式化。
- 巢狀 component 參數：遞迴解析。
- 玩家名稱、物品自訂名稱、村民自訂名稱：視為 literal，原樣保留。
- style、hover、click event：不送到 Discord 純文字 payload；只保留可見文字。

Fallback 順序：

1. `zh_tw` translation。
2. Minecraft runtime 可安全解析的 fallback text，通常為 `en_us`／目前 server rendering。
3. 事件 formatter 提供的中文通用名稱，例如「村民」或「未知進度」。

不得把 `advancements.story.mine_stone.title` 等 unresolved key 直接發送到 Discord。Fallback 發生時 MAY 記錄不含玩家隱私與 secret 的 debug／warning，但同一 key 應節流，避免 log flood。

## Advancement formatting

來源保留 `DisplayInfo.title()` component 與 `AdvancementType` semantic value。Discord formatter 將 frame type 映射為：

| Semantic type | zh-TW 顯示 |
|---|---|
| `task` | 進度 |
| `goal` | 目標 |
| `challenge` | 挑戰 |

通知必須包含玩家名稱與 localized advancement title。不得把 serialized English type 直接嵌入訊息，也不得因 translation 缺漏重新發送第二次通知。

## Villager level-up formatting

事件來源需要提供：

- 自訂名稱是否存在及其 literal text。
- villager profession identity。
- previous level。
- current level。

規則：

- 有自訂名稱時完整保留。
- 無自訂名稱時使用 localized「村民」。
- profession 使用 `zh_tw` 名稱；`none`／無法解析時可省略職業欄位。
- level 1–5 顯示中文階級名稱，並保留數值作為診斷友善資訊亦可。
- 建議格式：`村民（圖書管理員）升級：新手 → 學徒`。

不得只送 `等級 1 → 2`，也不得顯示 `Villager`、`Librarian`、`Novice` 等英文 fallback，除非 `zh_tw` translation table 完全不可用且安全 fallback 已啟用。

## Other Minecraft-generated text

同一 resolver 應逐步套用於：

- 原版 death message template 與其中的 entity／item component。
- Boss／實體預設名稱。
- raid result。
- difficulty display name。
- 未來新增至 Discord Bridge 的 Minecraft translatable component。

技術識別字如 gamerule name、registry ID 或管理命令 action MAY 保留原識別字，但周圍說明文字必須維持中文。

## Failure isolation

- Translation exception 必須在 formatter 邊界捕捉並回退，不得阻止 advancement award、villager trade、死亡或其他遊戲事件。
- Localization service 未初始化時，Bridge 仍可使用安全 fallback。
- Localization 失敗不得增加額外 HTTP retry 或建立重複 Discord event。
- Worker endpoint、event routing、channels 與 secret handling 全部沿用既有流程。

## Test strategy

### Unit tests

- Vanilla advancement title key 解析成預期 `zh_tw` 文字。
- `task`／`goal`／`challenge` 映射為中文。
- 巢狀 translatable component 保留玩家名、物品自訂名與格式參數。
- 未知 translation key 使用 fallback，Discord message 不包含 raw key。
- 村民自訂名稱原樣保留；未命名村民顯示「村民」。
- profession 與 level 1–5 產生中文名稱。
- Resource snapshot 原子替換與多執行緒讀取不產生部分狀態。

### Server tests

- Dedicated Server 啟動時不載入 Client-only class。
- Advancement 完成只建立一筆 localized `advancement` payload。
- 村民從 level 1 升至 2 只建立一筆 localized `villager_level_up` payload。
- Discord／Worker 不可用時，遊戲事件仍成功完成。

HTTP 測試應使用 payload capture seam 或 local fake transport，不得依賴真實 Discord token。