樣板圖片放這裡（PNG，灰階或彩色皆可，載入時會轉灰階）。

命名對應 scripts 模組的 template id：
  images/<id>.png

例如：
  images/battle/attack_button.png      -> id "battle/attack_button"
  images/state/battle_action.png       -> id "state/battle_action"
  images/state/result_win.png          -> id "state/result_win"

擷取方式（階段 2）：
  1. 在 MuMu 進入賽爾號各畫面
  2. adb exec-out screencap -p > screen.png 取得全螢幕
  3. 依「縮到高 720、等比」的比例裁下要辨識的小區塊
  4. 存成上述路徑

在有樣板前，App 會以 TestPipelineScript 驗證截圖/點擊管線；
GameStateDetector 對缺少的樣板會回報 UNKNOWN。
