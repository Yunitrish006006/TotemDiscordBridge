# Tasks: Discord zh-TW Event Localization

## 1. Localization infrastructure

- [ ] 1.1 新增 Server-only `DiscordLocalizationService`，Discord-facing locale 第一階段固定為 `zh_tw`。
- [ ] 1.2 在純 Dedicated Server runtime 載入 immutable `zh_tw` translation snapshot，不依賴 Client-only class。
- [ ] 1.3 Resource reload 時原子替換完整 snapshot，不修改 Minecraft 全域 `Language` instance。
- [ ] 1.4 實作 literal、translatable、巢狀參數與 sibling component 的純文字遞迴解析。
- [ ] 1.5 實作 `zh_tw` 缺漏／格式失敗的安全 fallback、raw translation key 防洩漏與重複 warning 節流。

## 2. Semantic event formatting

- [ ] 2.1 將 Discord formatter 與 HTTP transport 分離，使測試可捕捉 `event`、`username` 與 `message` 而不連線真實 Discord。
- [ ] 2.2 Advancement 事件傳遞未提前解析的 title component 與 semantic frame type。
- [ ] 2.3 將 `task`、`goal`、`challenge` 顯示為中文「進度」、「目標」、「挑戰」。
- [ ] 2.4 村民升級事件傳遞 custom name、profession、previous level 與 current level。
- [ ] 2.5 未命名村民、profession 與 level 1–5 使用 `zh_tw` 中文名稱；自訂名稱維持 literal。
- [ ] 2.6 死亡訊息、Boss／實體預設名稱、raid result 與 difficulty display name 共用 localization service。
- [ ] 2.7 玩家聊天、玩家名稱、物品／村民自訂名稱與 datapack literal text 不得被翻譯或改寫。

## 3. Safety and compatibility

- [ ] 3.1 保留既有 Worker endpoint、payload 欄位、event 名稱、多頻道路由與 Webhook／Bot Token fallback。
- [ ] 3.2 Localization exception 不得中止 advancement award、村民交易／升級、死亡或其他 Server event。
- [ ] 3.3 非同步 HTTP worker 只接收 immutable localized strings，不得讀取 Entity、Level 或 registry mutable state。
- [ ] 3.4 不新增設定檔 migration、SavedData、世界資料或 identifier 變更。

## 4. Tests

- [ ] 4.1 Vanilla advancement translation key 解析為預期 `zh_tw` title。
- [ ] 4.2 Advancement `task`／`goal`／`challenge` 中文格式矩陣。
- [ ] 4.3 巢狀 Component 保留玩家名稱、物品自訂名稱與格式參數。
- [ ] 4.4 未知 translation key／格式錯誤使用安全 fallback，payload 不包含 raw key。
- [ ] 4.5 未命名村民＋圖書管理員＋level 1→2 產生完整中文訊息。
- [ ] 4.6 自訂村民名稱原樣保留，profession 與 level 仍中文化。
- [ ] 4.7 Advancement 與村民升級各自 exactly-once 建立 Discord payload，不因 fallback 重複發送。
- [ ] 4.8 Dedicated Server 啟動與 GameTest 不載入 Client-only language class。
- [ ] 4.9 Worker／Discord 失敗時 localization event 不影響遊戲流程。
- [ ] 4.10 Java 25 Validate、完整 Server GameTests 與兩套 restart probes 通過。

## 5. Documentation

- [ ] 5.1 更新 Discord Bridge 主規格與事件格式文件。
- [ ] 5.2 更新 `docs/discord/` 說明 Discord 訊息固定使用繁體中文，以及 custom text／fallback 規則。
- [ ] 5.3 發佈時加入版本變更紀錄與人工 Discord 顯示驗收矩陣。